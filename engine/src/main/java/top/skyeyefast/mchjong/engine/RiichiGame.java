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

import static top.skyeyefast.mchjong.engine.Action.Type.*;

/** One server-owned table. All methods are called on the server thread. */
public final class RiichiGame extends TableSession {
    private static final long WALL_SEED_STEP = 0x9e3779b97f4a7c15L;
    public static final int DEAL_TICKS = 56;
    public static final int AUTO_ACTION_TICKS = 12;
    public static final int SETTLEMENT_TICKS = 10 * 20;
    public enum Phase { LOBBY, SHUFFLE, BUILD_WALL, DEAL, DRAW, TURN, REACTION, HAND_END, MATCH_END }

    RiichiRules rules;
    int handNumber;
    int age;
    PlayerState[] players = new PlayerState[4];
    Phase phase = Phase.LOBBY;
    int dealer;
    int initialDealer;
    int round;
    int honba;
    int riichiSticks;
    int turn;
    Wall wall;
    int lastTile = Tile.ABSENT;
    int lastFrom = -1;
    Action pending;
    boolean uninterrupted = true;
    boolean fourKanAbort;
    boolean dealerRepeats;
    boolean drawResult;
    boolean abortResult;
    boolean[] exposed = new boolean[4];
    int[] replies = {-1, -1, -1, -1};
    List<List<Action>> options = new ArrayList<>();
    List<TableView.Win> wins = new ArrayList<>();
    private long presentedDecision = -1;
    private int presentedSeats;
    private long skippedDecision = -1;
    private int skippedSeats;
    String result = "lobby";
    List<Integer> deltas = new ArrayList<>(Collections.nCopies(4, 0));
    List<Double> finalScores = new ArrayList<>();
    List<Double> finalUma = new ArrayList<>();
    List<Integer> finalRanks = new ArrayList<>();
    Map<UUID, Integer> pendingExperience = new java.util.HashMap<>();
    ReplayMatch replay;
    ReplayRecorder recorder;
    List<ReplayMatch> archiveQueue = new ArrayList<>();
    TimeControl timeControl = TimeControl.DEFAULT;
    int[] moveTicks = new int[4];
    int[] reserveTicks = new int[4];
    PlayerHandVisibility playerHandVisibility = PlayerHandVisibility.SELF;
    boolean openHands;
    boolean convenienceHints;
    transient List<ExternalBot> externalBots = List.of();
    ManualHandling handling = new ManualHandling();
    List<Integer> suppliedTiles;
    public RiichiGame(UUID tableId, RiichiPreset rules, long seed) {
        this(tableId, rules.config(), seed);
    }

    public RiichiGame(UUID tableId, RiichiRules rules, long seed) {
        super(tableId, MahjongVariant.RIICHI, rules.players(), seed);
        this.rules = Objects.requireNonNull(rules);
        suppliedTiles = Tile.set(rules.sanma(), rules.redFives());
        this.seed = seed;
        for (int i = 0; i < 4; i++) {
            players[i] = new PlayerState(participants[i]);
            players[i].points = rules.startingPoints();
            options.add(List.of());
        }
    }

    public Phase phase() { return phase; }
    public RiichiRules rules() { return rules; }
    public int points(int seat) { return players[seat].points; }
    public void configureExternalBots(List<ExternalBot> bots) {
        var available = List.copyOf(bots);
        if (available.equals(externalBots)) return;
        externalBots = available;
        if (phase == Phase.LOBBY) { decision++; revision++; }
    }
    @Override protected List<ExternalBot> externalBots() { return externalBots == null ? List.of() : externalBots; }

    /** Snapshot one active bot choice without disclosing other players' draws. */
    public BotPosition botPosition(int seat, UUID sessionId) {
        if (recorder == null || manual || age <= 0 || exitVote != null
            || !hasSeatedHuman() || externalBotId(seat) == null
            || phase != Phase.TURN && phase != Phase.REACTION || actions(seat).isEmpty()) return null;
        PlayerState player = players[seat];
        var pons = player.melds.stream().filter(meld -> meld.type() == Meld.Type.TRIPLET)
            .map(meld -> new BotPosition.Pon("PON", meld.tiles())).toList();
        return new BotPosition(1, player.member.externalBotId, rules.preset(), tableId, sessionId,
            handNumber, seat, rules.players(), decision,
            recorder.botOpening(seat), recorder.botEvents(seat), actions(seat),
            phase == Phase.REACTION ? new BotPosition.Focus(lastFrom, lastTile) : null,
            player.drawn < 0 ? null : player.drawn, pons);
    }

    public boolean actBot(int seat, long expectedDecision, int actionIndex) {
        return externalBotId(seat) != null && act(players[seat].member.id, expectedDecision, actionIndex);
    }
    public boolean equipped() {
        return suppliedTiles.size() == (rules.sanma() ? 108 : 136) && rules.allows(RedFives.of(suppliedTiles));
    }

    /** The Minecraft adapter supplies checked physical tiles, or an empty list for an empty table. */
    public boolean configureEquipment(boolean manual, List<Integer> tiles) {
        Objects.requireNonNull(tiles);
        if (phase != Phase.LOBBY || exitVote != null) return false;
        if (!tiles.isEmpty() && (!Tile.validSet(tiles) || tiles.size() != (rules.sanma() ? 108 : 136)))
            throw new IllegalArgumentException("Equipment must contain one complete physical tile set");
        if (this.manual == manual && suppliedTiles.equals(tiles)) return true;
        if (this.manual != manual) {
            seating = new RoomSeating();
            timeControl = manual ? TimeControl.MANUAL : TimeControl.DEFAULT;
            Arrays.fill(reserveTicks, timeControl.reserveSeconds() * 20);
        }
        this.manual = manual;
        suppliedTiles = List.copyOf(tiles);
        handling = new ManualHandling();
        for (PlayerState player : players) player.member.ready = false;
        newDecision(Phase.LOBBY);
        return true;
    }
    public List<ReplayMatch> pendingReplays() { return List.copyOf(archiveQueue); }
    public void acknowledgeReplay(UUID id) { archiveQueue.removeIf(match -> match.id().equals(id)); }

    void finishReplay() {
        if (recorder == null) return;
        if (replay == null) { recorder = null; return; }
        var completed = recorder.finish(this);
        replay = replay.append(completed, phase == Phase.MATCH_END);
        archiveQueue.removeIf(match -> match.id().equals(replay.id()));
        archiveQueue.add(replay);
        recorder = null;
    }
    public boolean configureRules(UUID actor, long expectedDecision, RiichiRules config) {
        if (config == null || !worldPolicy.permitsRules(config) || phase != Phase.LOBBY || exitVote != null || !isHost(actor)
            || expectedDecision != decision || rules.equals(config) || config.players() == 3 && players[3].member.id != null) return false;
        applyRules(config);
        newDecision(Phase.LOBBY);
        return true;
    }

    /** The server's equipment adapter uses this when the selected red stock cannot be supplied. */
    public boolean configureStockRedFives(RedFives reds) {
        if (phase != Phase.LOBBY || exitVote != null || !rules.preset().allows(reds) || rules.redFives() == reds) return false;
        applyRules(rules.with(RiichiRuleOption.RED_FIVES, reds.ordinal()));
        newDecision(Phase.LOBBY);
        return true;
    }

    private void applyRules(RiichiRules config) {
        if (rules.players() != config.players()) seating = new RoomSeating();
        rules = config;
        capacity = config.players();
        handling = new ManualHandling();
        for (PlayerState player : players) {
            player.points = rules.startingPoints();
            player.member.ready = false;
        }
    }

    /** Called only by the server's world-policy adapter, never by a room control. */
    public void configureWorld(WorldPolicy policy) {
        Objects.requireNonNull(policy);
        WorldPolicy previous = worldPolicy;
        boolean changed = !policy.equals(previous);
        boolean decisionChanged = phase == Phase.LOBBY
            && (policy.allowBots() != previous.allowBots() || policy.forcedPreset() != previous.forcedPreset());
        worldPolicy = policy;
        if (!policy.allowConvenienceHints() && convenienceHints) {
            convenienceHints = false;
            changed = true;
        }
        if (!policy.allowExperienceRewards() && !pendingExperience.isEmpty()) {
            pendingExperience.clear();
            changed = true;
        }
        if (!policy.replaysEnabled() && (replay != null || recorder != null && !hasExternalBot() || !archiveQueue.isEmpty())) {
            replay = null;
            if (!hasExternalBot()) recorder = null;
            archiveQueue.clear();
            changed = true;
        }
        if (phase == Phase.LOBBY) {
            boolean rosterChanged = false;
            for (int seat = 0; seat < rules.players(); seat++) {
                PlayerState player = players[seat];
                if (player.member.bot && (!player.member.entityBot && !policy.allowBots()
                    || player.member.entityBot && !policy.allowCompanionPlayers())) {
                    participants[seat] = new Participant();
                    players[seat] = new PlayerState(participants[seat]);
                    players[seat].points = rules.startingPoints();
                    rosterChanged = true;
                }
            }
            if (rosterChanged) {
                seating = new RoomSeating();
                for (PlayerState player : players) player.member.ready = false;
                changed = true;
                decisionChanged = true;
            }
            RiichiRules constrained = rules;
            if (policy.forcedPreset() != null && rules.preset() != policy.forcedPreset()
                && (policy.forcedPreset().players() == rules.players()
                    || java.util.stream.IntStream.range(policy.forcedPreset().players(), rules.players())
                        .allMatch(seat -> players[seat].member.id == null)))
                constrained = rules.withPreset(policy.forcedPreset());
            if (!policy.allowCustomRules() && constrained.custom())
                constrained = constrained.withPreset(constrained.preset());
            if (!rules.equals(constrained)) {
                applyRules(constrained);
                changed = true;
                decisionChanged = true;
            }
        }
        if (decisionChanged) newDecision(Phase.LOBBY);
        else if (changed) revision++;
    }

    public boolean configureHandVisibility(UUID actor, long expectedDecision, PlayerHandVisibility visibility) {
        if (visibility == null || visibility == playerHandVisibility || phase != Phase.LOBBY || exitVote != null
            || !isHost(actor) || expectedDecision != decision) return false;
        playerHandVisibility = visibility;
        for (PlayerState player : players) player.member.ready = false;
        newDecision(Phase.LOBBY);
        return true;
    }

    public boolean configureOpenHands(UUID actor, long expectedDecision, boolean enabled) {
        if (openHands == enabled || phase != Phase.LOBBY || exitVote != null
            || !isHost(actor) || expectedDecision != decision) return false;
        openHands = enabled;
        for (PlayerState player : players) player.member.ready = false;
        newDecision(Phase.LOBBY);
        return true;
    }

    public boolean configureConvenienceHints(UUID actor, long expectedDecision, boolean enabled) {
        if (convenienceHints == enabled || enabled && !worldPolicy.allowConvenienceHints() || phase != Phase.LOBBY || exitVote != null
            || !isHost(actor) || expectedDecision != decision) return false;
        convenienceHints = enabled;
        revision++;
        return true;
    }

    public RoomView roomView() {
        var seats = new ArrayList<RoomView.Seat>();
        for (int i = 0; i < rules.players(); i++) {
            var player = players[i];
            seats.add(new RoomView.Seat(player.member.id == null ? null : player.member.bot && !player.member.entityBot ? PlayerPresence.SEATED : player.member.presence,
                seating.winds[i], player.member.bot ? player.member.botDifficulty : null, player.member.externalBotId));
        }
        return new RoomView(host(), convenienceHints, seating.stage, seating.available, seats, externalBots(), settlementTicks(),
            skippedDecision == decision ? skippedSeats : 0);
    }

    private int settlementTicks() {
        int duration = phase == Phase.MATCH_END ? ScoreAnnouncements.maximumTicks(wins) + SETTLEMENT_TICKS
            : phase == Phase.HAND_END ? ScoreAnnouncements.maximumTicks(wins) : 0;
        return Math.max(0, duration - Math.max(0, age));
    }

    private boolean presentationComplete() {
        if (wins.isEmpty()) return true;
        if (presentedDecision != decision) return false;
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

    @Override protected void clearMatch() {
        // Completed hands remain queued for archival; an unfinished hand is not a settlement.
        recorder = null;
        replay = null;
        wall = null;
        pending = null;
        handling = new ManualHandling();
        players = new PlayerState[4];
        rosterChanged();
        dealer = initialDealer = round = honba = riichiSticks = turn = 0;
        lastFrom = -1;
        lastTile = Tile.ABSENT;
        wins.clear(); finalScores.clear(); finalUma.clear(); finalRanks.clear();
        exposed = new boolean[4];
        deltas = new ArrayList<>(Collections.nCopies(4, 0));
        result = "lobby";
        Arrays.fill(reserveTicks, timeControl.reserveSeconds() * 20);
        newDecision(Phase.LOBBY);
    }

    public boolean configureClock(UUID actor, TimeControl control) {
        if (phase != Phase.LOBBY || exitVote != null || !isHost(actor)) return false;
        timeControl = Objects.requireNonNull(control);
        for (PlayerState player : players) player.member.ready = player.member.bot;
        newDecision(Phase.LOBBY);
        Arrays.fill(reserveTicks, control.reserveSeconds() * 20);
        return true;
    }

    public boolean configureAutoPlay(UUID actor, long expectedDecision, AutoPlay.Option option, boolean enabled) {
        int seat = seatOf(actor);
        if (manual || seat < 0 || players[seat].member.bot || exitVote != null || expectedDecision != decision
            || option == AutoPlay.Option.KITA && !rules.sanma()
            || players[seat].autoPlay.enabled(option) == enabled) return false;
        if (option == AutoPlay.Option.SORT && !enabled) {
            var player = players[seat];
            player.hand.sort(Tile.ORDER);
            if (player.drawn >= 0 && player.hand.remove(Integer.valueOf(player.drawn))) player.hand.add(player.drawn);
        }
        players[seat].autoPlay = players[seat].autoPlay.with(option, enabled);
        // Other responders retain their decision token and remaining time.
        revision++;
        return true;
    }

    public boolean reorderHand(UUID actor, long expectedDecision, int source, int target, boolean after) {
        int seat = seatOf(actor);
        if (seat < 0 || players[seat].member.bot || manual || players[seat].autoPlay.sort()
            || exitVote != null || expectedDecision != decision || source == target
            || !players[seat].hand.contains(source) || !players[seat].hand.contains(target)) return false;
        var hand = players[seat].hand;
        hand.remove(Integer.valueOf(source));
        hand.add(hand.indexOf(target) + (after ? 1 : 0), source);
        revision++;
        return true;
    }

    private boolean hasExternalBot() {
        for (int seat = 0; seat < rules.players(); seat++) if (externalBotId(seat) != null) return true;
        return false;
    }

    @Override protected void rosterChanged() {
        var previous = new java.util.IdentityHashMap<Participant, PlayerState>();
        if (players != null) for (PlayerState player : players)
            if (player != null && player.member != null) previous.put(player.member, player);
        players = new PlayerState[4];
        for (int seat = 0; seat < 4; seat++) {
            PlayerState player = previous.get(participants[seat]);
            if (player == null) {
                player = new PlayerState(participants[seat]);
                player.points = rules.startingPoints();
            }
            players[seat] = player;
        }
    }

    @Override protected boolean supportsExternalBot(ExternalBot bot) { return bot.supports(rules); }

    @Override protected boolean canStartMatch() {
        if (!worldPolicy.permitsRules(rules)) return false;
        for (int seat = 0; seat < capacity; seat++) {
            String botId = externalBotId(seat);
            if (botId != null && (manual || externalBots().stream()
                .noneMatch(bot -> bot.id().equals(botId) && bot.supports(rules)))) return false;
        }
        return true;
    }

    /** Only preparation controls cross this boundary; no MCR tile action is a Riichi Action. */
    private static Action riichiRoomAction(RoomAction action) {
        Action.Type type = switch (action.type()) {
            case READY -> READY;
            case LEAVE_ROOM -> LEAVE_ROOM;
            case BEGIN_SEATING -> BEGIN_SEATING;
            case DRAW_WIND -> DRAW_WIND;
            case FILL_BOTS -> FILL_BOTS;
            case SET_BOT -> SET_BOT;
            case REMOVE_BOT -> REMOVE_BOT;
            case TRANSFER_HOST -> TRANSFER_HOST;
            case RETURN_TO_LOBBY -> throw new IllegalStateException("Riichi returns after its settlement readout");
        };
        return new Action(type, action.arguments());
    }

    List<Action> actions(int seat) {
        if (seat < 0 || seat >= rules.players() || players[seat].member.id == null || exitVote != null) return List.of();
        if (phase == Phase.LOBBY) {
            var actions = new ArrayList<Action>();
            for (RoomAction action : roomActions(seat)) actions.add(riichiRoomAction(action));
            if (seat == host() && worldPolicy.forcedPreset() == null)
                for (RiichiPreset preset : RiichiPreset.values())
                    if (!rules.withPreset(preset).equals(rules) && (preset.players() == 4 || players[3].member.id == null))
                        actions.add(new Action(CHANGE_RULE, preset.ordinal()));
            return List.copyOf(actions);
        }
        if (ManualHandling.active(phase)) return handling.actions(this, seat);
        if (phase == Phase.HAND_END || phase == Phase.MATCH_END) {
            if (players[seat].member.bot) return List.of();
            var actions = new ArrayList<Action>();
            if (manual && !players[seat].member.ready) actions.add(new Action(NEXT));
            actions.add(new Action(SKIP_SETTLEMENT));
            if (!wins.isEmpty() && age < ScoreAnnouncements.maximumTicks(wins)) actions.add(new Action(SETTLEMENT_DONE));
            return List.copyOf(actions);
        }
        if (phase == Phase.REACTION && replies[seat] >= 0) return List.of();
        return options.get(seat);
    }

    /** Reject stale, replayed, out-of-range, and out-of-turn requests without mutating state. */
    public boolean act(UUID actor, long expectedDecision, int actionIndex) {
        int seat = seatOf(actor);
        if (seat < 0 || expectedDecision != decision) return false;
        var legal = actions(seat);
        if (actionIndex < 0 || actionIndex >= legal.size()) return false;
        Action action = legal.get(actionIndex);
        if (ManualHandling.active(phase)) {
            handling.act(this, seat, action);
            return true;
        }
        if (phase == Phase.LOBBY) {
            if (action.type() == CHANGE_RULE) {
                applyRules(rules.withPreset(RiichiPreset.values()[action.tiles().get(0)]));
                newDecision(Phase.LOBBY);
                return true;
            }
            for (RoomAction offered : roomActions(seat))
                if (riichiRoomAction(offered).equals(action)) return actRoom(actor, expectedDecision, offered);
            return false;
        }
        if (phase == Phase.HAND_END || phase == Phase.MATCH_END) {
            if (action.type() == SKIP_SETTLEMENT) {
                if (skippedDecision == decision && (skippedSeats & (1 << seat)) != 0) return false;
                if (skippedDecision != decision) {
                    skippedDecision = decision;
                    skippedSeats = 0;
                }
                skippedSeats |= 1 << seat;
                if (settlementSkipped()) advanceSettlement();
                else revision++;
            }
            else if (action.type() == SETTLEMENT_DONE) {
                if (presentedDecision != decision) {
                    presentedDecision = decision;
                    presentedSeats = 0;
                }
                if ((presentedSeats & (1 << seat)) == 0) {
                    presentedSeats |= 1 << seat;
                    revision++;
                }
            }
            else {
                players[seat].member.ready = true;
                revision++;
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
            revision++;
            if (reactionsReady()) resolveReactions();
            return true;
        }
        switch (action.type()) {
            case DISCARD, RIICHI -> discard(seat, action);
            case TSUMO -> Settlement.win(this, List.of(seat), -1, players[seat].drawn);
            case CLOSED_KAN, ADDED_KAN, NUKI -> {
                pending = action;
                lastTile = action.type() == CLOSED_KAN && action.tiles().contains(players[seat].drawn)
                    ? players[seat].drawn : action.tiles().get(0);
                lastFrom = seat;
                if (recorder != null) recorder.declare(this, seat, action);
                beginReactions();
            }
            case ABORT_NINE -> Settlement.abort(this, "nine_terminals");
            default -> throw new IllegalStateException("Invalid turn action");
        }
        return true;
    }

    private void finishSettlement() {
        if (phase == Phase.MATCH_END) {
            returnToLobby(true);
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
        for (PlayerState player : players) player.member.ready = false;
        decision++;
        revision++;
    }

    @Override protected void startMatch() {
        renewIncarnation();
        initialDealer = dealer = 0;
        round = honba = riichiSticks = 0;
        for (PlayerState player : players) player.points = rules.startingPoints();
        long now = System.currentTimeMillis();
        replay = worldPolicy.replaysEnabled() ? new ReplayMatch(UUID.randomUUID(), tableId, now, now, rules, initialDealer,
            Arrays.stream(players).limit(rules.players()).map(player -> new ReplayMatch.Participant(player.member.id, player.member.name, player.member.bot)).toList(),
            List.of(), false, RedFives.of(suppliedTiles)) : null;
        startHand();
    }

    void startHand() {
        for (PlayerState player : players) player.resetHand();
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
        recorder = replay == null && !hasExternalBot() ? null : new ReplayRecorder(this);
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
        lifecycle = phase == Phase.LOBBY ? Lifecycle.LOBBY : phase == Phase.MATCH_END ? Lifecycle.FINISHED : Lifecycle.PLAYING;
        age = 0;
        Arrays.fill(moveTicks, timeControl.moveSeconds() * 20);
        decision++;
        revision++;
        Arrays.fill(replies, -1);
        for (int i = 0; i < 4; i++) options.set(i, List.of());
    }

    void draw(int seat, boolean replacement, boolean kan) {
        if ((!replacement && wall.remaining() == 0) || (replacement && !wall.canReplace())) {
            Settlement.exhaustive(this);
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
        PlayerState player = players[seat];
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

    private void discard(int seat, Action action) {
        PlayerState player = players[seat];
        int tile = action.tiles().get(0);
        boolean declare = action.type() == RIICHI;
        if (!player.hand.remove(Integer.valueOf(tile))) throw new IllegalStateException("Missing discarded tile");
        boolean sideways = declare || player.nextDiscardSideways;
        player.river.add(new Discard(tile, sideways, false, tile == player.drawn));
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
                act(players[i].member.id, decision, ron);
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
        Action call = null;
        for (int offset = 1; offset < rules.players(); offset++) {
            int seat = (lastFrom + offset) % rules.players();
            if (replies[seat] < 0) continue;
            Action choice = options.get(seat).get(replies[seat]);
            if (choice.type() == RON) winners.add(seat);
            if (choice.type() == CHI || choice.type() == PON || choice.type() == OPEN_KAN) {
                if (call == null || call.type() == CHI && choice.type() != CHI) { call = choice; caller = seat; }
            }
        }
        if (!winners.isEmpty()) {
            if (rules.tripleRonDraw() && winners.size() == 3) Settlement.abort(this, "triple_ron");
            else Settlement.win(this, rules.headBump() ? List.of(winners.get(0)) : winners, lastFrom, lastTile);
            return;
        }
        if (pending != null) { completeDeclaration(); return; }
        PlayerState source = players[lastFrom];
        if (source.pendingRiichi) {
            source.pendingRiichi = false;
            source.riichi = true;
            source.ippatsu = rules.ippatsu();
            source.points -= 1000;
            riichiSticks++;
            if (recorder != null) recorder.riichi(lastFrom);
        }
        if (rules.abortiveDraws()) {
            if (fourKanAbort) { Settlement.abort(this, "four_kans"); return; }
            if (!rules.sanma() && Arrays.stream(players).allMatch(p -> p.riichi)) {
                Settlement.abort(this, "four_riichi"); return;
            }
            if (!rules.sanma() && uninterrupted && Arrays.stream(players).allMatch(p -> p.river.size() == 1)) {
                int kind = Tile.kind(players[0].river.get(0).tile());
                if (kind >= Tile.EAST && kind <= Tile.NORTH && Arrays.stream(players)
                    .allMatch(p -> Tile.kind(p.river.get(0).tile()) == kind)) {
                    Settlement.abort(this, "four_winds"); return;
                }
            }
        }
        if (call != null) { completeCall(caller, call); return; }
        draw(next(lastFrom), false, false);
    }

    private void interrupt() {
        uninterrupted = false;
        for (PlayerState player : players) { player.ippatsu = false; player.firstTurn = false; }
    }

    private void completeCall(int seat, Action action) {
        PlayerState player = players[seat];
        if (rules.callsClearFuriten()) player.temporaryFuriten = false;
        PlayerState source = players[lastFrom];
        Discard discarded = source.river.get(source.river.size() - 1);
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
        PlayerState player = players[seat];
        Action action = pending;
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
        PlayerState player = players[seat];
        long dragons = player.melds.stream().filter(m -> m.kind() >= Tile.WHITE).count();
        long winds = player.melds.stream().filter(m -> m.kind() >= Tile.EAST && m.kind() <= Tile.NORTH).count();
        if (dragons == 3 && player.dragonPao < 0) player.dragonPao = from;
        if (winds == 4 && player.windPao < 0) player.windPao = from;
        if (openKan && rules.suukantsuPao() && player.melds.stream().filter(Meld::quad).count() == 4) player.kanPao = from;
    }

    /** Server-owned automation and timeouts, paced independently from client animations. */
    public void tick() {
        if (!tickRoom()) return;
        age++;
        if (age <= 0) return;
        if (phase == Phase.HAND_END || phase == Phase.MATCH_END) {
            int handTicks = ScoreAnnouncements.maximumTicks(wins);
            // Completion can shorten the fallback, but always leaves a server-timed reading tail.
            if (!wins.isEmpty() && age < handTicks - SETTLEMENT_TICKS && presentationComplete()) {
                age = handTicks - SETTLEMENT_TICKS;
                revision++;
            }
            if (age == handTicks) {
                if (phase == Phase.MATCH_END) beginFinalStandings();
                else finishSettlement();
            }
            else if (settlementTicks() == 0) advanceSettlement();
            else if (age % 20 == 0) revision++;
            return;
        }
        long token = decision;
        // Charge every eligible seat before processing any response, including bot responses.
        // Otherwise a bot acting first would grant all humans a free tick.
        for (int seat = 0; seat < rules.players(); seat++) if (clockActive(seat)) {
            if (moveTicks[seat] > 0) moveTicks[seat]--;
            else if (reserveTicks[seat] > 0) reserveTicks[seat]--;
        }
        if (age >= AUTO_ACTION_TICKS) {
            for (int seat = 0; seat < rules.players(); seat++) {
                PlayerState player = players[seat];
                if (player.member.id == null || player.member.bot) continue;
                var legal = actions(seat);
                int index;
                if (player.member.presence == PlayerPresence.DISCONNECTED) {
                    index = disconnectedAction(phase, player.drawn, legal);
                } else {
                    AutoPlay preference = manual ? AutoPlay.DEFAULT : player.autoPlay;
                    index = manual && phase == Phase.DRAW && player.riichi ? indexOf(legal, DRAW)
                        : preference.action(phase, player.riichi, player.drawn, legal);
                }
                if (index >= 0) {
                    act(player.member.id, token, index);
                    return;
                }
            }
        }
        for (int seat = 0; seat < rules.players() && decision == token; seat++) {
            if (!clockActive(seat)) continue;
            if (moveTicks[seat] + reserveTicks[seat] > 0) continue;
            var legal = actions(seat);
            int index = indexOf(legal, phase == Phase.REACTION ? PASS : DISCARD);
            if (phase == Phase.TURN) for (int i = 0; i < legal.size(); i++) {
                if (legal.get(i).type() == DISCARD && legal.get(i).tiles().get(0) == players[seat].drawn) index = i;
            }
            if (index >= 0) act(players[seat].member.id, token, index);
        }
        if ((phase == Phase.TURN || phase == Phase.REACTION) && (age == 1 || age % 10 == 0)) revision++;
        if (age > 0 && age % 12 == 0) {
            if (phase == Phase.LOBBY && seating.stage == RoomSeating.Stage.DRAWING)
                for (int seat = 0; seat < rules.players(); seat++)
                    if (players[seat].member.id != null && !players[seat].member.bot && seating.winds[seat] < 0) return;
            for (int seat = 0; seat < rules.players(); seat++) if (players[seat].member.bot) {
                var actions = actions(seat);
                if (!actions.isEmpty()) {
                    if (players[seat].member.externalBotId != null) continue;
                    act(players[seat].member.id, decision, TrainingBot.choose(view(players[seat].member.id), players[seat].member.botDifficulty));
                    return;
                }
            }
        }
    }

    private boolean clockActive(int seat) {
        return age >= 0 && hasSeatedHuman() && !players[seat].member.bot && players[seat].member.presence != PlayerPresence.DISCONNECTED
            && (phase == Phase.TURN || phase == Phase.REACTION)
            && !actions(seat).isEmpty();
    }

    static int disconnectedAction(Phase phase, int drawn, List<Action> legal) {
        int win = indexOf(legal, phase == Phase.REACTION ? RON : TSUMO);
        if (win >= 0) return win;
        if (phase == Phase.REACTION) return indexOf(legal, PASS);
        if (phase == Phase.TURN) {
            for (int i = 0; i < legal.size(); i++) {
                Action action = legal.get(i);
                if (action.type() == DISCARD && action.tiles().get(0) == drawn) return i;
            }
            int discard = indexOf(legal, DISCARD);
            if (discard >= 0) return discard;
        }
        return ManualHandling.active(phase) || phase == Phase.HAND_END || phase == Phase.MATCH_END
            ? legal.isEmpty() ? -1 : 0 : -1;
    }

    static int indexOf(List<Action> actions, Action.Type type) {
        for (int i = 0; i < actions.size(); i++) if (actions.get(i).type() == type) return i;
        return -1;
    }

    public TableView view(UUID authorizedViewer) {
        return view(seatOf(authorizedViewer), SpectatorHandVisibility.HIDDEN);
    }

    public TableView spectatorView(SpectatorHandVisibility visibility) {
        return view(-1, Objects.requireNonNull(visibility));
    }

    private TableView view(int viewer, SpectatorHandVisibility spectatorVisibility) {
        var seats = new ArrayList<TableView.Seat>();
        TableView.Focus focus = null;
        for (int seat = 0; seat < rules.players(); seat++) {
            PlayerState player = players[seat];
            boolean visible = openHands || seat == viewer || exposed[seat]
                || viewer >= 0 && playerHandVisibility.reveals(players[viewer].riichi)
                || viewer < 0 && spectatorVisibility.reveals(playerHandVisibility);
            List<Integer> hand = new ArrayList<>(player.hand);
            if (manual || player.autoPlay.sort()) hand.sort(Tile.ORDER);
            if (player.drawn >= 0 && (seat != viewer || manual || player.autoPlay.sort())
                && hand.remove(Integer.valueOf(player.drawn))) hand.add(player.drawn);
            if (phase == Phase.REACTION && seat == lastFrom)
                focus = new TableView.Focus(seat, lastTile, pending != null,
                    pending == null ? player.river.size() - 1 : hand.indexOf(lastTile));
            if (!visible) hand.replaceAll(tile -> Tile.HIDDEN);
            seats.add(new TableView.Seat(player.member.entityBot, player.member.name, player.member.id != null, player.member.bot, player.member.ready, player.points,
                hand, player.drawn < 0 ? Tile.ABSENT : visible ? player.drawn : Tile.HIDDEN,
                player.melds, player.river, player.norths, player.riichi, exposed[seat], player.doubleRiichi));
        }
        boolean ura = rules.uraDora() && wins.stream().anyMatch(win -> players[win.seat()].riichi);
        var clocks = new ArrayList<TimeControl.Clock>();
        for (int seat = 0; seat < rules.players(); seat++)
            clocks.add(new TimeControl.Clock(moveTicks[seat], reserveTicks[seat], clockActive(seat)));
        return new TableView(tableId, revision, decision, handNumber, rules, phase, viewer, dealer, round, honba, riichiSticks,
            turn, wall == null ? 0 : wall.remaining(), wall == null ? 0 : wall.breakOffset,
            wall == null ? List.of() : manual ? handling.wallView(this, ura) : wall.publicTiles(ura), focus, seats, actions(viewer), wins, result, deltas, finalScores, finalUma,
            timeControl, clocks, finalRanks, playerHandVisibility, openHands, exitVote, manual ? handling.view(this) : null,
            viewer < 0 || manual ? null : players[viewer].autoPlay,
            viewer >= 0 && (players[viewer].temporaryFuriten || players[viewer].riichiFuriten),
            viewer < 0 ? 0 : players[viewer].doubleRiichi || players[viewer].firstTurn && uninterrupted ? 2 : 1,
            recorder == null ? Map.of() : recorder.riichiSafeTiles());
    }

    /** Rewards remain queued for disconnected participants until their player is online. */
    public Map<UUID, Integer> pendingExperience() { return Map.copyOf(pendingExperience); }
    public int takeExperience(UUID player) {
        Integer amount = pendingExperience.remove(player);
        return amount == null ? 0 : worldPolicy.limitExperienceChange(amount);
    }

    /** Used on loading a saved table and by conservation tests, never as a network input. */
    public void validate() {
        validateRoom();
        if (players == null || players.length != 4 || Arrays.stream(players).anyMatch(Objects::isNull))
            throw new IllegalStateException("Invalid Riichi player state");
        for (int seat = 0; seat < 4; seat++) players[seat].member = participants[seat];
        if (capacity != rules.players() || variant != MahjongVariant.RIICHI)
            throw new IllegalStateException("Riichi rules do not match the room");
        Objects.requireNonNull(tableId); Objects.requireNonNull(rules); Objects.requireNonNull(phase);
        if (lifecycle != (phase == Phase.LOBBY ? Lifecycle.LOBBY
            : phase == Phase.MATCH_END ? Lifecycle.FINISHED : Lifecycle.PLAYING))
            throw new IllegalStateException("Riichi phase does not match the room lifecycle");
        Objects.requireNonNull(seating).validate(rules.players());
        Objects.requireNonNull(timeControl); Objects.requireNonNull(finalRanks);
        Objects.requireNonNull(finalUma); Objects.requireNonNull(pendingExperience);
        Objects.requireNonNull(archiveQueue);
        Objects.requireNonNull(playerHandVisibility);
        if (worldPolicy == null) worldPolicy = WorldPolicy.DEFAULT;
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
        for (PlayerState player : players) {
            Objects.requireNonNull(player.autoPlay);
        }
        if (wall == null) return;
        Set<Integer> seen = new HashSet<>();
        for (int tile : wall.tiles) if (tile >= 0 && !seen.add(tile)) throw new IllegalStateException("Duplicated wall tile");
        for (int seat = 0; seat < rules.players(); seat++) {
            PlayerState player = players[seat];
            for (int tile : player.physicalTiles()) if (!seen.add(tile)) throw new IllegalStateException("Duplicated physical tile: " + tile);
        }
        var supplied = suppliedTiles;
        if (!seen.equals(new HashSet<>(supplied))) throw new IllegalStateException("Tile conservation failed");
        long points = riichiSticks * 1000L;
        for (int i = 0; i < rules.players(); i++) points += players[i].points;
        if (points != (long) rules.players() * rules.startingPoints()) throw new IllegalStateException("Point conservation failed");
    }

    @Override void restored() {
        long previous = decision;
        super.restored();
        if (presentedDecision == previous) presentedDecision = decision;
        if (skippedDecision == previous) skippedDecision = decision;
    }
}
