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

    public McrSession(UUID tableId, long seed) {
        super(tableId, MahjongVariant.MCR, 4, seed);
    }

    /** Copy the prepared room roster in seat order; never populate it from a client action. */
    public static McrSession start(UUID tableId, List<TableParticipant> participants, long seed, List<Integer> stock) {
        var roster = roster(participants);
        var session = new McrSession(tableId, seed);
        for (int seat = 0; seat < 4; seat++) session.participants[seat] = Participant.restore(roster.get(seat));
        session.hostId = roster.get(0).id();
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
        if (!game.act(seat, decision, actionIndex)) return false;
        if (game.decision() != decision) resetDecision(false);
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
        resetDecision(true);
        confirmed = 0;
        lifecycle = Lifecycle.PLAYING;
        renewIncarnation();
    }

    protected void clearMatch() {
        game = null;
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
        return !paused() && exitVote == null && (game.phase() == McrGame.Phase.TURN || game.phase() == McrGame.Phase.REACTION)
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
        confirmed = 0;
        resetDecision(true);
        changed(false);
    }

    @Override protected boolean pauseForAbsence() { return !hasSeatedHuman(); }
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
        return new State(State.FORMAT, saveRoom(), stock, confirmed, timeControl, clocks, age, game == null ? null : game.save());
    }

    /** A private envelope, not a wire view. No mount state or reusable request incarnation is persisted. */
    public record State(int format, TableSession.State room, List<Integer> stock, int confirmed,
                        TimeControl timeControl, List<TimeControl.Clock> clocks, int age, McrGameState game) {
        public static final int FORMAT = 6;

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
            if (room.variant() != MahjongVariant.MCR || room.capacity() != 4 || room.manual()
                || !stock.isEmpty() && !Tile.validMcrSet(stock)
                || confirmed < 0 || confirmed >= 15 || confirmed != 0 && (game == null || game.phase() != McrGame.Phase.HAND_END)
                || (room.lifecycle() == Lifecycle.LOBBY) != (game == null)
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
        if (copy.size() != 4 || copy.stream().anyMatch(player -> player.id() == null || player.bot() || !identities.add(player.id())))
            throw new IllegalArgumentException("An MCR session requires four distinct participants in seat order");
        return copy;
    }
}
