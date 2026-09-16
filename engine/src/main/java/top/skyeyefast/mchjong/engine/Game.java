package top.skyeyefast.mchjong.engine;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static top.skyeyefast.mchjong.engine.Action.Type.*;

/** One server-owned table. All methods are called on the server thread. */
public final class Game {
    public static final int DEAL_TICKS = 56;
    public enum Phase { LOBBY, TURN, REACTION, HAND_END, MATCH_END }

    UUID tableId;
    RuleSet rules;
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
    TimeControl timeControl = TimeControl.DEFAULT;
    int[] moveTicks = new int[4];
    int[] reserveTicks = new int[4];

    public Game(UUID tableId, RuleSet rules, long seed) {
        this.tableId = Objects.requireNonNull(tableId);
        this.rules = Objects.requireNonNull(rules);
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
    public RuleSet rules() { return rules; }
    public boolean isHost(UUID player) { return seatOf(player) >= 0 && seatOf(player) == host(); }

    public boolean configureClock(UUID actor, TimeControl control) {
        if (phase != Phase.LOBBY || !isHost(actor)) return false;
        timeControl = Objects.requireNonNull(control);
        for (PlayerState player : players) player.ready = player.bot;
        newDecision(Phase.LOBBY);
        Arrays.fill(reserveTicks, control.reserveSeconds() * 20);
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
        if (existing >= 0) return existing == seat;
        if (phase != Phase.LOBBY || players[seat].id != null) return false;
        players[seat].id = player;
        players[seat].name = name.length() > 32 ? name.substring(0, 32) : name;
        revision++;
        return true;
    }

    public void leave(UUID player) {
        int seat = seatOf(player);
        // During a match the seat stays reserved for reconnection; timers safely pass/discard.
        if (seat >= 0 && phase == Phase.LOBBY) {
            players[seat] = new PlayerState();
            players[seat].points = rules.startingPoints();
            revision++;
        }
    }

    int host() {
        for (int i = 0; i < rules.players(); i++) if (players[i].id != null && !players[i].bot) return i;
        return -1;
    }

    List<Action> actions(int seat) {
        if (seat < 0 || seat >= rules.players() || players[seat].id == null) return List.of();
        if (phase == Phase.LOBBY) {
            var actions = new ArrayList<Action>();
            actions.add(new Action(READY));
            if (seat == host()) {
                actions.add(new Action(PRACTICE));
                for (RuleSet preset : RuleSet.values()) {
                    if (preset != rules && (preset.players() == 4 || players[3].id == null)) {
                        actions.add(new Action(CHANGE_RULE, preset.ordinal()));
                    }
                }
            }
            actions.add(new Action(LEAVE));
            return actions;
        }
        if (phase == Phase.HAND_END || phase == Phase.MATCH_END) {
            return players[seat].ready ? List.of() : List.of(new Action(NEXT));
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
        if (phase == Phase.LOBBY) {
            switch (action.type()) {
                case READY -> players[seat].ready = !players[seat].ready;
                case PRACTICE -> {
                    for (int i = 0; i < rules.players(); i++) if (players[i].id == null) {
                        players[i].id = UUID.nameUUIDFromBytes((tableId + ":bot:" + i).getBytes(StandardCharsets.UTF_8));
                        players[i].name = "Bot " + (i + 1);
                        players[i].bot = players[i].ready = true;
                    }
                    players[seat].ready = true;
                }
                case CHANGE_RULE -> {
                    rules = RuleSet.values()[action.tiles().getFirst()];
                    for (PlayerState player : players) {
                        player.points = rules.startingPoints();
                        player.ready = player.bot;
                    }
                }
                case LEAVE -> leave(actor);
                default -> throw new IllegalStateException("Invalid lobby action");
            }
            revision++;
            decision++;
            if (allReady()) startMatch();
            return true;
        }
        if (phase == Phase.HAND_END || phase == Phase.MATCH_END) {
            players[seat].ready = true;
            revision++;
            if (allReady()) {
                if (phase == Phase.MATCH_END) startMatch();
                else {
                    if (!dealerRepeats) { dealer = next(dealer); round++; }
                    honba = drawResult || dealerRepeats ? honba + 1 : 0;
                    startHand();
                }
            }
            return true;
        }
        if (phase == Phase.REACTION) {
            // Revision changes are cosmetic here. Every responder keeps the SAME decision token.
            replies[seat] = actionIndex;
            if (action.type() != RON && legal.stream().anyMatch(a -> a.type() == RON)) {
                players[seat].temporaryFuriten = true;
                if (players[seat].riichi) players[seat].riichiFuriten = true;
            }
            revision++;
            if (allReplied()) resolveReactions();
            return true;
        }
        switch (action.type()) {
            case DISCARD, RIICHI -> discard(seat, action);
            case TSUMO -> Settlement.win(this, List.of(seat), -1, players[seat].drawn);
            case CLOSED_KAN, ADDED_KAN, NUKI -> {
                pending = action;
                lastTile = action.type() == CLOSED_KAN && action.tiles().contains(players[seat].drawn)
                    ? players[seat].drawn : action.tiles().getFirst();
                lastFrom = seat;
                beginReactions();
            }
            case ABORT_NINE -> Settlement.abort(this, "nine_terminals");
            default -> throw new IllegalStateException("Invalid turn action");
        }
        return true;
    }

    private boolean allReady() {
        for (int i = 0; i < rules.players(); i++) if (players[i].id == null || !players[i].ready) return false;
        return true;
    }

    private void startMatch() {
        initialDealer = dealer = new Random(seed ^ handNumber).nextInt(rules.players());
        round = honba = riichiSticks = 0;
        for (PlayerState player : players) player.points = rules.startingPoints();
        startHand();
    }

    void startHand() {
        for (PlayerState player : players) player.resetHand();
        wall = new Wall(rules, seed + 0x9e3779b97f4a7c15L * ++handNumber);
        // Deal three groups of four tiles and then one tile to each player.
        for (int packet = 0; packet < 3; packet++) for (int offset = 0; offset < rules.players(); offset++) {
            for (int i = 0; i < 4; i++) players[(dealer + offset) % rules.players()].hand.add(wall.draw());
        }
        for (int offset = 0; offset < rules.players(); offset++) players[(dealer + offset) % rules.players()].hand.add(wall.draw());
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
        draw(dealer, false, false);
        // Give the initial wall/deal presentation time before a training opponent acts.
        // This is not an animation-driven game state: explicit legal actions still work.
        age = -DEAL_TICKS;
    }

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
        PlayerState player = players[seat];
        player.drawn = replacement ? wall.replace() : wall.draw();
        player.hand.add(player.drawn);
        player.lastDraw = !replacement && wall.remaining() == 0;
        player.rinshan = kan;
        player.canDeclare = true;
        newDecision(Phase.TURN);
        options.set(seat, LegalActions.onTurn(this, seat));
    }

    private void discard(int seat, Action action) {
        PlayerState player = players[seat];
        int tile = action.tiles().getFirst();
        boolean declare = action.type() == RIICHI;
        if (!player.hand.remove(Integer.valueOf(tile))) throw new IllegalStateException("Missing discarded tile");
        boolean sideways = declare || player.nextDiscardSideways;
        player.river.add(new Discard(tile, sideways, false, tile == player.drawn));
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
        lastTile = tile;
        lastFrom = seat;
        pending = null;
        beginReactions();
    }

    void beginReactions() {
        newDecision(Phase.REACTION);
        for (int i = 0; i < rules.players(); i++) if (i != lastFrom) {
            options.set(i, LegalActions.onReaction(this, i));
        }
        if (allReplied()) resolveReactions();
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
            else Settlement.win(this, rules.headBump() ? List.of(winners.getFirst()) : winners, lastFrom, lastTile);
            return;
        }
        if (pending != null) { completeDeclaration(); return; }
        PlayerState source = players[lastFrom];
        if (source.pendingRiichi) {
            source.pendingRiichi = false;
            source.riichi = source.ippatsu = true;
            source.points -= 1000;
            riichiSticks++;
        }
        if (rules.abortiveDraws()) {
            if (fourKanAbort) { Settlement.abort(this, "four_kans"); return; }
            if (!rules.sanma() && Arrays.stream(players).allMatch(p -> p.riichi)) {
                Settlement.abort(this, "four_riichi"); return;
            }
            if (!rules.sanma() && uninterrupted && Arrays.stream(players).allMatch(p -> p.river.size() == 1)) {
                int kind = Tile.kind(players[0].river.getFirst().tile());
                if (kind >= Tile.EAST && kind <= Tile.NORTH && Arrays.stream(players)
                    .allMatch(p -> Tile.kind(p.river.getFirst().tile()) == kind)) {
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
        PlayerState source = players[lastFrom];
        Discard discarded = source.river.getLast();
        source.river.set(source.river.size() - 1, discarded.markCalled());
        if (discarded.riichi()) source.nextDiscardSideways = true;
        var tiles = new ArrayList<>(action.tiles());
        for (int tile : tiles) if (!player.hand.remove(Integer.valueOf(tile))) throw new IllegalStateException("Missing called tile");
        tiles.add(lastTile);
        tiles.sort(Integer::compareTo);
        Meld.Type type = switch (action.type()) {
            case CHI -> Meld.Type.CHI;
            case PON -> Meld.Type.PON;
            case OPEN_KAN -> Meld.Type.OPEN_KAN;
            default -> throw new IllegalStateException("Not a call");
        };
        player.melds.add(new Meld(type, tiles, lastFrom, lastTile));
        recordPao(seat, lastFrom, type == Meld.Type.OPEN_KAN);
        interrupt();
        turn = seat;
        if (action.type() == OPEN_KAN) { completeKan(seat, false); return; }
        player.drawn = Tile.ABSENT;
        player.rinshan = player.lastDraw = player.canDeclare = false;
        player.forbiddenDiscards.add(Tile.kind(lastTile));
        if (action.type() == CHI) {
            int low = Tile.kind(tiles.getFirst());
            int called = Tile.kind(lastTile);
            if (called == low && low % 9 < 6) player.forbiddenDiscards.add(low + 3);
            if (called == low + 2 && low % 9 > 0) player.forbiddenDiscards.add(low - 1);
        }
        newDecision(Phase.TURN);
        options.set(seat, LegalActions.onTurn(this, seat));
    }

    private void completeDeclaration() {
        int seat = lastFrom;
        PlayerState player = players[seat];
        Action action = pending;
        pending = null;
        interrupt();
        if (action.type() == NUKI) {
            int tile = action.tiles().getFirst();
            player.hand.remove(Integer.valueOf(tile));
            player.norths.add(tile);
            draw(seat, true, true);
            return;
        }
        if (action.type() == CLOSED_KAN) {
            player.hand.removeAll(action.tiles());
            player.melds.add(new Meld(Meld.Type.CLOSED_KAN, action.tiles(), seat, Tile.ABSENT));
        } else {
            int tile = action.tiles().getFirst();
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
        if (closed || !rules.delayedOpenKanDora()) wall.reveal();
        else wall.pendingIndicators++;
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

    /** A slow, visible training opponent. Timeouts for human seats never claim a win automatically. */
    public void tick() {
        age++;
        if (age <= 0) return;
        long token = decision;
        // Charge every eligible seat before processing any response, including bot responses.
        // Otherwise a bot acting first would grant all humans a free tick.
        for (int seat = 0; seat < rules.players(); seat++) if (clockActive(seat)) {
            if (moveTicks[seat] > 0) moveTicks[seat]--;
            else if (reserveTicks[seat] > 0) reserveTicks[seat]--;
        }
        for (int seat = 0; seat < rules.players() && decision == token; seat++) {
            if (!clockActive(seat)) continue;
            if (moveTicks[seat] + reserveTicks[seat] > 0) continue;
            var legal = actions(seat);
            int index = indexOf(legal, phase == Phase.REACTION ? PASS : DISCARD);
            if (phase == Phase.TURN) for (int i = 0; i < legal.size(); i++) {
                if (legal.get(i).type() == DISCARD && legal.get(i).tiles().getFirst() == players[seat].drawn) index = i;
            }
            if (index >= 0) act(players[seat].id, token, index);
        }
        if ((phase == Phase.TURN || phase == Phase.REACTION) && (age == 1 || age % 10 == 0)) revision++;
        if (age > 0 && age % 12 == 0) {
            for (int seat = 0; seat < rules.players(); seat++) if (players[seat].bot) {
                var actions = actions(seat);
                if (!actions.isEmpty()) {
                    act(players[seat].id, decision, TrainingBot.choose(this, seat, actions));
                    return;
                }
            }
        }
    }

    private boolean clockActive(int seat) {
        return age >= 0 && !players[seat].bot && (phase == Phase.TURN || phase == Phase.REACTION)
            && !actions(seat).isEmpty();
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
            boolean visible = seat == viewer || exposed[seat];
            List<Integer> hand = new ArrayList<>(player.hand);
            hand.sort(Comparator.comparingInt(Tile::kind).thenComparingInt(Integer::intValue));
            if (player.drawn >= 0 && hand.remove(Integer.valueOf(player.drawn))) hand.add(player.drawn);
            if (phase == Phase.REACTION && seat == lastFrom)
                focus = new TableView.Focus(seat, lastTile, pending != null,
                    pending == null ? player.river.size() - 1 : hand.indexOf(lastTile));
            if (!visible) hand.replaceAll(tile -> Tile.HIDDEN);
            seats.add(new TableView.Seat(player.name, player.id != null, player.bot, player.ready, player.points,
                hand, player.drawn < 0 ? Tile.ABSENT : visible ? player.drawn : Tile.HIDDEN,
                player.melds, player.river, player.norths, player.riichi, exposed[seat]));
        }
        boolean ura = wins.stream().anyMatch(win -> players[win.seat()].riichi);
        var clocks = new ArrayList<TimeControl.Clock>();
        for (int seat = 0; seat < rules.players(); seat++)
            clocks.add(new TimeControl.Clock(moveTicks[seat], reserveTicks[seat], clockActive(seat)));
        return new TableView(tableId, revision, decision, handNumber, rules, phase, viewer, dealer, round, honba, riichiSticks,
            turn, wall == null ? 0 : wall.remaining(), wall == null ? 0 : wall.breakOffset,
            wall == null ? List.of() : wall.publicTiles(ura), focus, seats, actions(viewer), wins, result, deltas, finalScores,
            timeControl, clocks, finalRanks);
    }

    /** Used on loading a saved table and by conservation tests, never as a network input. */
    public void validate() {
        Objects.requireNonNull(tableId); Objects.requireNonNull(rules); Objects.requireNonNull(phase);
        Objects.requireNonNull(timeControl); Objects.requireNonNull(finalRanks);
        if (moveTicks.length != 4 || reserveTicks.length != 4) throw new IllegalStateException("Invalid clocks");
        for (int seat = 0; seat < 4; seat++) {
            if (moveTicks[seat] < 0 || moveTicks[seat] > timeControl.moveSeconds() * 20
                || reserveTicks[seat] < 0 || reserveTicks[seat] > timeControl.reserveSeconds() * 20)
                throw new IllegalStateException("Invalid clock allowance");
        }
        if (players.length != 4 || options.size() != 4 || dealer < 0 || dealer >= rules.players()
            || round < 0 || round >= 3 * rules.players() || honba < 0 || riichiSticks < 0) {
            throw new IllegalStateException("Invalid saved table");
        }
        Set<UUID> ids = new HashSet<>();
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
        if (!seen.equals(new HashSet<>(Tile.set(rules.sanma())))) throw new IllegalStateException("Tile conservation failed");
        long points = riichiSticks * 1000L;
        for (int i = 0; i < rules.players(); i++) points += players[i].points;
        if (points != (long) rules.players() * rules.startingPoints()) throw new IllegalStateException("Point conservation failed");
    }
}
