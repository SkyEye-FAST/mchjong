package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static top.skyeyefast.mchjong.engine.RiichiAction.Type.*;

/** One already-started Riichi match. The owning session provides room authority. */
public final class RiichiGame {
    private static final long WALL_SEED_STEP = 0x9e3779b97f4a7c15L;
    public static final int DEAL_TICKS = 56;
    public static final int AUTO_ACTION_TICKS = 12;
    public static final int SETTLEMENT_TICKS = 10 * 20;
    public enum Phase { SHUFFLE, BUILD_WALL, DEAL, DRAW, TURN, REACTION, HAND_END, MATCH_END }

    final RiichiSession session;
    final RiichiRules rules;
    final UUID tableId;
    final long seed;
    final boolean manual;
    final List<Integer> suppliedTiles;
    final TimeControl timeControl;
    final PlayerHandVisibility playerHandVisibility;
    final boolean openHands;
    int handNumber;
    int age;
    RiichiPlayerState[] players = new RiichiPlayerState[4];
    Phase phase;
    int dealer;
    int initialDealer;
    int round;
    int honba;
    int riichiSticks;
    int turn;
    Wall wall;
    int lastTile = Tile.ABSENT;
    int lastFrom = -1;
    RiichiAction pending;
    boolean uninterrupted = true;
    boolean fourKanAbort;
    boolean dealerRepeats;
    boolean drawResult;
    boolean abortResult;
    boolean[] exposed = new boolean[4];
    int[] replies = {-1, -1, -1, -1};
    List<List<RiichiAction>> options = new ArrayList<>();
    List<RiichiView.Win> wins = new ArrayList<>();
    private long presentedDecision = -1;
    private int presentedSeats;
    private long skippedDecision = -1;
    private int skippedSeats;
    String result = "playing";
    List<Integer> deltas = new ArrayList<>(Collections.nCopies(4, 0));
    List<Double> finalScores = new ArrayList<>();
    List<Double> finalUma = new ArrayList<>();
    List<Integer> finalRanks = new ArrayList<>();
    ReplayMatch replay;
    ReplayRecorder recorder;
    int[] moveTicks = new int[4];
    int[] reserveTicks = new int[4];
    ManualHandling handling = new ManualHandling();

    /** Detached private match state; room identity and configuration live in RiichiSession.State. */
    public record State(int handNumber, int age, List<RiichiPlayerState.Saved> players, Phase phase,
                        int dealer, int initialDealer, int round, int honba, int riichiSticks, int turn,
                        Wall.Saved wall, int lastTile, int lastFrom, RiichiAction pending,
                        boolean uninterrupted, boolean fourKanAbort, boolean dealerRepeats,
                        boolean drawResult, boolean abortResult, List<Boolean> exposed,
                        List<Integer> replies, List<List<RiichiAction>> options, List<RiichiView.Win> wins,
                        long presentedDecision, int presentedSeats, long skippedDecision, int skippedSeats,
                        String result, List<Integer> deltas, List<Double> finalScores,
                        List<Double> finalUma, List<Integer> finalRanks, ReplayMatch replay,
                        ReplayRecorderState recorder, List<Integer> moveTicks, List<Integer> reserveTicks,
                        ManualHandling.Saved handling) {
        public State {
            players = List.copyOf(players);
            exposed = List.copyOf(exposed);
            replies = List.copyOf(replies);
            options = options.stream().map(List::copyOf).toList();
            wins = List.copyOf(wins);
            deltas = List.copyOf(deltas);
            finalScores = List.copyOf(finalScores);
            finalUma = List.copyOf(finalUma);
            finalRanks = List.copyOf(finalRanks);
            moveTicks = List.copyOf(moveTicks);
            reserveTicks = List.copyOf(reserveTicks);
            Objects.requireNonNull(phase);
            Objects.requireNonNull(result);
            Objects.requireNonNull(handling);
            if (handNumber < 1 || players.size() != 4 || exposed.size() != 4 || replies.size() != 4
                || options.size() != 4 || moveTicks.size() != 4 || reserveTicks.size() != 4
                || presentedSeats < 0 || presentedSeats > 15 || skippedSeats < 0 || skippedSeats > 15
                || recorder != null && wall == null)
                throw new IllegalArgumentException("Invalid Riichi match state");
        }
    }

    public State save() {
        validate();
        return new State(handNumber, age, Arrays.stream(players).map(RiichiPlayerState::save).toList(), phase,
            dealer, initialDealer, round, honba, riichiSticks, turn, wall == null ? null : wall.save(),
            lastTile, lastFrom, pending, uninterrupted, fourKanAbort, dealerRepeats, drawResult, abortResult,
            java.util.stream.IntStream.range(0, 4).mapToObj(i -> exposed[i]).toList(),
            Arrays.stream(replies).boxed().toList(), options, wins,
            presentedDecision, presentedSeats, skippedDecision, skippedSeats, result, deltas,
            finalScores, finalUma, finalRanks, replay, recorder == null ? null : recorder.save(),
            Arrays.stream(moveTicks).boxed().toList(), Arrays.stream(reserveTicks).boxed().toList(), handling.save());
    }

    static RiichiGame restore(RiichiSession session, State state, long savedDecision) {
        var game = new RiichiGame(session);
        game.handNumber = state.handNumber();
        game.age = state.age();
        game.players = new RiichiPlayerState[4];
        for (int seat = 0; seat < 4; seat++)
            game.players[seat] = RiichiPlayerState.restore(state.players().get(seat), session.participants[seat]);
        game.phase = state.phase();
        game.dealer = state.dealer();
        game.initialDealer = state.initialDealer();
        game.round = state.round();
        game.honba = state.honba();
        game.riichiSticks = state.riichiSticks();
        game.turn = state.turn();
        game.wall = state.wall() == null ? null : Wall.restore(state.wall());
        game.lastTile = state.lastTile();
        game.lastFrom = state.lastFrom();
        game.pending = state.pending();
        game.uninterrupted = state.uninterrupted();
        game.fourKanAbort = state.fourKanAbort();
        game.dealerRepeats = state.dealerRepeats();
        game.drawResult = state.drawResult();
        game.abortResult = state.abortResult();
        for (int seat = 0; seat < 4; seat++) {
            game.exposed[seat] = state.exposed().get(seat);
            game.replies[seat] = state.replies().get(seat);
            game.moveTicks[seat] = state.moveTicks().get(seat);
            game.reserveTicks[seat] = state.reserveTicks().get(seat);
        }
        game.options = new ArrayList<>(state.options());
        game.wins = new ArrayList<>(state.wins());
        game.presentedDecision = state.presentedDecision();
        game.presentedSeats = state.presentedSeats();
        game.skippedDecision = state.skippedDecision();
        game.skippedSeats = state.skippedSeats();
        game.result = state.result();
        game.deltas = new ArrayList<>(state.deltas());
        game.finalScores = new ArrayList<>(state.finalScores());
        game.finalUma = new ArrayList<>(state.finalUma());
        game.finalRanks = new ArrayList<>(state.finalRanks());
        game.replay = state.replay();
        game.handling = ManualHandling.restore(state.handling());
        if (state.recorder() != null) game.recorder = ReplayRecorder.restore(game, state.recorder());
        game.restored(savedDecision);
        game.validate();
        return game;
    }
    RiichiGame(RiichiSession session) {
        this.session = Objects.requireNonNull(session);
        rules = session.rules;
        tableId = session.tableId;
        seed = session.seed;
        manual = session.manual;
        suppliedTiles = List.copyOf(session.suppliedTiles);
        timeControl = session.timeControl;
        playerHandVisibility = session.playerHandVisibility;
        openHands = session.openHands;
        for (int i = 0; i < 4; i++) {
            players[i] = new RiichiPlayerState(session.participants[i]);
            players[i].points = rules.startingPoints();
            options.add(List.of());
        }
    }

    public Phase phase() { return phase; }
    public RiichiRules rules() { return rules; }
    public int points(int seat) { return players[seat].points; }
    public UUID tableId() { return tableId; }
    public long decision() { return session.decision; }
    public long revision() { return session.revision; }
    public String externalBotId(int seat) { return session.externalBotId(seat); }
    public boolean equipped() { return session.equipped(); }

    /** Snapshot one active bot choice without disclosing other players' draws. */
    public BotPosition botPosition(int seat, UUID sessionId) {
        if (recorder == null || manual || age <= 0 || session.exitVote != null
            || !session.hasSeatedHuman() || externalBotId(seat) == null
            || phase != Phase.TURN && phase != Phase.REACTION || actions(seat).isEmpty()) return null;
        RiichiPlayerState player = players[seat];
        var pons = player.melds.stream().filter(meld -> meld.type() == Meld.Type.TRIPLET)
            .map(meld -> new BotPosition.Pon("PON", meld.tiles())).toList();
        return new BotPosition(1, player.member.externalBotId, rules.preset(), tableId, sessionId,
            handNumber, seat, rules.players(), session.decision,
            recorder.botOpening(seat), recorder.botEvents(seat), actions(seat),
            phase == Phase.REACTION ? new BotPosition.Focus(lastFrom, lastTile) : null,
            player.drawn < 0 ? null : player.drawn, pons);
    }

    public boolean actBot(int seat, long expectedDecision, int actionIndex) {
        return externalBotId(seat) != null && act(players[seat].member.id, expectedDecision, actionIndex);
    }
    void finishReplay() {
        if (recorder == null) return;
        if (replay == null) { recorder = null; return; }
        var completed = recorder.finish(this);
        replay = replay.appendRiichi(completed, phase == Phase.MATCH_END);
        session.archiveQueue.removeIf(match -> match.id().equals(replay.id()));
        session.archiveQueue.add(replay);
        recorder = null;
    }

    int settlementSkippedSeats() { return skippedDecision == session.decision ? skippedSeats : 0; }

    int settlementTicks() {
        int duration = phase == Phase.MATCH_END ? ScoreAnnouncements.maximumTicks(wins) + SETTLEMENT_TICKS
            : phase == Phase.HAND_END ? ScoreAnnouncements.maximumTicks(wins) : 0;
        return Math.max(0, duration - Math.max(0, age));
    }

    private boolean presentationComplete() {
        if (wins.isEmpty()) return true;
        if (presentedDecision != session.decision) return false;
        for (int seat = 0; seat < rules.players(); seat++) {
            var player = players[seat];
            if (!player.member.bot && player.member.presence == PlayerPresence.SEATED && (presentedSeats & (1 << seat)) == 0) return false;
        }
        return true;
    }

    private boolean settlementSkipped() {
        for (int seat = 0; seat < rules.players(); seat++) {
            var player = players[seat];
            if (player.member.id != null && !player.member.bot && (skippedSeats & (1 << seat)) == 0) return false;
        }
        return true;
    }
    public boolean configureAutoPlay(UUID actor, long expectedDecision, RiichiAutoPlay.Option option, boolean enabled) {
        int seat = session.seatOf(actor);
        if (manual || seat < 0 || players[seat].member.bot || session.exitVote != null || expectedDecision != session.decision
            || option == RiichiAutoPlay.Option.KITA && !rules.sanma()
            || players[seat].autoPlay.enabled(option) == enabled) return false;
        if (option == RiichiAutoPlay.Option.SORT && !enabled) {
            var player = players[seat];
            player.hand.sort(Tile.ORDER);
            if (player.drawn >= 0 && player.hand.remove(Integer.valueOf(player.drawn))) player.hand.add(player.drawn);
        }
        players[seat].autoPlay = players[seat].autoPlay.with(option, enabled);
        // Other responders retain their decision token and remaining time.
        session.revision++;
        return true;
    }

    public boolean reorderHand(UUID actor, long expectedDecision, int source, int target, boolean after) {
        int seat = session.seatOf(actor);
        if (seat < 0 || players[seat].member.bot || manual || players[seat].autoPlay.sort()
            || session.exitVote != null || expectedDecision != session.decision || source == target
            || !players[seat].hand.contains(source) || !players[seat].hand.contains(target)) return false;
        var hand = players[seat].hand;
        hand.remove(Integer.valueOf(source));
        hand.add(hand.indexOf(target) + (after ? 1 : 0), source);
        session.revision++;
        return true;
    }

    void rebindPlayers() {
        for (int seat = 0; seat < 4; seat++) players[seat].member = session.participants[seat];
    }

    List<RiichiAction> actions(int seat) {
        if (seat < 0 || seat >= rules.players() || players[seat].member.id == null || session.exitVote != null) return List.of();
        if (ManualHandling.active(phase)) return handling.actions(this, seat);
        if (phase == Phase.HAND_END || phase == Phase.MATCH_END) {
            if (players[seat].member.bot) return List.of();
            var actions = new ArrayList<RiichiAction>();
            if (manual && !players[seat].member.ready) actions.add(new RiichiAction(NEXT));
            actions.add(new RiichiAction(SKIP_SETTLEMENT));
            if (!wins.isEmpty() && age < ScoreAnnouncements.maximumTicks(wins)) actions.add(new RiichiAction(SETTLEMENT_DONE));
            return List.copyOf(actions);
        }
        if (phase == Phase.REACTION && replies[seat] >= 0) return List.of();
        return options.get(seat);
    }

    /** Reject stale, replayed, out-of-range, and out-of-turn requests without mutating state. */
    public boolean act(UUID actor, long expectedDecision, int actionIndex) {
        int seat = session.seatOf(actor);
        if (seat < 0 || expectedDecision != session.decision) return false;
        var legal = actions(seat);
        if (actionIndex < 0 || actionIndex >= legal.size()) return false;
        RiichiAction action = legal.get(actionIndex);
        if (ManualHandling.active(phase)) {
            handling.act(this, seat, action);
            return true;
        }
        if (phase == Phase.HAND_END || phase == Phase.MATCH_END) {
            if (action.type() == SKIP_SETTLEMENT) {
                if (skippedDecision == session.decision && (skippedSeats & (1 << seat)) != 0) return false;
                if (skippedDecision != session.decision) {
                    skippedDecision = session.decision;
                    skippedSeats = 0;
                }
                skippedSeats |= 1 << seat;
                if (settlementSkipped()) advanceSettlement();
                else session.revision++;
            }
            else if (action.type() == SETTLEMENT_DONE) {
                if (presentedDecision != session.decision) {
                    presentedDecision = session.decision;
                    presentedSeats = 0;
                }
                if ((presentedSeats & (1 << seat)) == 0) {
                    presentedSeats |= 1 << seat;
                    session.revision++;
                }
            }
            else {
                players[seat].member.ready = true;
                session.revision++;
            }
            return true;
        }
        if (recorder != null) recorder.decision(seat, legal, actionIndex);
        if (phase == Phase.REACTION) {
            // Revision changes are cosmetic here. Every responder keeps the SAME decision token.
            replies[seat] = actionIndex;
            if (action.type() != RON && legal.stream().anyMatch(a -> a.type() == RON)) {
                players[seat].temporaryFuriten = true;
                if (players[seat].riichi) players[seat].riichiFuriten = true;
            }
            session.revision++;
            if (reactionsReady()) resolveReactions();
            return true;
        }
        switch (action.type()) {
            case DISCARD, RIICHI -> discard(seat, action);
            case TSUMO -> RiichiSettlement.win(this, List.of(seat), -1, players[seat].drawn);
            case CLOSED_KAN, ADDED_KAN, NUKI -> {
                pending = action;
                lastTile = action.type() == CLOSED_KAN && action.tiles().contains(players[seat].drawn)
                    ? players[seat].drawn : action.tiles().get(0);
                lastFrom = seat;
                if (recorder != null) recorder.declare(this, seat, action);
                beginReactions();
            }
            case ABORT_NINE -> RiichiSettlement.abort(this, "nine_terminals");
            default -> throw new IllegalStateException("Invalid turn action");
        }
        return true;
    }

    private void finishSettlement() {
        if (phase == Phase.MATCH_END) {
            session.returnToLobby(true);
        } else {
            if (!dealerRepeats) { dealer = next(dealer); round++; }
            honba = drawResult || dealerRepeats ? honba + 1 : 0;
            startHand();
        }
    }

    private void advanceSettlement() {
        if (phase == Phase.MATCH_END && age < ScoreAnnouncements.maximumTicks(wins)) {
            beginFinalStandings();
        } else finishSettlement();
    }

    private void beginFinalStandings() {
        age = ScoreAnnouncements.maximumTicks(wins);
        for (RiichiPlayerState player : players) player.member.ready = false;
        session.decision++;
        session.revision++;
    }

    void startMatch() {
        initialDealer = dealer = 0;
        round = honba = riichiSticks = 0;
        for (RiichiPlayerState player : players) player.points = rules.startingPoints();
        long now = System.currentTimeMillis();
        replay = session.worldPolicy.replaysEnabled() ? new ReplayMatch(UUID.randomUUID(), tableId, now, now,
            Arrays.stream(players).limit(rules.players()).map(player -> new ReplayMatch.Participant(player.member.id, player.member.name, player.member.bot)).toList(),
            MahjongVariant.RIICHI, false, new RiichiReplay(rules, initialDealer, RedFives.of(suppliedTiles), List.of()), null, null) : null;
        startHand();
    }

    void startHand() {
        session.resetAutomation();
        for (RiichiPlayerState player : players) player.resetHand();
        handNumber++;
        recorder = null;
        uninterrupted = true;
        fourKanAbort = false;
        lastTile = Tile.ABSENT;
        lastFrom = -1;
        pending = null;
        wins.clear(); finalScores.clear(); finalUma.clear(); finalRanks.clear();
        Arrays.fill(reserveTicks, timeControl.reserveSeconds() * 20);
        exposed = new boolean[4];
        deltas = new ArrayList<>(Collections.nCopies(4, 0));
        result = "playing";
        if (manual) { handling.begin(this); return; }
        createWall();
        // Deal three groups of four tiles and then one tile to each player.
        for (int packet = 0; packet < 3; packet++) for (int offset = 0; offset < rules.players(); offset++) {
            for (int i = 0; i < 4; i++) players[(dealer + offset) % rules.players()].hand.add(wall.draw());
        }
        for (int offset = 0; offset < rules.players(); offset++) players[(dealer + offset) % rules.players()].hand.add(wall.draw());
        recorder = replay == null && !session.hasExternalBot() ? null : new ReplayRecorder(this);
        draw(dealer, false, false);
        // Give the initial wall/deal presentation time before a training opponent acts.
        // This is not an animation-driven game state: explicit legal actions still work.
        age = -DEAL_TICKS;
    }

    void createWall() {
        if (!equipped()) throw new IllegalStateException("Cannot deal without a physical set");
        wall = new Wall(rules, wallSeed(handNumber), dealer, suppliedTiles, !manual);
    }

    long wallSeed(int hand) { return seed + WALL_SEED_STEP * hand; }

    int next(int seat) { return (seat + 1) % rules.players(); }
    int wind(int seat) { return Math.floorMod(seat - dealer, rules.players()); }
    int kanCount() { return Arrays.stream(players).mapToInt(p -> (int) p.melds.stream().filter(Meld::quad).count()).sum(); }

    void newDecision(Phase nextPhase) {
        phase = nextPhase;
        age = 0;
        Arrays.fill(moveTicks, timeControl.moveSeconds() * 20);
        session.matchPhaseChanged(phase);
        Arrays.fill(replies, -1);
        for (int i = 0; i < 4; i++) options.set(i, List.of());
    }

    void draw(int seat, boolean replacement, boolean kan) {
        if ((!replacement && wall.remaining() == 0) || (replacement && !wall.canReplace())) {
            RiichiSettlement.exhaustive(this);
            return;
        }
        turn = seat;
        if (manual) {
            handling.replacement = replacement;
            handling.kan = kan;
            newDecision(Phase.DRAW);
            return;
        }
        drawNow(seat, replacement, kan);
    }

    void drawNow(int seat, boolean replacement, boolean kan) {
        turn = seat;
        RiichiPlayerState player = players[seat];
        if (rules.callsClearFuriten()) player.temporaryFuriten = false;
        player.drawn = replacement ? wall.replace() : wall.draw();
        player.hand.add(player.drawn);
        if (recorder != null) recorder.draw(seat, player.drawn);
        player.lastDraw = !replacement && wall.remaining() == 0;
        player.rinshan = kan;
        player.canDeclare = true;
        newDecision(Phase.TURN);
        options.set(seat, LegalActions.onTurn(this, seat));
    }

    private void discard(int seat, RiichiAction action) {
        RiichiPlayerState player = players[seat];
        int tile = action.tiles().get(0);
        boolean declare = action.type() == RIICHI;
        if (!player.hand.remove(Integer.valueOf(tile))) throw new IllegalStateException("Missing discarded tile");
        boolean sideways = declare || player.nextDiscardSideways;
        player.river.add(new RiichiDiscard(tile, sideways, false, tile == player.drawn));
        if (recorder != null) recorder.discard(seat, tile, tile == player.drawn, declare);
        player.nextDiscardSideways = false;
        player.pendingRiichi = declare;
        if (declare) player.doubleRiichi = player.firstTurn && uninterrupted;
        if (player.riichi) player.ippatsu = false;
        player.firstTurn = false;
        // A reverse-order call is not the player's next draw turn. Passing ron
        // and then calling pon must not clear temporary furiten prematurely.
        if (player.drawn >= 0) player.temporaryFuriten = false;
        player.forbiddenDiscards.clear();
        player.drawn = Tile.ABSENT;
        wall.revealPending();
        if (recorder != null) recorder.dora(this);
        lastTile = tile;
        lastFrom = seat;
        pending = null;
        beginReactions();
    }

    void beginReactions() {
        newDecision(Phase.REACTION);
        for (int i = 0; i < rules.players(); i++) if (i != lastFrom) {
            options.set(i, LegalActions.onReaction(this, i));
            if ((rules.yakulessFuriten() || rules.minHan() > 1) && (pending == null || pending.type() == ADDED_KAN
                || pending.type() == NUKI && rules.robNorthWithoutKokushi())
                && options.get(i).stream().noneMatch(action -> action.type() == RON)
                && RiichiHandAnalyzer.waits(players[i].hand, players[i].melds).contains(Tile.kind(lastTile))) {
                players[i].temporaryFuriten = true;
                if (players[i].riichi) players[i].riichiFuriten = true;
            }
        }
        // The built-in bot takes a legal ron before publishing call-only choices,
        // so a lower-priority call cannot hold up the settlement.
        for (int i = 0; i < rules.players(); i++) if (players[i].member.bot && players[i].member.externalBotId == null) {
            int ron = indexOf(options.get(i), RON);
            if (ron >= 0) {
                act(players[i].member.id, session.decision, ron);
                if (phase != Phase.REACTION) return;
            }
        }
        if (allReplied()) resolveReactions();
    }

    private boolean reactionsReady() {
        boolean ronChosen = false;
        for (int i = 0; i < rules.players(); i++) if (replies[i] >= 0
            && options.get(i).get(replies[i]).type() == RON) ronChosen = true;
        if (!ronChosen) return allReplied();
        for (int i = 0; i < rules.players(); i++) if (replies[i] < 0
            && indexOf(options.get(i), RON) >= 0) return false;
        return true;
    }

    private boolean allReplied() {
        for (int i = 0; i < rules.players(); i++) if (!options.get(i).isEmpty() && replies[i] < 0) return false;
        return true;
    }

    private void resolveReactions() {
        List<Integer> winners = new ArrayList<>();
        int caller = -1;
        RiichiAction call = null;
        for (int offset = 1; offset < rules.players(); offset++) {
            int seat = (lastFrom + offset) % rules.players();
            if (replies[seat] < 0) continue;
            RiichiAction choice = options.get(seat).get(replies[seat]);
            if (choice.type() == RON) winners.add(seat);
            if (choice.type() == CHI || choice.type() == PON || choice.type() == OPEN_KAN) {
                if (call == null || call.type() == CHI && choice.type() != CHI) { call = choice; caller = seat; }
            }
        }
        if (!winners.isEmpty()) {
            if (rules.tripleRonDraw() && winners.size() == 3) RiichiSettlement.abort(this, "triple_ron");
            else RiichiSettlement.win(this, rules.headBump() ? List.of(winners.get(0)) : winners, lastFrom, lastTile);
            return;
        }
        if (pending != null) { completeDeclaration(); return; }
        RiichiPlayerState source = players[lastFrom];
        if (source.pendingRiichi) {
            source.pendingRiichi = false;
            source.riichi = true;
            source.ippatsu = rules.ippatsu();
            source.points -= 1000;
            riichiSticks++;
            if (recorder != null) recorder.riichi(lastFrom);
        }
        if (rules.abortiveDraws()) {
            if (fourKanAbort) { RiichiSettlement.abort(this, "four_kans"); return; }
            if (!rules.sanma() && Arrays.stream(players).allMatch(p -> p.riichi)) {
                RiichiSettlement.abort(this, "four_riichi"); return;
            }
            if (!rules.sanma() && uninterrupted && Arrays.stream(players).allMatch(p -> p.river.size() == 1)) {
                int kind = Tile.kind(players[0].river.get(0).tile());
                if (kind >= Tile.EAST && kind <= Tile.NORTH && Arrays.stream(players)
                    .allMatch(p -> Tile.kind(p.river.get(0).tile()) == kind)) {
                    RiichiSettlement.abort(this, "four_winds"); return;
                }
            }
        }
        if (call != null) { completeCall(caller, call); return; }
        draw(next(lastFrom), false, false);
    }

    private void interrupt() {
        uninterrupted = false;
        for (RiichiPlayerState player : players) { player.ippatsu = false; player.firstTurn = false; }
    }

    private void completeCall(int seat, RiichiAction action) {
        RiichiPlayerState player = players[seat];
        if (rules.callsClearFuriten()) player.temporaryFuriten = false;
        RiichiPlayerState source = players[lastFrom];
        RiichiDiscard discarded = source.river.get(source.river.size() - 1);
        source.river.set(source.river.size() - 1, discarded.markCalled());
        if (discarded.riichi()) source.nextDiscardSideways = true;
        var tiles = new ArrayList<>(action.tiles());
        for (int tile : tiles) if (!player.hand.remove(Integer.valueOf(tile))) throw new IllegalStateException("Missing called tile");
        tiles.add(lastTile);
        tiles.sort(Tile.ORDER);
        Meld.Type type = switch (action.type()) {
            case CHI -> Meld.Type.SEQUENCE;
            case PON -> Meld.Type.TRIPLET;
            case OPEN_KAN -> Meld.Type.OPEN_QUAD;
            default -> throw new IllegalStateException("Not a call");
        };
        player.melds.add(new Meld(type, tiles, lastFrom, lastTile));
        if (recorder != null) recorder.call(seat, player.melds.get(player.melds.size() - 1));
        recordPao(seat, lastFrom, type == Meld.Type.OPEN_QUAD);
        interrupt();
        turn = seat;
        if (action.type() == OPEN_KAN) { completeKan(seat, false); return; }
        player.drawn = Tile.ABSENT;
        player.rinshan = player.lastDraw = player.canDeclare = false;
        player.forbiddenDiscards.addAll(LegalActions.forbiddenAfterCall(action, lastTile));
        newDecision(Phase.TURN);
        options.set(seat, LegalActions.onTurn(this, seat));
    }

    private void completeDeclaration() {
        int seat = lastFrom;
        RiichiPlayerState player = players[seat];
        RiichiAction action = pending;
        pending = null;
        if (recorder != null) recorder.confirmDeclaration();
        interrupt();
        if (action.type() == NUKI) {
            int tile = action.tiles().get(0);
            player.hand.remove(Integer.valueOf(tile));
            player.norths.add(tile);
            draw(seat, true, true);
            return;
        }
        if (action.type() == CLOSED_KAN) {
            player.hand.removeAll(action.tiles());
            player.melds.add(new Meld(Meld.Type.CONCEALED_QUAD, action.tiles(), seat, Tile.ABSENT));
        } else {
            int tile = action.tiles().get(0);
            player.hand.remove(Integer.valueOf(tile));
            for (int i = 0; i < player.melds.size(); i++) {
                Meld meld = player.melds.get(i);
                if (meld.type() == Meld.Type.TRIPLET && meld.kind() == Tile.kind(tile)) {
                    var tiles = new ArrayList<>(meld.tiles());
                    tiles.add(tile);
                    player.melds.set(i, new Meld(Meld.Type.ADDED_QUAD, tiles, meld.fromSeat(), meld.calledTile()));
                    break;
                }
            }
        }
        completeKan(seat, action.type() == CLOSED_KAN);
    }

    private void completeKan(int seat, boolean closed) {
        wall.revealPending();
        if (rules.kanDora()) {
            if (closed || !rules.delayedOpenKanDora()) wall.reveal();
            else wall.pendingIndicators++;
        }
        if (recorder != null) recorder.dora(this);
        fourKanAbort = rules.abortiveDraws() && kanCount() == 4 && Arrays.stream(players).filter(p -> p.melds.stream().anyMatch(Meld::quad)).count() > 1;
        draw(seat, true, true);
    }

    private void recordPao(int seat, int from, boolean openKan) {
        RiichiPlayerState player = players[seat];
        long dragons = player.melds.stream().filter(m -> m.kind() >= Tile.WHITE).count();
        long winds = player.melds.stream().filter(m -> m.kind() >= Tile.EAST && m.kind() <= Tile.NORTH).count();
        if (dragons == 3 && player.dragonPao < 0) player.dragonPao = from;
        if (winds == 4 && player.windPao < 0) player.windPao = from;
        if (openKan && rules.suukantsuPao() && player.melds.stream().filter(Meld::quad).count() == 4) player.kanPao = from;
    }

    /** Server-owned automation and timeouts, paced independently from client animations. */
    public void tick() {
        if (!session.tickRoom()) return;
        age++;
        if (age <= 0) return;
        if (phase == Phase.HAND_END || phase == Phase.MATCH_END) {
            int handTicks = ScoreAnnouncements.maximumTicks(wins);
            // Completion can shorten the fallback, but always leaves a server-timed reading tail.
            if (!wins.isEmpty() && age < handTicks - SETTLEMENT_TICKS && presentationComplete()) {
                age = handTicks - SETTLEMENT_TICKS;
                session.revision++;
            }
            if (age == handTicks) {
                if (phase == Phase.MATCH_END) beginFinalStandings();
                else finishSettlement();
            }
            else if (settlementTicks() == 0) advanceSettlement();
            else if (age % 20 == 0) session.revision++;
            return;
        }
        long token = session.decision;
        // Charge every eligible seat before processing any response, including bot responses.
        // Otherwise a bot acting first would grant all humans a free tick.
        for (int seat = 0; seat < rules.players(); seat++) if (clockActive(seat)) {
            var remaining = new TimeControl.Clock(moveTicks[seat], reserveTicks[seat], true).after(50);
            moveTicks[seat] = remaining.moveTicks();
            reserveTicks[seat] = remaining.reserveTicks();
        }
        if (age >= AUTO_ACTION_TICKS) {
            for (int seat = 0; seat < rules.players(); seat++) {
                RiichiPlayerState player = players[seat];
                if (player.member.id == null || player.member.bot) continue;
                var legal = actions(seat);
                int index;
                if (player.member.presence == PlayerPresence.DISCONNECTED) {
                    index = disconnectedAction(phase, player.drawn, legal);
                } else {
                    RiichiAutoPlay preference = manual ? RiichiAutoPlay.DEFAULT : player.autoPlay;
                    index = manual && phase == Phase.DRAW && player.riichi ? indexOf(legal, DRAW)
                        : preference.action(manual ? MatchAutomation.DEFAULT : session.automation.get(seat), phase, player.riichi, player.drawn, legal);
                }
                if (index >= 0) {
                    act(player.member.id, token, index);
                    return;
                }
            }
        }
        for (int seat = 0; seat < rules.players() && session.decision == token; seat++) {
            if (!clockActive(seat)) continue;
            if (moveTicks[seat] + reserveTicks[seat] > 0) continue;
            var legal = actions(seat);
            int index = indexOf(legal, phase == Phase.REACTION ? PASS : DISCARD);
            if (phase == Phase.TURN) for (int i = 0; i < legal.size(); i++) {
                if (legal.get(i).type() == DISCARD && legal.get(i).tiles().get(0) == players[seat].drawn) index = i;
            }
            if (index >= 0) act(players[seat].member.id, token, index);
        }
        if ((phase == Phase.TURN || phase == Phase.REACTION) && (age == 1 || age % 10 == 0)) session.revision++;
        if (age > 0 && age % 12 == 0) {
            for (int seat = 0; seat < rules.players(); seat++) if (players[seat].member.bot) {
                var legal = actions(seat);
                if (!legal.isEmpty()) {
                    if (players[seat].member.externalBotId != null) continue;
                    act(players[seat].member.id, session.decision,
                        TrainingBot.choose(view(players[seat].member.id), players[seat].member.botDifficulty));
                    return;
                }
            }
        }
    }
    private boolean clockActive(int seat) {
        return age >= 0 && session.hasSeatedHuman() && !players[seat].member.bot && players[seat].member.presence != PlayerPresence.DISCONNECTED
            && (phase == Phase.TURN || phase == Phase.REACTION)
            && !actions(seat).isEmpty();
    }

    static int disconnectedAction(Phase phase, int drawn, List<RiichiAction> legal) {
        int win = indexOf(legal, phase == Phase.REACTION ? RON : TSUMO);
        if (win >= 0) return win;
        if (phase == Phase.REACTION) return indexOf(legal, PASS);
        if (phase == Phase.TURN) {
            for (int i = 0; i < legal.size(); i++) {
                RiichiAction action = legal.get(i);
                if (action.type() == DISCARD && action.tiles().get(0) == drawn) return i;
            }
            int discard = indexOf(legal, DISCARD);
            if (discard >= 0) return discard;
        }
        return ManualHandling.active(phase) || phase == Phase.HAND_END || phase == Phase.MATCH_END
            ? legal.isEmpty() ? -1 : 0 : -1;
    }

    static int indexOf(List<RiichiAction> actions, RiichiAction.Type type) {
        for (int i = 0; i < actions.size(); i++) if (actions.get(i).type() == type) return i;
        return -1;
    }

    public RiichiView view(UUID authorizedViewer) {
        return view(session.seatOf(authorizedViewer), SpectatorHandVisibility.HIDDEN);
    }

    public RiichiView spectatorView(SpectatorHandVisibility visibility) {
        return view(-1, Objects.requireNonNull(visibility));
    }

    private RiichiView view(int viewer, SpectatorHandVisibility spectatorVisibility) {
        var seats = new ArrayList<RiichiView.Seat>();
        RiichiView.Focus focus = null;
        for (int seat = 0; seat < rules.players(); seat++) {
            RiichiPlayerState player = players[seat];
            boolean visible = openHands || seat == viewer || exposed[seat]
                || viewer >= 0 && playerHandVisibility.reveals(players[viewer].riichi)
                || viewer < 0 && spectatorVisibility.reveals(playerHandVisibility);
            List<Integer> hand = new ArrayList<>(player.hand);
            if (manual || player.autoPlay.sort()) hand.sort(Tile.ORDER);
            if (player.drawn >= 0 && (seat != viewer || manual || player.autoPlay.sort())
                && hand.remove(Integer.valueOf(player.drawn))) hand.add(player.drawn);
            if (phase == Phase.REACTION && seat == lastFrom)
                focus = new RiichiView.Focus(seat, lastTile, pending != null,
                    pending == null ? player.river.size() - 1 : hand.indexOf(lastTile));
            if (!visible) hand.replaceAll(tile -> Tile.HIDDEN);
            seats.add(new RiichiView.Seat(player.member.entityBot, player.member.name, player.member.id != null, player.member.bot, player.member.ready, player.points,
                hand, player.drawn < 0 ? Tile.ABSENT : visible ? player.drawn : Tile.HIDDEN,
                player.melds, player.river, player.norths, player.riichi, exposed[seat], player.doubleRiichi));
        }
        boolean ura = rules.uraDora() && wins.stream().anyMatch(win -> players[win.seat()].riichi);
        var clocks = new ArrayList<TimeControl.Clock>();
        for (int seat = 0; seat < rules.players(); seat++)
            clocks.add(new TimeControl.Clock(moveTicks[seat], reserveTicks[seat], clockActive(seat)));
        return new RiichiView(tableId, session.revision, session.decision, handNumber, rules, RiichiView.Phase.valueOf(phase.name()), viewer, dealer, round, honba, riichiSticks,
            turn, wall == null ? 0 : wall.remaining(), wall == null ? 0 : wall.breakOffset,
            wall == null ? List.of() : manual ? handling.wallView(this, ura) : wall.publicTiles(ura), focus, seats, actions(viewer), wins, result, deltas, finalScores, finalUma,
            timeControl, clocks, finalRanks, playerHandVisibility, openHands, session.exitVote, manual ? handling.view(this) : null,
            viewer < 0 || manual ? null : players[viewer].autoPlay,
            viewer >= 0 && (players[viewer].temporaryFuriten || players[viewer].riichiFuriten),
            viewer < 0 ? 0 : players[viewer].doubleRiichi || players[viewer].firstTurn && uninterrupted ? 2 : 1,
            recorder == null ? Map.of() : recorder.riichiSafeTiles(),
            session.externalBots(), settlementTicks(), settlementSkippedSeats());
    }

    /** Used on loading a saved table and by conservation tests, never as a network input. */
    public void validate() {
        session.validateRoom();
        if (players == null || players.length != 4 || Arrays.stream(players).anyMatch(Objects::isNull))
            throw new IllegalStateException("Invalid Riichi player state");
        rebindPlayers();
        if (session.capacity != rules.players() || session.variant != MahjongVariant.RIICHI)
            throw new IllegalStateException("Riichi rules do not match the room");
        Objects.requireNonNull(tableId); Objects.requireNonNull(rules); Objects.requireNonNull(phase);
        if (session.lifecycle != (phase == Phase.MATCH_END ? TableSession.Lifecycle.FINISHED : TableSession.Lifecycle.PLAYING))
            throw new IllegalStateException("Riichi phase does not match the room lifecycle");
        Objects.requireNonNull(session.seating).validate(rules.players());
        Objects.requireNonNull(timeControl); Objects.requireNonNull(finalRanks);
        Objects.requireNonNull(finalUma);
        Objects.requireNonNull(playerHandVisibility);
        Objects.requireNonNull(suppliedTiles); Objects.requireNonNull(handling);
        if (!suppliedTiles.isEmpty() && !Tile.validSet(suppliedTiles)) throw new IllegalStateException("Invalid physical set");
        handling.validate(this);
        if (moveTicks.length != 4 || reserveTicks.length != 4) throw new IllegalStateException("Invalid clocks");
        for (int seat = 0; seat < 4; seat++) {
            if (moveTicks[seat] < 0 || moveTicks[seat] > timeControl.moveSeconds() * 20
                || reserveTicks[seat] < 0 || reserveTicks[seat] > timeControl.reserveSeconds() * 20)
                throw new IllegalStateException("Invalid clock allowance");
        }
        if (players.length != 4 || options.size() != 4 || dealer < 0 || dealer >= rules.players()
            || round < 0 || round >= rules.scheduledRounds() + rules.players() || honba < 0 || riichiSticks < 0) {
            throw new IllegalStateException("Invalid saved table");
        }
        for (RiichiPlayerState player : players) {
            Objects.requireNonNull(player.autoPlay);
        }
        if (wall == null) return;
        Set<Integer> seen = new HashSet<>();
        for (int tile : wall.tiles) if (tile >= 0 && !seen.add(tile)) throw new IllegalStateException("Duplicated wall tile");
        for (int seat = 0; seat < rules.players(); seat++) {
            RiichiPlayerState player = players[seat];
            for (int tile : player.physicalTiles()) if (!seen.add(tile)) throw new IllegalStateException("Duplicated physical tile: " + tile);
        }
        var supplied = suppliedTiles;
        if (!seen.equals(new HashSet<>(supplied))) throw new IllegalStateException("Tile conservation failed");
        long points = riichiSticks * 1000L;
        for (int i = 0; i < rules.players(); i++) points += players[i].points;
        if (points != (long) rules.players() * rules.startingPoints()) throw new IllegalStateException("Point conservation failed");
    }

    void restored(long previous) {
        if (presentedDecision == previous) presentedDecision = session.decision;
        if (skippedDecision == previous) skippedDecision = session.decision;
    }
}
