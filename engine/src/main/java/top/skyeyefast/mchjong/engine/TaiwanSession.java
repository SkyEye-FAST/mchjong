package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Taiwanese match with humans and built-in opponents in the shared room lifecycle. */
public final class TaiwanSession extends TableSession {
    private static final long SEED_STEP = 0x9e3779b97f4a7c15L;
    private static final int READING_TICKS = 200;
    private static final int BOT_ACTION_TICKS = 12;
    private TaiwanGameState.Rules rules;
    private List<Integer> stock = List.of();
    private TimeControl control = TimeControl.DEFAULT;
    private final List<TimeControl.Clock> clocks = new ArrayList<>();
    private final List<TaiwanGameState> completed = new ArrayList<>();
    private TaiwanGame game;
    private long futureSeed;
    private int confirmed;
    private int age;
    private ReplayMatch replay;
    private TaiwanReplayRecorder recorder;
    private final List<ReplayMatch> archiveQueue = new ArrayList<>();

    public TaiwanSession(UUID tableId, long seed, TaiwanRules rules) {
        super(tableId, MahjongVariant.TAIWAN, 4, seed);
        this.rules = TaiwanGameState.Rules.of(Objects.requireNonNull(rules));
    }
    public TaiwanSession(UUID tableId, long seed) { this(tableId,seed,TaiwanPreset.POCKET_COMMON.rules()); }
    public TaiwanGame game() { return game; }
    public TaiwanRules rules() { return rules.restore(); }
    @Override protected long matchDecision() { return game == null ? decision : game.getDecision(); }
    @Override protected boolean pauseForAbsence() { return !hasSeatedHuman(); }
    @Override protected boolean allowsBots() { return worldPolicy.allowBots(); }
    @Override protected void addBotChoices(List<RoomAction> actions, int target, Participant member) {
        for (var difficulty : BotDifficulty.values()) if (!member.bot || member.botDifficulty != difficulty)
            actions.add(new RoomAction(RoomAction.Type.SET_BOT,List.of(target,difficulty.ordinal())));
    }
    @Override protected void setBotChoice(int target, int choice) {
        if (choice < 0 || choice >= BotDifficulty.values().length) throw new IllegalArgumentException("Unknown Taiwan bot");
        setBot(target,BotDifficulty.values()[choice]);
    }
    @Override public void configureWorld(WorldPolicy policy) {
        boolean botsChanged = worldPolicy.allowBots() != policy.allowBots();
        super.configureWorld(policy);
        if (lobby() && !policy.allowBots()) {
            for (int seat = 0; seat < 4; seat++) if (participants[seat].bot) {
                participants[seat] = new Participant(); botsChanged = true;
            }
            if (botsChanged) { seating = new RoomSeating(); resetReadiness(); }
        }
        if (botsChanged) changed(lobby());
    }
    @Override protected boolean canReturnToLobby(int seat) { return seat == host(); }
    @Override public TimeControl timeControl() { return control; }
    @Override public boolean configureClock(UUID actor, TimeControl value) {
        if (!lobby() || exitVote != null || !isHost(actor)) return false;
        control = Objects.requireNonNull(value); resetReadiness(); changed(true); return true;
    }
    public TaiwanRoomSettings roomSettings() { return new TaiwanRoomSettings(rules, control, worldPolicy.allowCustomRules()); }
    public boolean configureRules(UUID actor, long token, TaiwanRules value) {
        if (token != decision) return false;
        return configureRules(actor, value);
    }
    public boolean configureRules(UUID actor, TaiwanRules value) {
        if (!lobby() || exitVote != null || !isHost(actor)) return false;
        var proposed = TaiwanGameState.Rules.of(value);
        if (!worldPolicy.allowCustomRules() && java.util.Arrays.stream(TaiwanPreset.values()).noneMatch(p -> TaiwanGameState.Rules.of(p.rules()).equals(proposed))) return false;
        if (proposed.equals(rules)) return false;
        rules = proposed; resetReadiness(); changed(true); return true;
    }
    @Override public boolean equipped() { return validStock(stock,rules); }
    private static boolean validStock(List<Integer> tiles, TaiwanGameState.Rules rules) {
        var expected = rules.flowers() == TaiwanRules.Flowers.NONE ? Tile.set(false,RedFives.NONE) : Tile.standard144Set();
        return tiles.size() == expected.size() && new java.util.HashSet<>(tiles).equals(new java.util.HashSet<>(expected));
    }
    @Override public boolean configureEquipment(boolean manual, List<Integer> tiles) {
        if (manual || !lobby() || exitVote != null) return false;
        tiles = List.copyOf(tiles);
        if (!tiles.isEmpty() && !(Tile.validStandard144Set(tiles) || tiles.size() == 136 && new java.util.HashSet<>(tiles).equals(new java.util.HashSet<>(Tile.set(false,RedFives.NONE)))))
            throw new IllegalArgumentException("Taiwan needs an undecorated 136/144 stock");
        if (stock.equals(tiles)) return false;
        stock = tiles; resetReadiness(); changed(true); return true;
    }
    /** Engine hosts may supply an already positioned roster without Minecraft dependencies. */
    public static TaiwanSession start(UUID table, List<TableParticipant> roster, long seed, List<Integer> stock, TaiwanRules rules) {
        validateRoster(roster);
        var session = new TaiwanSession(table,seed,rules);
        for (int s = 0; s < 4; s++) session.participants[s] = Participant.restore(roster.get(s));
        session.hostId = roster.stream().filter(p -> !p.bot()).findFirst().orElseThrow().id();
        session.seating.positioned(4);
        session.configureEquipment(false,stock);
        session.startMatch();
        return session;
    }
    @Override protected void startMatch() {
        if (!equipped()) throw new IllegalStateException("Missing Taiwan stock");
        validateRoster(participants());
        completed.clear(); futureSeed = seed;
        if (worldPolicy.replaysEnabled()) {
            long now = System.currentTimeMillis();
            replay = new ReplayMatch(UUID.randomUUID(),tableId,now,now,
                participants().stream().map(p -> new ReplayMatch.Participant(p.id(),p.name(),p.bot())).toList(),
                MahjongVariant.TAIWAN,false,null,null,null,new TaiwanReplay(rules,List.of()));
        }
        deal(0,Tile.EAST,0);
        lifecycle = Lifecycle.PLAYING; renewIncarnation(); updateEnd();
    }
    private void deal(int dealer, int wind, int continuation) {
        long previous = game == null ? 0 : game.getDecision();
        var random = new java.util.Random(futureSeed);
        futureSeed += SEED_STEP;
        var tiles = new ArrayList<>(stock); Collections.shuffle(tiles,random);
        var dice = List.of(random.nextInt(6)+1,random.nextInt(6)+1,random.nextInt(6)+1);
        game = new TaiwanGame(rules.restore(),new TaiwanOpening(dealer,dice),tiles,wind,continuation);
        game.rebaseDecision(previous);
        if (replay != null) {
            var initial = new long[4];
            for (var hand : completed) for (int s = 0; s < 4; s++) initial[s] = Math.addExact(initial[s],hand.settlement().deltas().get(s));
            recorder = new TaiwanReplayRecorder(completed.size()+1,List.of(initial[0],initial[1],initial[2],initial[3]),game);
            if (game.getPhase() == TaiwanGame.Phase.FINISHED) finishReplay();
        }
        confirmed = 0; resetClock(true);
    }
    @Override protected void clearMatch() {
        if (replay != null && !replay.complete() && replay.handCount() > 0) archiveQueue.add(replay);
        game = null; replay = null; recorder = null; completed.clear(); clocks.clear(); confirmed = age = 0; futureSeed = 0;
    }
    private void finishReplay() {
        replay = replay.appendTaiwan(recorder.finish(game),matchEnded()); recorder = null;
        if (replay.complete()) archiveQueue.add(replay);
    }
    public List<ReplayMatch> pendingReplays() { return List.copyOf(archiveQueue); }
    public void acknowledgeReplay(UUID id) { archiveQueue.removeIf(match -> match.id().equals(id)); }
    private int rotations() {
        return (int) completed.stream().filter(h -> h.settlement().nextDealer() != h.opening().dealer()).count();
    }
    public boolean matchEnded() {
        return game != null && game.getSettlement() != null && rotations() == 15
            && game.getSettlement().getNextDealer() != game.getOpening().getDealer();
    }
    public List<Long> scores() {
        var totals = new long[4];
        for (var hand : completed) for (int s = 0; s < 4; s++) totals[s] = Math.addExact(totals[s],hand.settlement().deltas().get(s));
        if (game != null && game.getSettlement() != null) for (int s = 0; s < 4; s++) totals[s] = Math.addExact(totals[s],game.getSettlement().getDeltas().get(s));
        return List.of(totals[0],totals[1],totals[2],totals[3]);
    }
    private void updateEnd() { if (matchEnded()) lifecycle = Lifecycle.FINISHED; }
    public boolean act(UUID actor, UUID table, UUID incarnation, long token, int index) {
        if (game == null || lifecycle != Lifecycle.PLAYING || paused() || exitVote != null) return false;
        int seat = authorize(actor,table,incarnation,token,game.getDecision());
        return seat >= 0 && apply(seat,token,index);
    }
    private boolean apply(int seat, long token, int index) {
        var d = game.decisions().stream().filter(v -> v.getSeat() == seat && v.getToken() == token).findFirst().orElse(null);
        if (d == null || index < 0 || index >= d.getActions().size()) return false;
        var before = recorder == null ? null : game.save();
        game.submit(seat,token,index);
        if (recorder != null) {
            recorder.accepted(before,seat,d.getActions(),index,game);
            if (game.getPhase() == TaiwanGame.Phase.FINISHED) finishReplay();
        }
        if (game.getPhase() == TaiwanGame.Phase.FINISHED && !matchEnded()) confirmBots();
        if (game.getDecision() != token || game.getPhase() == TaiwanGame.Phase.FINISHED) resetClock(false);
        updateEnd(); changed(false); return true;
    }
    public boolean confirmNextHand(UUID actor, UUID table, UUID incarnation, long token) {
        if (game == null || paused() || exitVote != null || lifecycle != Lifecycle.PLAYING || game.getPhase() != TaiwanGame.Phase.FINISHED) return false;
        int seat = authorize(actor,table,incarnation,token,game.getDecision());
        if (seat < 0 || participants[seat].bot || (confirmed & 1 << seat) != 0) return false;
        confirmBots();
        confirmed |= 1 << seat;
        if (confirmed == 15) nextHand();
        changed(false); return true;
    }
    private void confirmBots() {
        for (int seat = 0; seat < 4; seat++) if (participants[seat].bot) confirmed |= 1 << seat;
    }
    private void nextHand() {
        if (game.getPhase() != TaiwanGame.Phase.FINISHED || matchEnded()) throw new IllegalStateException("Cannot advance hand");
        var result = game.getSettlement();
        completed.add(game.save());
        int rotation = rotations();
        deal(result.getNextDealer(),Tile.EAST + rotation / 4,result.getNextContinuation());
        updateEnd(); changed(false);
    }
    private void resetClock(boolean newHand) {
        age = 0;
        if (newHand) resetAutomation();
        for (int s = 0; s < 4; s++) {
            int reserve = newHand ? control.reserveSeconds()*20 : clocks.get(s).reserveTicks();
            var clock = new TimeControl.Clock(control.moveSeconds()*20,reserve,false);
            if (clocks.size() <= s) clocks.add(clock); else clocks.set(s,clock);
        }
    }
    private boolean active(int seat) { return !participants[seat].bot && game != null && !paused() && exitVote == null && game.decisions().stream().anyMatch(d -> d.getSeat() == seat); }
    @Override public void tick() {
        if (!tickRoom() || game == null || lifecycle != Lifecycle.PLAYING) return;
        age = Math.min(14_400,age + 1);
        if (game.getPhase() == TaiwanGame.Phase.FINISHED) {
            confirmBots();
            if (confirmed == 15 || age >= READING_TICKS) nextHand();
            else if (age % 10 == 0) changed(false);
            return;
        }
        long token = game.getDecision();
        for (int s = 0; s < 4; s++) if (active(s)) {
            var c = clocks.get(s); var next = new TimeControl.Clock(c.moveTicks(),c.reserveTicks(),true).after(50);
            clocks.set(s,new TimeControl.Clock(next.moveTicks(),next.reserveTicks(),false));
        }
        for (var d : game.decisions()) {
            var c = clocks.get(d.getSeat());
            if (game.getDecision() != token) break;
            if (participants[d.getSeat()].bot) {
                if (age >= BOT_ACTION_TICKS) {
                    int choice = TaiwanBot.choose(game.view(d.getSeat()));
                    if (choice >= 0) apply(d.getSeat(),token,choice);
                }
                continue;
            }
            if (c.moveTicks()+c.reserveTicks() > 0) continue;
            int fallback = -1;
            int drawn = game.view(d.getSeat()).seats().get(d.getSeat()).drawn();
            for (int i = 0; i < d.getActions().size(); i++) {
                var a = d.getActions().get(i);
                if (a.getType() == TaiwanAction.Type.PASS) { fallback = i; break; }
                if (a.getType() == TaiwanAction.Type.DISCARD) {
                    if (fallback < 0) fallback = i;
                    if (a.getTiles().get(0) == drawn) { fallback = i; break; }
                }
            }
            if (fallback >= 0) apply(d.getSeat(),token,fallback);
        }
        if (age % 10 == 0) changed(false);
    }

    public State save() { return new State(State.FORMAT,saveRoom(),rules,stock,control,clocks,age,confirmed,futureSeed,completed,
        game == null ? null : game.save(),replay,recorder == null ? null : recorder.save(),archiveQueue); }
    public static TaiwanSession restore(State state) {
        var session = new TaiwanSession(state.room().tableId(),state.room().seed(),state.rules().restore());
        session.restoreRoom(state.room()); session.stock = state.stock(); session.control = state.control();
        session.clocks.addAll(state.clocks()); session.age = state.age(); session.confirmed = state.confirmed(); session.futureSeed = state.futureSeed();
        int rotations = 0; int dealer = 0; int continuation = 0;
        for (var saved : state.completed()) {
            validatePosition(saved,state.rules(),dealer,Tile.EAST+rotations/4,continuation);
            var restored = TaiwanGame.restore(saved);
            if (restored.getPhase() != TaiwanGame.Phase.FINISHED || rotations >= 16) throw new IllegalArgumentException("Invalid completed Taiwan hand");
            var result = restored.getSettlement();
            if (result.getNextDealer() != dealer) rotations++;
            dealer = result.getNextDealer(); continuation = result.getNextContinuation();
            session.completed.add(saved);
        }
        if (state.game() != null) {
            if (rotations >= 16) throw new IllegalArgumentException("Taiwan match already ended");
            validatePosition(state.game(),state.rules(),dealer,Tile.EAST+rotations/4,continuation);
            session.game = TaiwanGame.restore(state.game());
            if (state.futureSeed() != state.room().seed() + SEED_STEP*(state.completed().size()+1L)
                || (state.room().lifecycle() == Lifecycle.FINISHED) != session.matchEnded()) throw new IllegalArgumentException("Invalid Taiwan match chain");
            validateRoster(session.participants()); session.scores();
        }
        session.replay = state.replay();
        if (state.replay() != null) {
            ReplayCodec.validate(state.replay());
            for (int i = 0; i < state.replay().handCount(); i++) {
                var proof = i < state.completed().size() ? state.completed().get(i) : state.game();
                if (!TaiwanReplayPlayback.samePosition(state.replay().taiwan().hands().get(i).finalState(),proof))
                    throw new IllegalArgumentException("Taiwan replay history disagrees with match");
            }
        }
        if (state.recorder() != null) {
            if (!TaiwanReplayPlayback.samePosition(TaiwanReplayPlayback.reconstruct(state.recorder()).save(),state.game()))
                throw new IllegalArgumentException("Taiwan recorder disagrees with saved game");
            session.recorder = new TaiwanReplayRecorder(state.recorder());
        }
        for (var archived : state.archiveQueue()) ReplayCodec.validate(archived);
        session.archiveQueue.addAll(state.archiveQueue());
        return session;
    }
    private static void validatePosition(TaiwanGameState state, TaiwanGameState.Rules rules, int dealer, int wind, int continuation) {
        if (!state.rules().equals(rules) || state.opening().dealer() != dealer || state.roundWind() != wind || state.continuation() != continuation)
            throw new IllegalArgumentException("Invalid Taiwan dealer/round chain");
    }
    private static void validateRoster(List<TableParticipant> roster) {
        if (roster.size() != 4 || roster.stream().anyMatch(p -> p.id() == null || p.entityBot() || p.externalBotId() != null || p.bot() && !p.ready())
            || roster.stream().allMatch(TableParticipant::bot) || roster.stream().map(TableParticipant::id).distinct().count() != 4)
            throw new IllegalArgumentException("Taiwan requires four participants including a human and only built-in bots");
    }
    public record State(int format, TableSession.State room, TaiwanGameState.Rules rules, List<Integer> stock,
                        TimeControl control, List<TimeControl.Clock> clocks, int age, int confirmed, long futureSeed,
                        List<TaiwanGameState> completed, TaiwanGameState game, ReplayMatch replay,
                        TaiwanReplayRecorder.State recorder, List<ReplayMatch> archiveQueue) {
        public static final int FORMAT = 2;
        public State {
            Objects.requireNonNull(room); Objects.requireNonNull(rules); Objects.requireNonNull(control);
            stock = List.copyOf(stock); clocks = List.copyOf(clocks); completed = List.copyOf(completed);
            archiveQueue = List.copyOf(archiveQueue);
            if (replay != null && (game == null || replay.variant() != MahjongVariant.TAIWAN || !replay.tableId().equals(room.tableId())
                || !replay.taiwan().rules().equals(rules) || replay.handCount() != completed.size()+(game.settlement() == null ? 0 : 1)
                || replay.complete() != (room.lifecycle() == Lifecycle.FINISHED)
                || !replay.participants().equals(room.participants().stream().map(p -> new ReplayMatch.Participant(p.id(),p.name(),p.bot())).toList()))
                || replay != null && (game.settlement() == null) != (recorder != null)
                || recorder != null && (replay == null || recorder.number() != completed.size()+1 || !recorder.rules().equals(rules)
                    || !recorder.opening().equals(game.opening()) || recorder.roundWind() != game.roundWind() || recorder.continuation() != game.continuation()
                    || !recorder.initialScores().equals(replay.handCount() == 0 ? List.of(0L,0L,0L,0L) : replay.taiwan().hands().get(replay.handCount()-1).finalScores()))
                || archiveQueue.stream().map(ReplayMatch::id).distinct().count() != archiveQueue.size()
                || archiveQueue.stream().anyMatch(m -> m.variant() != MahjongVariant.TAIWAN || m.handCount() == 0 || !m.tableId().equals(room.tableId())
                    || replay != null && m.id().equals(replay.id()) && (!replay.complete() || !m.equals(replay))))
                throw new IllegalArgumentException("Invalid Taiwan replay session state");
            if (format != FORMAT || room.variant() != MahjongVariant.TAIWAN || room.capacity() != 4 || room.manual()
                || room.participants().stream().anyMatch(p -> p.entityBot() || p.externalBotId() != null || p.bot() && !p.ready())
                || (room.lifecycle() == Lifecycle.LOBBY) != (game == null) || age < 0 || age > 14_400
                || confirmed < 0 || confirmed >= 15 || confirmed != 0 && (game == null || game.phase() != TaiwanGame.Phase.FINISHED || room.lifecycle() != Lifecycle.PLAYING)
                || clocks.size() != (game == null ? 0 : 4) || game == null && (!completed.isEmpty() || age != 0 || futureSeed != 0)
                || game != null && (!validStock(stock,rules) || game.phase() == TaiwanGame.Phase.FINISHED && age >= READING_TICKS)
                || clocks.stream().anyMatch(c -> c.active() || c.moveTicks() < 0 || c.reserveTicks() < 0 || c.moveTicks() > control.moveSeconds()*20 || c.reserveTicks() > control.reserveSeconds()*20))
                throw new IllegalArgumentException("Invalid Taiwan session state");
        }
    }
    /** Recipient-safe match projection; common preparation uses TableRoomView. */
    public View view(UUID recipient) {
        int seat = lobby() ? seatOf(recipient) : viewerSeat(recipient);
        boolean stopped = paused() || exitVote != null;
        TimeControl.Clock visible = null;
        if (game != null && seat >= 0) { var c = clocks.get(seat); visible = new TimeControl.Clock(c.moveTicks(),c.reserveTicks(),active(seat)); }
        return new View(tableId,incarnation,revision,decision,lifecycle,seat,host(),participants(),seated(),rules,control,equipped(),stopped,
            seat < 0 ? List.of() : roomActions(recipient),exitVote,leaveDecision(recipient),confirmed,age,
            visible,completed.size()+1,scores(),game == null ? null : game.view(seat,!stopped));
    }
    public record View(UUID tableId, UUID incarnation, long revision, long roomDecision, Lifecycle lifecycle, int recipient, int host,
                       List<TableParticipant> participants, int seated, TaiwanGameState.Rules rules, TimeControl control,
                       boolean equipped, boolean paused, List<RoomAction> roomActions, ExitVote exitVote, boolean leaveDecision,
                       int confirmed, int age, TimeControl.Clock clock, int handNumber, List<Long> scores, TaiwanView game) {
        public View {
            Objects.requireNonNull(tableId); Objects.requireNonNull(incarnation); Objects.requireNonNull(lifecycle);
            Objects.requireNonNull(rules); Objects.requireNonNull(control);
            participants = List.copyOf(participants); roomActions = List.copyOf(roomActions); scores = List.copyOf(scores);
            if (revision < 1 || roomDecision < 1 || recipient < -1 || recipient > 3 || host < -1 || host > 3 || participants.size() != 4
                || seated < 0 || seated > 15 || confirmed < 0 || confirmed >= 15 || age < 0 || age > 14_400 || handNumber < 1
                || scores.size() != 4 || scores.stream().mapToLong(Long::longValue).sum() != 0
                || (lifecycle == Lifecycle.LOBBY) != (game == null) || (clock != null) != (game != null && recipient >= 0)
                || recipient < 0 && !roomActions.isEmpty()
                || game != null && (game.recipient() != recipient || !game.rules().equals(rules) || paused && !game.actions().isEmpty())
                || clock != null && (clock.moveTicks() < 0 || clock.reserveTicks() < 0 || clock.moveTicks() > control.moveSeconds()*20 || clock.reserveTicks() > control.reserveSeconds()*20 || paused && clock.active()))
                throw new IllegalArgumentException("Invalid Taiwan session view");
        }
    }
}
