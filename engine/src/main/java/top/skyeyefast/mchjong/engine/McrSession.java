package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** MCR match state and hand acknowledgements; room authority belongs to TableSession. */
public final class McrSession extends TableSession {
    private McrGame game;
    private List<Integer> stock = List.of();
    private int confirmed;
    static final int AUTO_ACTION_TICKS = 12;
    static final int SETTLEMENT_TICKS = 200;
    private TimeControl timeControl = TimeControl.DEFAULT;
    private List<TimeControl.Clock> clocks = new ArrayList<>();
    private int age;
    private ReplayMatch replay;
    private McrReplayRecorder recorder;
    private final List<ReplayMatch> archiveQueue = new ArrayList<>();

    public McrSession(UUID tableId, long seed) {
        super(tableId, MahjongVariant.MCR, 4, seed);
    }

    /** Copy the prepared room roster in seat order; never populate it from a client action. */
    public static McrSession start(UUID tableId, List<TableParticipant> participants, long seed, List<Integer> stock) {
        var roster = roster(participants);
        var session = new McrSession(tableId, seed);
        for (int seat = 0; seat < 4; seat++) session.participants[seat] = Participant.restore(roster.get(seat));
        session.hostId = roster.stream().filter(player -> !player.bot()).findFirst().orElseThrow().id();
        for (var participant : session.participants) if (participant.bot) participant.presence = PlayerPresence.SEATED;
        session.seating.positioned(4);
        session.configureEquipment(false, stock);
        session.startMatch();
        return session;
    }

    /** Presence is deliberately not restored. Old requests cannot target the new incarnation. */
    public static McrSession restore(State state) {
        Objects.requireNonNull(state);
        var session = new McrSession(state.room().tableId(), state.room().seed());
        session.restoreRoom(state.room());
        session.stock = state.stock();
        session.game = state.game() == null ? null : McrGame.restore(state.game());
        session.confirmed = state.confirmed();
        session.timeControl = state.timeControl();
        session.clocks = new ArrayList<>(state.clocks());
        session.age = state.age();
        session.replay = state.replay();
        session.recorder = state.recorder() == null ? null : new McrReplayRecorder(state.recorder());
        session.archiveQueue.addAll(state.archiveQueue());
        if (session.game != null) roster(session.participants());
        return session;
    }

    /** Authenticated sender only; no seat number or tile identity is accepted from the request. */
    public boolean act(UUID actor, UUID expectedTable, UUID expectedIncarnation, long decision, int actionIndex) {
        if (game == null || paused() || exitVote != null) return false;
        int seat = authorize(actor, expectedTable, expectedIncarnation, decision, game.decision());
        return seat >= 0 && apply(seat, decision, actionIndex);
    }

    private boolean apply(int seat, long decision, int actionIndex) {
        var options = recorder == null ? null : game.actions(seat);
        var before = recorder == null ? null : game.save();
        if (!game.act(seat, decision, actionIndex)) return false;
        if (recorder != null) {
            recorder.accepted(before, seat, options, actionIndex, game);
            if (game.phase() == McrGame.Phase.HAND_END || game.phase() == McrGame.Phase.MATCH_END) finishReplay();
        }
        if (game.decision() != decision) resetDecision(false);
        if (game.phase() == McrGame.Phase.HAND_END)
            for (int bot = 0; bot < 4; bot++) if (participants[bot].bot) confirmed |= 1 << bot;
        if (game.phase() == McrGame.Phase.MATCH_END) lifecycle = Lifecycle.FINISHED;
        changed(false);
        return true;
    }

    /** Explicit confirmations may finish the server-timed reading period early. */
    public boolean confirmNextHand(UUID actor, UUID expectedTable, UUID expectedIncarnation, long decision) {
        if (game == null || paused() || exitVote != null) return false;
        int seat = authorize(actor, expectedTable, expectedIncarnation, decision, game.decision());
        if (seat < 0 || game.phase() != McrGame.Phase.HAND_END || (confirmed & (1 << seat)) != 0) return false;
        confirmed |= 1 << seat;
        if (confirmed == 15) nextHand();
        changed(false);
        return true;
    }

    public boolean equipped() { return Tile.validMcrSet(stock); }

    public TimeControl timeControl() { return timeControl; }

    public boolean configureClock(UUID actor, TimeControl control) {
        if (!lobby() || exitVote != null || !isHost(actor)) return false;
        timeControl = Objects.requireNonNull(control);
        resetReadiness();
        changed(true);
        return true;
    }

    public boolean configureEquipment(boolean manual, List<Integer> tiles) {
        Objects.requireNonNull(tiles);
        if (manual || !lobby()) return false;
        if (!tiles.isEmpty() && !Tile.validMcrSet(tiles))
            throw new IllegalArgumentException("An MCR table requires all 144 physical tiles");
        if (stock.equals(tiles)) return false;
        stock = List.copyOf(tiles);
        resetReadiness();
        changed(true);
        return true;
    }

    protected void startMatch() {
        if (!equipped()) throw new IllegalStateException("Cannot start MCR without its complete stock");
        roster(participants());
        game = McrGame.fromStock(seed, stock);
        if (worldPolicy.replaysEnabled()) {
            long now = System.currentTimeMillis();
            replay = new ReplayMatch(UUID.randomUUID(), tableId, now, now,
                participants().stream().map(player -> new ReplayMatch.Participant(player.id(), player.name(), player.bot())).toList(),
                MahjongVariant.MCR, false, null, new McrReplay(List.of()), null);
            recorder = new McrReplayRecorder(game);
        } else { replay = null; recorder = null; }
        resetDecision(true);
        confirmed = 0;
        lifecycle = Lifecycle.PLAYING;
        renewIncarnation();
    }

    protected void clearMatch() {
        game = null;
        replay = null;
        recorder = null;
        confirmed = 0;
        clocks.clear();
        age = 0;
    }

    public void tick() {
        if (!tickRoom() || game == null || lifecycle == Lifecycle.FINISHED) return;
        age++;
        if (game.phase() == McrGame.Phase.HAND_END) {
            if (age >= SETTLEMENT_TICKS) nextHand();
            if (age % 10 == 0) changed(false);
            return;
        }
        long decision = game.decision();
        // Charge all pending responders before any action can close the shared window.
        for (int seat = 0; seat < 4; seat++) if (clockActive(seat)) {
            var clock = clocks.get(seat);
            var remaining = new TimeControl.Clock(clock.moveTicks(), clock.reserveTicks(), true).after(50);
            clocks.set(seat, new TimeControl.Clock(remaining.moveTicks(), remaining.reserveTicks(), false));
        }
        for (int seat = 0; seat < 4 && game.decision() == decision; seat++) {
            var actions = game.actions(seat);
            if (actions.isEmpty()) continue;
            var first = actions.get(0).type();
            if (actions.size() == 1 && (first == McrAction.Type.DRAW || first == McrAction.Type.REPLACE_FLOWER)) {
                if (age >= AUTO_ACTION_TICKS) apply(seat, decision, 0);
            } else if (participants[seat].bot) {
                if (age >= AUTO_ACTION_TICKS) {
                    int choice = McrBot.choose(game.view(seat));
                    if (choice >= 0) apply(seat, decision, choice);
                }
            } else if (clockActive(seat) && clocks.get(seat).moveTicks() + clocks.get(seat).reserveTicks() == 0) {
                int fallback = -1;
                for (int index = 0; index < actions.size(); index++) {
                    var action = actions.get(index);
                    if (action.type() == McrAction.Type.PASS) { fallback = index; break; }
                    if (action.type() == McrAction.Type.DISCARD) {
                        if (fallback < 0) fallback = index;
                        if (action.tiles().get(0) == game.drawn(seat)) { fallback = index; break; }
                    }
                }
                if (fallback >= 0) apply(seat, decision, fallback);
            }
        }
        if (age == 1 || age % 10 == 0) changed(false);
    }

    private boolean clockActive(int seat) {
        return !participants[seat].bot && !paused() && exitVote == null && (game.phase() == McrGame.Phase.TURN || game.phase() == McrGame.Phase.REACTION)
            && !game.actions(seat).isEmpty();
    }

    private void resetDecision(boolean newHand) {
        age = 0;
        for (int seat = 0; seat < 4; seat++) {
            int reserve = newHand ? timeControl.reserveSeconds() * 20 : clocks.get(seat).reserveTicks();
            var clock = new TimeControl.Clock(timeControl.moveSeconds() * 20, reserve, false);
            if (clocks.size() <= seat) clocks.add(clock);
            else clocks.set(seat, clock);
        }
    }

    private void nextHand() {
        game.nextHand();
        if (replay != null) recorder = new McrReplayRecorder(game);
        confirmed = 0;
        resetDecision(true);
        changed(false);
    }

    @Override protected boolean pauseForAbsence() { return !hasSeatedHuman(); }
    @Override protected boolean allowsBots() { return worldPolicy.allowBots(); }

    @Override protected void addBotChoices(List<RoomAction> actions, int target, Participant member) {
        if (!member.bot) actions.add(new RoomAction(RoomAction.Type.SET_BOT, List.of(target, 0)));
    }

    @Override protected void setBotChoice(int target, int choice) {
        if (choice != 0) throw new IllegalArgumentException("Unknown MCR bot");
        setBot(target, BotDifficulty.EASY);
    }

    @Override public void configureWorld(WorldPolicy policy) {
        boolean botsChanged = worldPolicy.allowBots() != policy.allowBots();
        super.configureWorld(policy);
        if (lobby() && !policy.allowBots()) {
            boolean removed = false;
            for (int seat = 0; seat < 4; seat++) if (participants[seat].bot) {
                participants[seat] = new Participant();
                removed = true;
            }
            if (removed) {
                seating = new RoomSeating();
                resetReadiness();
                botsChanged = true;
            }
        }
        if (botsChanged) changed(lobby());
    }
    @Override protected boolean canReturnToLobby(int seat) { return seat == host(); }

    /** An absent participant gets the same concealed-data protection as an unprivileged spectator. */
    public View view(UUID recipient) {
        if (game == null) return null;
        var visibleClocks = new ArrayList<TimeControl.Clock>(4);
        for (int seat = 0; seat < 4; seat++) {
            var clock = clocks.get(seat);
            visibleClocks.add(new TimeControl.Clock(clock.moveTicks(), clock.reserveTicks(), clockActive(seat)));
        }
        return new View(tableId, incarnation, revision, participants(), seated(), confirmed, paused() || exitVote != null,
            visibleClocks, game.phase() == McrGame.Phase.HAND_END ? SETTLEMENT_TICKS - age : 0,
            McrView.project(game, viewerSeat(recipient), !paused() && exitVote == null));
    }

    public State save() {
        return new State(State.FORMAT, saveRoom(), stock, confirmed, timeControl, clocks, age, game == null ? null : game.save(),
            replay, recorder == null ? null : recorder.save(), archiveQueue);
    }

    private void finishReplay() {
        replay = replay.appendMcr(recorder.finish(game), game.phase() == McrGame.Phase.MATCH_END);
        archiveQueue.removeIf(match -> match.id().equals(replay.id()));
        archiveQueue.add(replay);
        recorder = null;
    }

    public List<ReplayMatch> pendingReplays() { return List.copyOf(archiveQueue); }
    public void acknowledgeReplay(UUID id) { archiveQueue.removeIf(match -> match.id().equals(id)); }

    /** A private envelope, not a wire view. No mount state or reusable request incarnation is persisted. */
    public record State(int format, TableSession.State room, List<Integer> stock, int confirmed,
                        TimeControl timeControl, List<TimeControl.Clock> clocks, int age, McrGameState game,
                        ReplayMatch replay, McrReplayRecorder.State recorder, List<ReplayMatch> archiveQueue) {
        public static final int FORMAT = 8;

        public State {
            if (format != FORMAT) throw new IllegalArgumentException("Unsupported MCR session format");
            Objects.requireNonNull(room);
            Objects.requireNonNull(timeControl);
            clocks = List.copyOf(clocks);
            if (clocks.size() != (game == null ? 0 : 4) || age < 0 || age > 14_400 || game == null && age != 0
                || game != null && game.phase() == McrGame.Phase.HAND_END && age >= SETTLEMENT_TICKS
                || clocks.stream().anyMatch(clock -> clock.active() || clock.moveTicks() < 0
                    || clock.moveTicks() > timeControl.moveSeconds() * 20 || clock.reserveTicks() < 0
                    || clock.reserveTicks() > timeControl.reserveSeconds() * 20))
                throw new IllegalArgumentException("Invalid MCR clock state");
            stock = List.copyOf(stock);
            archiveQueue = List.copyOf(archiveQueue);
            if (room.variant() != MahjongVariant.MCR || room.capacity() != 4 || room.manual()
                || !stock.isEmpty() && !Tile.validMcrSet(stock)
                || confirmed < 0 || confirmed >= 15 || confirmed != 0 && (game == null || game.phase() != McrGame.Phase.HAND_END)
                || (room.lifecycle() == Lifecycle.LOBBY) != (game == null)
                || recorder != null && (replay == null || game == null || game.result() != null || recorder.number() != game.handNumber())
                || replay != null && replay.variant() != MahjongVariant.MCR
                || archiveQueue.stream().anyMatch(match -> match.variant() != MahjongVariant.MCR || match.handCount() == 0)
                || game != null && (!Tile.validMcrSet(stock)
                    || (room.lifecycle() == Lifecycle.FINISHED) != (game.phase() == McrGame.Phase.MATCH_END)))
                throw new IllegalArgumentException("Invalid MCR session state");
            if (game != null) roster(room.participants());
        }
    }

    /** Identity-bound recipient projection. Access to private game state is never part of this contract. */
    public record View(UUID tableId, UUID incarnation, long revision, List<TableParticipant> participants,
                       int seated, int confirmed, boolean paused, List<TimeControl.Clock> clocks, int settlementTicks, McrView game) {
        public View {
            Objects.requireNonNull(tableId);
            Objects.requireNonNull(incarnation);
            participants = roster(participants);
            Objects.requireNonNull(game);
            clocks = List.copyOf(clocks);
            if (revision < 1 || seated < 0 || seated > 15 || confirmed < 0 || confirmed >= 15
                || confirmed != 0 && game.phase() != McrGame.Phase.HAND_END
                || game.viewerSeat() >= 0 && (seated & (1 << game.viewerSeat())) == 0
                || paused && !game.actions().isEmpty() || clocks.size() != 4 || settlementTicks < 0
                || settlementTicks > SETTLEMENT_TICKS || clocks.stream().anyMatch(clock -> clock.moveTicks() < 0
                    || clock.reserveTicks() < 0 || paused && clock.active()))
                throw new IllegalArgumentException("Invalid MCR session view");
        }

        public boolean canConfirmNextHand() {
            return !paused && game.viewerSeat() >= 0 && game.phase() == McrGame.Phase.HAND_END
                && (confirmed & (1 << game.viewerSeat())) == 0;
        }
    }

    private static List<TableParticipant> roster(List<TableParticipant> participants) {
        var copy = List.copyOf(participants);
        var identities = new HashSet<UUID>();
        if (copy.size() != 4 || copy.stream().noneMatch(player -> !player.bot())
            || copy.stream().anyMatch(player -> player.id() == null || player.entityBot() || player.externalBotId() != null
                || player.bot() && player.difficulty() != BotDifficulty.EASY || !identities.add(player.id())))
            throw new IllegalArgumentException("An MCR session requires four distinct participants in seat order");
        return copy;
    }
}
