package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static top.skyeyefast.mchjong.engine.Action.Type.*;

/** One server-owned table. All methods are called on the server thread. */
public final class Game {
    private static final long WALL_SEED_STEP = 0x9e3779b97f4a7c15L;
    public static final int DEAL_TICKS = 56;
    public static final int AUTO_ACTION_TICKS = 12;
    public static final int AWAY_GRACE_TICKS = 5 * 20;
    public static final int SETTLEMENT_TICKS = 10 * 20;
    public enum Phase { LOBBY, SHUFFLE, BUILD_WALL, DEAL, DRAW, TURN, REACTION, HAND_END, MATCH_END }

    UUID tableId;
    RuleConfig rules;
    long seed;
    long revision = 1;
    long decision = 1;
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
    String result = "lobby";
    List<Integer> deltas = new ArrayList<>(Collections.nCopies(4, 0));
    List<Double> finalScores = new ArrayList<>();
    List<Integer> finalRanks = new ArrayList<>();
    ReplayMatch replay;
    ReplayRecorder recorder;
    List<ReplayMatch> archiveQueue = new ArrayList<>();
    TimeControl timeControl = TimeControl.DEFAULT;
    int[] moveTicks = new int[4];
    int[] reserveTicks = new int[4];
    UUID hostId;
    RoomSeating seating = new RoomSeating();
    HandVisibility handVisibility = HandVisibility.SELF;
    transient boolean invitationTeleport;
    ExitVote exitVote;
    long exitVoteSequence;
    int exitCooldown;
    boolean manual;
    ManualHandling handling = new ManualHandling();
    List<Integer> suppliedTiles;

    public Game(UUID tableId, RuleSet rules, long seed) {
        this(tableId, rules.config(), seed);
    }

    public Game(UUID tableId, RuleConfig rules, long seed) {
        this.tableId = Objects.requireNonNull(tableId);
        this.rules = Objects.requireNonNull(rules);
        suppliedTiles = Tile.set(rules.sanma(), rules.redFives());
        this.seed = seed;
        for (int i = 0; i < 4; i++) {
            players[i] = new PlayerState();
            players[i].points = rules.startingPoints();
            options.add(List.of());
        }
    }

    public UUID tableId() { return tableId; }
    public long revision() { return revision; }
    public Phase phase() { return phase; }
    public RuleConfig rules() { return rules; }
    public boolean manual() { return manual; }
    public int points(int seat) { return players[seat].points; }
    public boolean trainingSeat(int seat) { return seat >= 0 && seat < rules.players() && players[seat].bot; }
    public boolean entityBot(UUID id) { int seat = seatOf(id); return seat >= 0 && players[seat].entityBot; }

    /** Server integrations bind a physical companion to an empty place in its owner's room. */
    public boolean joinEntityBot(UUID owner, UUID id, String name, int seat) {
        if (id == null || name == null || seat < 0 || seat >= rules.players() || seatOf(id) >= 0
            || seatOf(owner) < 0 || players[seatOf(owner)].bot || phase != Phase.LOBBY
            || exitVote != null || players[seat].id != null) return false;
        setBot(seat, BotDifficulty.EASY);
        var bot = players[seat];
        bot.id = id;
        bot.name = name.substring(0, Math.min(32, name.length()));
        bot.entityBot = true;
        bot.presence = PlayerPresence.SEATED;
        for (var player : players) player.ready = player.bot;
        decision++;
        revision++;
        return true;
    }

    /** Leaving companions release lobby places; during play a training bot finishes their hand. */
    public void leaveEntityBot(UUID id) {
        int seat = seatOf(id);
        if (seat < 0 || !players[seat].entityBot) return;
        if (phase == Phase.LOBBY) removeMember(seat);
        else {
            var bot = players[seat];
            bot.entityBot = false;
            bot.id = UUID.randomUUID();
            bot.name = "Bot " + (seat + 1);
            bot.presence = PlayerPresence.SEATED;
            bot.awayTicks = 0;
            revision++;
        }
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
        for (PlayerState player : players) player.ready = false;
        newDecision(Phase.LOBBY);
        return true;
    }
    public List<ReplayMatch> pendingReplays() { return List.copyOf(archiveQueue); }
    public void acknowledgeReplay(UUID id) { archiveQueue.removeIf(match -> match.id().equals(id)); }

    void finishReplay() {
        if (recorder == null || replay == null) return;
        replay = replay.append(recorder.finish(this), phase == Phase.MATCH_END);
        archiveQueue.removeIf(match -> match.id().equals(replay.id()));
        archiveQueue.add(replay);
        recorder = null;
    }
    public boolean isHost(UUID player) { return seatOf(player) >= 0 && seatOf(player) == host(); }

    public boolean configureRules(UUID actor, long expectedDecision, RuleConfig config) {
        if (config == null || phase != Phase.LOBBY || exitVote != null || !isHost(actor)
            || expectedDecision != decision || rules.equals(config) || config.players() == 3 && players[3].id != null) return false;
        applyRules(config);
        newDecision(Phase.LOBBY);
        return true;
    }

    private void applyRules(RuleConfig config) {
        if (rules.players() != config.players()) seating = new RoomSeating();
        rules = config;
        handling = new ManualHandling();
        for (PlayerState player : players) {
            player.points = rules.startingPoints();
            player.ready = false;
        }
    }

    /** Called only by the server's world-policy adapter, never by a room control. */
    public void configureWorld(boolean invitationTeleport) {
        if (this.invitationTeleport == invitationTeleport) return;
        this.invitationTeleport = invitationTeleport;
        revision++;
    }

    public boolean configureHandVisibility(UUID actor, long expectedDecision, HandVisibility visibility) {
        if (visibility == null || visibility == handVisibility || phase != Phase.LOBBY || exitVote != null
            || !isHost(actor) || expectedDecision != decision) return false;
        handVisibility = visibility;
        for (PlayerState player : players) player.ready = false;
        newDecision(Phase.LOBBY);
        return true;
    }

    public RoomView roomView() {
        var seats = new ArrayList<RoomView.Seat>();
        for (int i = 0; i < rules.players(); i++) {
            var player = players[i];
            seats.add(new RoomView.Seat(player.id == null ? null : player.bot && !player.entityBot ? PlayerPresence.SEATED : player.presence,
                seating.winds[i], player.bot ? player.botDifficulty : null));
        }
        return new RoomView(host(), invitationTeleport, seating.stage, seating.available, seats, settlementTicks());
    }

    private int settlementTicks() {
        int duration = phase == Phase.MATCH_END ? SETTLEMENT_TICKS * 2 : phase == Phase.HAND_END ? SETTLEMENT_TICKS : 0;
        return Math.max(0, duration - Math.max(0, age));
    }

    /** Only the world adapter supplies actual mounts and live connections. Clients cannot confirm presence. */
    public void synchronizeSeats(Map<UUID, Integer> mounted, Set<UUID> connected) {
        boolean changed = false;
        for (int seat = 0; seat < rules.players(); seat++) {
            var player = players[seat];
            if (player.id == null || player.bot && !player.entityBot) continue;
            PlayerPresence previous = player.presence;
            if (player.entityBot) {
                if (Objects.equals(mounted.get(player.id), seat)) {
                    player.presence = PlayerPresence.SEATED;
                    player.awayTicks = 0;
                } else if (player.presence == PlayerPresence.SEATED) {
                    player.presence = PlayerPresence.AWAY;
                    player.awayTicks = AWAY_GRACE_TICKS;
                }
            } else if (!connected.contains(player.id)) {
                player.presence = PlayerPresence.DISCONNECTED;
                player.awayTicks = 0;
            } else if (Objects.equals(mounted.get(player.id), seat)) {
                player.presence = PlayerPresence.SEATED;
                player.awayTicks = 0;
            } else if (player.presence == PlayerPresence.SEATED) {
                player.presence = PlayerPresence.AWAY;
                player.awayTicks = AWAY_GRACE_TICKS;
            }
            if (player.presence != previous) {
                if (phase == Phase.LOBBY) player.ready = false;
                changed = true;
            }
        }
        if (changed) {
            revision++;
            if (phase == Phase.LOBBY) decision++;
        }
    }

    public boolean transferHost(UUID actor, UUID successor) {
        int seat = seatOf(successor);
        if (!isHost(actor) || actor.equals(successor) || seat < 0 || players[seat].bot || exitVote != null) return false;
        hostId = successor;
        decision++;
        revision++;
        return true;
    }

    public boolean requestExit(UUID actor) {
        int seat = seatOf(actor);
        if (seat < 0 || players[seat].bot || exitVote != null || phase == Phase.MATCH_END) return false;
        if (phase == Phase.LOBBY) {
            if (!isHost(actor)) return false;
            closeMatch();
            return true;
        }
        int humans = 0;
        for (int i = 0; i < rules.players(); i++) if (players[i].id != null && !players[i].bot) humans++;
        if (humans == 1) { closeMatch(); return true; }
        if (exitCooldown > 0) return false;
        exitVote = new ExitVote(++exitVoteSequence, seat, ExitVote.DURATION_TICKS, humans, List.of(seat));
        decision++;
        revision++;
        return true;
    }

    public boolean answerExit(UUID actor, long voteId, boolean agree) {
        int seat = seatOf(actor);
        if (seat < 0 || players[seat].bot || exitVote == null || exitVote.id() != voteId) return false;
        if (!agree) { cancelExit(); return true; }
        if (exitVote.agreed().contains(seat)) return false;
        var votes = new ArrayList<>(exitVote.agreed());
        votes.add(seat);
        if (votes.size() == exitVote.required()) closeMatch();
        else {
            exitVote = new ExitVote(voteId, exitVote.requester(), exitVote.ticksLeft(), exitVote.required(), votes);
            revision++;
        }
        return true;
    }

    private void cancelExit() {
        exitVote = null;
        exitCooldown = ExitVote.DURATION_TICKS;
        decision++;
        revision++;
    }

    private void closeMatch() {
        // Completed hands remain queued for archival; an unfinished hand is not a settlement.
        recorder = null;
        replay = null;
        wall = null;
        pending = null;
        exitVote = null;
        handling = new ManualHandling();
        exitCooldown = 0;
        hostId = null;
        seating = new RoomSeating();
        for (int i = 0; i < players.length; i++) {
            players[i] = new PlayerState();
            players[i].points = rules.startingPoints();
        }
        dealer = initialDealer = round = honba = riichiSticks = turn = 0;
        lastFrom = -1;
        lastTile = Tile.ABSENT;
        wins.clear(); finalScores.clear(); finalRanks.clear();
        exposed = new boolean[4];
        deltas = new ArrayList<>(Collections.nCopies(4, 0));
        result = "lobby";
        Arrays.fill(reserveTicks, timeControl.reserveSeconds() * 20);
        newDecision(Phase.LOBBY);
    }

    public boolean configureClock(UUID actor, TimeControl control) {
        if (phase != Phase.LOBBY || exitVote != null || !isHost(actor)) return false;
        timeControl = Objects.requireNonNull(control);
        for (PlayerState player : players) player.ready = player.bot;
        newDecision(Phase.LOBBY);
        Arrays.fill(reserveTicks, control.reserveSeconds() * 20);
        return true;
    }

    public boolean configureAutoPlay(UUID actor, long expectedDecision, AutoPlay.Option option, boolean enabled) {
        int seat = seatOf(actor);
        if (manual || seat < 0 || players[seat].bot || exitVote != null || expectedDecision != decision
            || option == AutoPlay.Option.KITA && !rules.sanma()
            || players[seat].autoPlay.enabled(option) == enabled) return false;
        players[seat].autoPlay = players[seat].autoPlay.with(option, enabled);
        // Other responders retain their decision token and remaining time.
        revision++;
        return true;
    }

    public int seatOf(UUID player) {
        if (player == null) return -1;
        for (int i = 0; i < rules.players(); i++) if (player.equals(players[i].id)) return i;
        return -1;
    }

    public boolean join(UUID player, String name, int seat) {
        if (player == null || seat < 0 || seat >= rules.players()) return false;
        int existing = seatOf(player);
        if (existing >= 0) {
            if (existing != seat) return false;
            if (players[seat].presence != PlayerPresence.SEATED) {
                players[seat].presence = PlayerPresence.SEATED;
                players[seat].awayTicks = 0;
                revision++;
                if (phase == Phase.LOBBY) decision++;
            }
            return true;
        }
        if (phase != Phase.LOBBY || exitVote != null || players[seat].id != null) return false;
        players[seat].id = player;
        players[seat].name = name.length() > 32 ? name.substring(0, 32) : name;
        players[seat].presence = PlayerPresence.SEATED;
        players[seat].awayTicks = 0;
        if (hostId == null) hostId = player;
        decision++;
        revision++;
        return true;
    }

    /** A lobby dismount leaves the room; active matches retain membership for reconnection. */
    public void unseat(UUID player) {
        int seat = seatOf(player);
        if (seat < 0 || players[seat].bot) return;
        if (phase == Phase.LOBBY) {
            if (exitVote != null) cancelExit();
            removeMember(seat);
            return;
        }
        if (players[seat].presence != PlayerPresence.SEATED) return;
        players[seat].presence = PlayerPresence.AWAY;
        players[seat].awayTicks = AWAY_GRACE_TICKS;
        revision++;
    }

    private void removeMember(int seat) {
        UUID player = players[seat].id;
        players[seat] = new PlayerState();
        players[seat].points = rules.startingPoints();
        if (player.equals(hostId)) {
            hostId = null;
            for (PlayerState remaining : players) if (remaining.id != null && !remaining.bot) {
                hostId = remaining.id;
                break;
            }
        }
        for (PlayerState remaining : players) remaining.ready = remaining.bot;
        if (hostId == null) closeMatch();
        decision++;
        revision++;
    }

    private void setBot(int seat, BotDifficulty difficulty) {
        var bot = players[seat];
        if (!bot.bot) {
            bot = players[seat] = new PlayerState();
            bot.id = UUID.randomUUID();
            bot.points = rules.startingPoints();
        }
        if (!bot.entityBot) bot.name = "Bot " + (seat + 1);
        bot.bot = bot.ready = true;
        bot.botDifficulty = difficulty;
    }

    private boolean fullRoom() {
        for (int i = 0; i < rules.players(); i++) if (players[i].id == null) return false;
        return true;
    }

    private void assignSeats() {
        PlayerState[] assigned = new PlayerState[4];
        for (int i = 0; i < rules.players(); i++) {
            var player = players[i];
            int destination = seating.winds[i];
            if ((!player.bot || player.entityBot) && destination != i) {
                player.presence = PlayerPresence.AWAY;
                player.awayTicks = AWAY_GRACE_TICKS;
            }
            player.ready = player.bot;
            if (player.bot && !player.entityBot) player.name = "Bot " + (destination + 1);
            assigned[destination] = player;
        }
        if (rules.players() == 3) assigned[3] = players[3];
        players = assigned;
        seating.positioned(rules.players());
    }

    int host() {
        return seatOf(hostId);
    }

    List<Action> actions(int seat) {
        if (seat < 0 || seat >= rules.players() || players[seat].id == null || exitVote != null) return List.of();
        if (phase == Phase.LOBBY) {
            var actions = new ArrayList<Action>();
            if (!players[seat].bot) actions.add(new Action(LEAVE_ROOM));
            if (seating.stage == RoomSeating.Stage.POSITIONING && (players[seat].bot || players[seat].presence == PlayerPresence.SEATED)
                && (!players[seat].bot || !players[seat].ready) && equipped())
                actions.add(new Action(READY));
            if (seating.stage == RoomSeating.Stage.DRAWING && seating.winds[seat] < 0)
                for (int tile = 0; tile < rules.players(); tile++) if ((seating.available & 1 << tile) != 0)
                    actions.add(new Action(DRAW_WIND, tile));
            if (seat == host()) {
                if (!fullRoom()) actions.add(new Action(FILL_BOTS));
                if (fullRoom() && seating.stage == RoomSeating.Stage.GATHERING) actions.add(new Action(BEGIN_SEATING));
                for (int target = 0; target < rules.players(); target++) {
                    var player = players[target];
                    if (target != seat && (player.id == null || player.bot || player.presence == PlayerPresence.DISCONNECTED)) {
                        for (var difficulty : BotDifficulty.values()) if (!player.bot || difficulty != player.botDifficulty)
                            actions.add(new Action(SET_BOT, List.of(target, difficulty.ordinal())));
                        if (player.bot) actions.add(new Action(REMOVE_BOT, target));
                    }
                    if (target != seat && player.id != null && !player.bot) actions.add(new Action(TRANSFER_HOST, target));
                }
                for (RuleSet preset : RuleSet.values()) {
                    if (!rules.withPreset(preset).equals(rules) && (preset.players() == 4 || players[3].id == null)) {
                        actions.add(new Action(CHANGE_RULE, preset.ordinal()));
                    }
                }
            }
            return actions;
        }
        if (ManualHandling.active(phase)) return handling.actions(this, seat);
        if (phase == Phase.HAND_END || phase == Phase.MATCH_END) {
            return manual && !players[seat].ready ? List.of(new Action(NEXT)) : List.of();
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
            switch (action.type()) {
                case READY -> players[seat].ready = !players[seat].ready;
                case FILL_BOTS -> {
                    for (int i = 0; i < rules.players(); i++) if (players[i].id == null) setBot(i, BotDifficulty.EASY);
                }
                case SET_BOT -> setBot(action.tiles().get(0), BotDifficulty.values()[action.tiles().get(1)]);
                case REMOVE_BOT -> {
                    int target = action.tiles().get(0);
                    players[target] = new PlayerState();
                    players[target].points = rules.startingPoints();
                }
                case TRANSFER_HOST -> transferHost(actor, players[action.tiles().get(0)].id);
                case LEAVE_ROOM -> removeMember(seat);
                case BEGIN_SEATING -> {
                    seating.begin(rules.players(), manual, seed ^ decision);
                    if (!manual) assignSeats();
                }
                case DRAW_WIND -> {
                    seating.draw(seat, action.tiles().get(0));
                    if (seating.complete(rules.players())) assignSeats();
                }
                case CHANGE_RULE -> applyRules(rules.withPreset(RuleSet.values()[action.tiles().get(0)]));
                default -> throw new IllegalStateException("Invalid lobby action");
            }
            if (action.type() == SET_BOT || action.type() == REMOVE_BOT || action.type() == FILL_BOTS)
                for (var player : players) player.ready = player.bot;
            revision++;
            decision++;
            if (allReady()) startMatch();
            return true;
        }
        if (phase == Phase.HAND_END || phase == Phase.MATCH_END) {
            players[seat].ready = true;
            revision++;
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
            var roster = players.clone();
            UUID host = hostId;
            closeMatch();
            players = roster;
            hostId = host;
            for (var player : players) { player.resetHand(); player.points = rules.startingPoints(); }
        } else {
            if (!dealerRepeats) { dealer = next(dealer); round++; }
            honba = drawResult || dealerRepeats ? honba + 1 : 0;
            startHand();
        }
    }

    private boolean allReady() {
        if (!equipped()) return false;
        if (phase == Phase.LOBBY && seating.stage != RoomSeating.Stage.POSITIONING) return false;
        for (int i = 0; i < rules.players(); i++) if (players[i].id == null || !players[i].ready) return false;
        if (phase == Phase.LOBBY) for (int i = 0; i < rules.players(); i++)
            if ((!players[i].bot || players[i].entityBot) && players[i].presence != PlayerPresence.SEATED) return false;
        return true;
    }

    private void startMatch() {
        initialDealer = dealer = 0;
        round = honba = riichiSticks = 0;
        for (PlayerState player : players) player.points = rules.startingPoints();
        long now = System.currentTimeMillis();
        replay = new ReplayMatch(UUID.randomUUID(), tableId, now, now, rules, initialDealer,
            Arrays.stream(players).limit(rules.players()).map(player -> new ReplayMatch.Participant(player.id, player.name, player.bot)).toList(),
            List.of(), false, RedFives.of(suppliedTiles));
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
        wins.clear(); finalScores.clear(); finalRanks.clear();
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
        recorder = replay == null ? null : new ReplayRecorder(this);
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
    int kanCount() { return Arrays.stream(players).mapToInt(p -> (int) p.melds.stream().filter(Meld::kan).count()).sum(); }

    void newDecision(Phase nextPhase) {
        phase = nextPhase;
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
                && HandAnalyzer.waits(players[i].hand, players[i].melds).contains(Tile.kind(lastTile))) {
                players[i].temporaryFuriten = true;
                if (players[i].riichi) players[i].riichiFuriten = true;
            }
        }
        // A bot always takes a legal ron. Record it before publishing call-only
        // choices so a lower-priority call cannot hold up the settlement.
        for (int i = 0; i < rules.players(); i++) if (players[i].bot) {
            int ron = indexOf(options.get(i), RON);
            if (ron >= 0) {
                act(players[i].id, decision, ron);
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
            case CHI -> Meld.Type.CHI;
            case PON -> Meld.Type.PON;
            case OPEN_KAN -> Meld.Type.OPEN_KAN;
            default -> throw new IllegalStateException("Not a call");
        };
        player.melds.add(new Meld(type, tiles, lastFrom, lastTile));
        if (recorder != null) recorder.call(seat, player.melds.get(player.melds.size() - 1));
        recordPao(seat, lastFrom, type == Meld.Type.OPEN_KAN);
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
            player.melds.add(new Meld(Meld.Type.CLOSED_KAN, action.tiles(), seat, Tile.ABSENT));
        } else {
            int tile = action.tiles().get(0);
            player.hand.remove(Integer.valueOf(tile));
            for (int i = 0; i < player.melds.size(); i++) {
                Meld meld = player.melds.get(i);
                if (meld.type() == Meld.Type.PON && meld.kind() == Tile.kind(tile)) {
                    var tiles = new ArrayList<>(meld.tiles());
                    tiles.add(tile);
                    player.melds.set(i, new Meld(Meld.Type.ADDED_KAN, tiles, meld.fromSeat(), meld.calledTile()));
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
        fourKanAbort = rules.abortiveDraws() && kanCount() == 4 && Arrays.stream(players).filter(p -> p.melds.stream().anyMatch(Meld::kan)).count() > 1;
        draw(seat, true, true);
    }

    private void recordPao(int seat, int from, boolean openKan) {
        PlayerState player = players[seat];
        long dragons = player.melds.stream().filter(m -> m.kind() >= Tile.WHITE).count();
        long winds = player.melds.stream().filter(m -> m.kind() >= Tile.EAST && m.kind() <= Tile.NORTH).count();
        if (dragons == 3 && player.dragonPao < 0) player.dragonPao = from;
        if (winds == 4 && player.windPao < 0) player.windPao = from;
        if (openKan && rules.suukantsuPao() && player.melds.stream().filter(Meld::kan).count() == 4) player.kanPao = from;
    }

    /** Server-owned automation and timeouts, paced independently from client animations. */
    public void tick() {
        tickPresence();
        if (exitCooldown > 0) exitCooldown--;
        if (exitVote != null) {
            int remaining = exitVote.ticksLeft() - 1;
            if (remaining == 0) cancelExit();
            else exitVote = new ExitVote(exitVote.id(), exitVote.requester(), remaining, exitVote.required(), exitVote.agreed());
            if (remaining % 20 == 0) revision++;
            return;
        }
        age++;
        if (age <= 0) return;
        if (phase == Phase.HAND_END || phase == Phase.MATCH_END) {
            if (settlementTicks() == 0) finishSettlement();
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
                if (player.id == null || player.bot) continue;
                var legal = actions(seat);
                int index;
                if (player.presence == PlayerPresence.DISCONNECTED) {
                    index = disconnectedAction(phase, player.drawn, legal);
                } else {
                    AutoPlay preference = manual ? AutoPlay.DEFAULT : player.autoPlay;
                    index = manual && phase == Phase.DRAW && player.riichi ? indexOf(legal, DRAW)
                        : preference.action(phase, player.riichi, player.drawn, legal);
                }
                if (index >= 0) {
                    act(player.id, token, index);
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
            if (index >= 0) act(players[seat].id, token, index);
        }
        if ((phase == Phase.TURN || phase == Phase.REACTION) && (age == 1 || age % 10 == 0)) revision++;
        if (age > 0 && age % 12 == 0) {
            if (phase == Phase.LOBBY && seating.stage == RoomSeating.Stage.DRAWING)
                for (int seat = 0; seat < rules.players(); seat++)
                    if (players[seat].id != null && !players[seat].bot && seating.winds[seat] < 0) return;
            for (int seat = 0; seat < rules.players(); seat++) if (players[seat].bot) {
                var actions = actions(seat);
                if (!actions.isEmpty()) {
                    act(players[seat].id, decision, TrainingBot.choose(view(players[seat].id), players[seat].botDifficulty));
                    return;
                }
            }
        }
    }

    private void tickPresence() {
        boolean changed = false;
        for (int seat = 0; seat < rules.players(); seat++) {
            PlayerState player = players[seat];
            if (player.id == null || player.bot && !player.entityBot || player.presence != PlayerPresence.AWAY) continue;
            if (--player.awayTicks > 0) continue;
            if (player.entityBot) {
                leaveEntityBot(player.id);
                continue;
            }
            player.awayTicks = 0;
            player.presence = PlayerPresence.DISCONNECTED;
            if (phase == Phase.LOBBY) player.ready = false;
            changed = true;
        }
        if (changed) {
            revision++;
            if (phase == Phase.LOBBY) decision++;
        }
        if (phase == Phase.LOBBY && hostId != null) {
            for (PlayerState player : players)
                if (player.id != null && !player.bot && player.presence != PlayerPresence.DISCONNECTED) return;
            closeMatch();
        }
    }

    private boolean clockActive(int seat) {
        return age >= 0 && !players[seat].bot && players[seat].presence != PlayerPresence.DISCONNECTED
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
        int viewer = seatOf(authorizedViewer);
        var seats = new ArrayList<TableView.Seat>();
        TableView.Focus focus = null;
        for (int seat = 0; seat < rules.players(); seat++) {
            PlayerState player = players[seat];
            boolean visible = seat == viewer || exposed[seat]
                || handVisibility.reveals(viewer >= 0 && players[viewer].riichi);
            List<Integer> hand = new ArrayList<>(player.hand);
            if (manual || player.autoPlay.sort()) hand.sort(Tile.ORDER);
            if (player.drawn >= 0 && hand.remove(Integer.valueOf(player.drawn))) hand.add(player.drawn);
            if (phase == Phase.REACTION && seat == lastFrom)
                focus = new TableView.Focus(seat, lastTile, pending != null,
                    pending == null ? player.river.size() - 1 : hand.indexOf(lastTile));
            if (!visible) hand.replaceAll(tile -> Tile.HIDDEN);
            seats.add(new TableView.Seat(player.entityBot, player.name, player.id != null, player.bot, player.ready, player.points,
                hand, player.drawn < 0 ? Tile.ABSENT : visible ? player.drawn : Tile.HIDDEN,
                player.melds, player.river, player.norths, player.riichi, exposed[seat]));
        }
        boolean ura = rules.uraDora() && wins.stream().anyMatch(win -> players[win.seat()].riichi);
        var clocks = new ArrayList<TimeControl.Clock>();
        for (int seat = 0; seat < rules.players(); seat++)
            clocks.add(new TimeControl.Clock(moveTicks[seat], reserveTicks[seat], clockActive(seat)));
        return new TableView(tableId, revision, decision, handNumber, rules, phase, viewer, dealer, round, honba, riichiSticks,
            turn, wall == null ? 0 : wall.remaining(), wall == null ? 0 : wall.breakOffset,
            wall == null ? List.of() : manual ? handling.wallView(this, ura) : wall.publicTiles(ura), focus, seats, actions(viewer), wins, result, deltas, finalScores,
            timeControl, clocks, finalRanks, handVisibility, exitVote, manual ? handling.view(this) : null,
            viewer < 0 || manual ? null : players[viewer].autoPlay,
            viewer >= 0 && (players[viewer].temporaryFuriten || players[viewer].riichiFuriten),
            viewer < 0 ? 0 : players[viewer].doubleRiichi || players[viewer].firstTurn && uninterrupted ? 2 : 1);
    }

    /** Used on loading a saved table and by conservation tests, never as a network input. */
    public void validate() {
        Objects.requireNonNull(tableId); Objects.requireNonNull(rules); Objects.requireNonNull(phase);
        Objects.requireNonNull(seating).validate(rules.players());
        Objects.requireNonNull(timeControl); Objects.requireNonNull(finalRanks);
        Objects.requireNonNull(archiveQueue);
        Objects.requireNonNull(handVisibility);
        Objects.requireNonNull(suppliedTiles); Objects.requireNonNull(handling);
        if (!suppliedTiles.isEmpty() && !Tile.validSet(suppliedTiles)) throw new IllegalStateException("Invalid physical set");
        handling.validate(this);
        if (exitCooldown < 0 || exitCooldown > ExitVote.DURATION_TICKS) throw new IllegalStateException("Invalid exit cooldown");
        if (exitVote != null && (exitVote.ticksLeft() < 1
            || exitVote.ticksLeft() > ExitVote.DURATION_TICKS || exitVote.required() < 2
            || exitVote.required() != Arrays.stream(players).limit(rules.players()).filter(p -> p.id != null && !p.bot).count()
            || !exitVote.agreed().contains(exitVote.requester()) || exitVote.agreed().size() >= exitVote.required()
            || new HashSet<>(exitVote.agreed()).size() != exitVote.agreed().size()
            || exitVote.agreed().stream().anyMatch(seat -> seat < 0 || seat >= rules.players()
                || players[seat].id == null || players[seat].bot))) throw new IllegalStateException("Invalid exit vote");
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
        Set<UUID> ids = new HashSet<>();
        for (PlayerState player : players) {
            Objects.requireNonNull(player.autoPlay);
            Objects.requireNonNull(player.botDifficulty);
            if (player.entityBot && (!player.bot || player.id == null)) throw new IllegalStateException("Invalid entity bot");
            if (player.presence == null) player.presence = PlayerPresence.SEATED;
            if (player.presence != PlayerPresence.AWAY) player.awayTicks = 0;
        }
        for (PlayerState player : players) if (player.id != null && !ids.add(player.id)) throw new IllegalStateException("Duplicate occupant");
        if (wall == null) return;
        Set<Integer> seen = new HashSet<>();
        for (int tile : wall.tiles) if (tile >= 0 && !seen.add(tile)) throw new IllegalStateException("Duplicated wall tile");
        for (int seat = 0; seat < rules.players(); seat++) {
            PlayerState player = players[seat];
            var physical = new ArrayList<>(player.hand);
            player.melds.forEach(meld -> physical.addAll(meld.tiles()));
            physical.addAll(player.norths);
            player.river.stream().filter(discard -> !discard.called()).forEach(discard -> physical.add(discard.tile()));
            for (int tile : physical) if (!seen.add(tile)) throw new IllegalStateException("Duplicated physical tile: " + tile);
        }
        var supplied = suppliedTiles;
        if (!seen.equals(new HashSet<>(supplied))) throw new IllegalStateException("Tile conservation failed");
        long points = riichiSticks * 1000L;
        for (int i = 0; i < rules.players(); i++) points += players[i].points;
        if (points != (long) rules.players() * rules.startingPoints()) throw new IllegalStateException("Point conservation failed");
    }
}
