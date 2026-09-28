package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** A single-threaded, server-owned MCR match. Seats are fixed indices in turn order. */
public final class McrGame {
    public enum Phase { DRAW, TURN, REACTION, HAND_END, MATCH_END }

    private static final long WALL_SEED_STEP = 0x9e3779b97f4a7c15L;
    private final long seed;
    // Only the physical zones, drawn-tile alias and points are shared with Riichi.
    private final PlayerState[] players = {new PlayerState(), new PlayerState(), new PlayerState(), new PlayerState()};
    private final boolean[] winForbidden = new boolean[4];
    private final List<McrSettlement.Penalty> penalties = new ArrayList<>();
    private final Action[] replies = new Action[4];
    private List<List<Action>> choices;
    private McrWall wall;
    private Phase phase;
    private long decision;
    private int handIndex;
    private int turn;
    private int claimTile = Tile.ABSENT;
    private int claimFrom = -1;
    private Action pendingKong;
    private boolean drawWallLast;
    private McrWinContext.KongWin drawKong = McrWinContext.KongWin.NONE;
    private McrSettlement.Result result;

    public McrGame(long seed) { this(seed, new McrWall(seed)); }

    /** An ordered opening wall is private engine input for deterministic games and verification. */
    public McrGame(long seed, List<Integer> openingWall) { this(seed, new McrWall(openingWall)); }

    private McrGame(long seed, McrWall wall) {
        this.seed = seed;
        startHand(wall);
    }

    public Phase phase() { return phase; }
    public long decision() { return decision; }
    public int handNumber() { return handIndex + 1; }
    public int dealer() { return handIndex % 4; }
    public int turn() { return turn; }
    public int roundWind() { return Tile.EAST + handIndex / 4; }
    public int seatWind(int seat) { checkSeat(seat); return Tile.EAST + Math.floorMod(seat - dealer(), 4); }
    public int remaining() { return wall.remaining(); }
    public int points(int seat) { return player(seat).points; }
    public int drawn(int seat) { return player(seat).drawn; }
    public boolean winForbidden(int seat) { checkSeat(seat); return winForbidden[seat]; }
    public List<Integer> hand(int seat) { return List.copyOf(player(seat).hand); }
    public List<Meld> melds(int seat) { return List.copyOf(player(seat).melds); }
    public List<Discard> river(int seat) { return List.copyOf(player(seat).river); }
    public List<Integer> flowers(int seat) { return List.copyOf(player(seat).flowers); }
    public McrSettlement.Result result() { return result; }
    public List<McrSettlement.Penalty> penalties() { return List.copyOf(penalties); }

    /** Engine inspection is private, not a recipient-filtered network snapshot. */
    public List<Action> actions(int seat) {
        checkSeat(seat);
        return replies[seat] == null ? choices.get(seat) : List.of();
    }

    /** Accept only a current engine-issued action, once per seat in a response window. */
    public boolean act(int seat, long expectedDecision, int actionIndex) {
        if (seat < 0 || seat >= 4 || expectedDecision != decision || replies[seat] != null
            || actionIndex < 0 || actionIndex >= choices.get(seat).size()) return false;
        Action action = choices.get(seat).get(actionIndex);
        if (phase == Phase.REACTION) {
            replies[seat] = action;
            if (responsesComplete()) resolveReactions();
        } else {
            switch (action.type()) {
                case DRAW -> draw(false);
                case DISCARD -> discard(action.tiles().get(0));
                case TSUMO -> declareSelfDraw();
                case CLOSED_KAN -> concealedKong(action);
                case ADDED_KAN -> {
                    pendingKong = action;
                    claimTile = action.tiles().get(0);
                    claimFrom = seat;
                    reactions();
                }
                default -> throw new IllegalStateException("Unexpected MCR turn action: " + action.type());
            }
        }
        return true;
    }

    /** Hand advancement is an engine host operation, separate from player tile actions. */
    public boolean nextHand() {
        if (phase != Phase.HAND_END) return false;
        handIndex++;
        startHand(new McrWall(seed + WALL_SEED_STEP * handIndex));
        return true;
    }

    private void startHand(McrWall nextWall) {
        wall = Objects.requireNonNull(nextWall);
        for (var player : players) player.resetHand();
        Arrays.fill(winForbidden, false);
        result = null;
        pendingKong = null;
        claimTile = Tile.ABSENT;
        claimFrom = -1;
        drawKong = McrWinContext.KongWin.NONE;
        drawWallLast = false;
        turn = dealer();
        // Three four-tile packets per player, then the dealer takes the two upper tiles.
        // Raw flowers stay in the dealt packets until all 53 physical tiles are distributed.
        for (int packet = 0; packet < 3; packet++) for (int wind = 0; wind < 4; wind++)
            for (int tile = 0; tile < 4; tile++) players[(dealer() + wind) % 4].hand.add(wall.drawRaw());
        var east = players[dealer()];
        east.hand.add(wall.drawRaw());
        players[(dealer() + 1) % 4].hand.add(wall.drawRaw());
        east.drawn = wall.drawRaw();
        east.hand.add(east.drawn);
        players[(dealer() + 2) % 4].hand.add(wall.drawRaw());
        players[(dealer() + 3) % 4].hand.add(wall.drawRaw());
        // Finish each seat's complete replacement chain before moving East -> South -> West -> North.
        for (int wind = 0; wind < 4; wind++) {
            int seat = (dealer() + wind) % 4;
            var player = players[seat];
            for (int tile : List.copyOf(player.hand)) if (Tile.isFlower(tile)) {
                player.hand.remove(Integer.valueOf(tile));
                player.flowers.add(tile);
                int replacement = wall.replace(player);
                if (replacement == Tile.ABSENT) { finish(new McrSettlement.Draw()); return; }
                player.hand.add(replacement);
                if (seat == dealer()) player.drawn = replacement;
            }
            player.hand.sort(Tile.ORDER);
        }
        changePhase(Phase.TURN);
        validate();
    }

    private void draw(boolean afterKong) {
        var player = players[turn];
        player.drawn = Tile.ABSENT;
        int flowersBefore = player.flowers.size();
        int tile = afterKong ? wall.replace(player) : wall.draw(player);
        if (tile == Tile.ABSENT) { finish(new McrSettlement.Draw()); return; }
        player.hand.add(tile);
        player.hand.sort(Tile.ORDER);
        player.drawn = tile;
        drawWallLast = wall.remaining() == 0;
        // A flower starts its own supplementary chain, even when the first tail draw followed a kong.
        drawKong = afterKong && player.flowers.size() == flowersBefore
            ? McrWinContext.KongWin.REPLACEMENT : McrWinContext.KongWin.NONE;
        changePhase(Phase.TURN);
    }

    private void discard(int tile) {
        var player = players[turn];
        player.hand.remove(Integer.valueOf(tile));
        player.river.add(new Discard(tile, false, false, tile == player.drawn));
        player.drawn = Tile.ABSENT;
        claimTile = tile;
        claimFrom = turn;
        pendingKong = null;
        reactions();
    }

    private void reactions() {
        changePhase(Phase.REACTION);
        if (responsesComplete()) resolveReactions();
    }

    private boolean responsesComplete() {
        for (int seat = 0; seat < 4; seat++) if (!choices.get(seat).isEmpty() && replies[seat] == null) return false;
        return true;
    }

    private void resolveReactions() {
        McrSettlement.Win win = null;
        // Arrival order cannot select the winner. Every actual invalid declaration is penalized;
        // among qualifying declarations only the nearest seat after the supplier wins.
        for (int distance = 1; distance < 4; distance++) {
            int seat = (claimFrom + distance) % 4;
            if (replies[seat] == null || replies[seat].type() != Action.Type.RON) continue;
            var score = Objects.requireNonNull(score(seat));
            if (!score.meetsMinimum()) wrongWin(seat, claimTile);
            else if (win == null) win = McrSettlement.win(seat, claimFrom, claimTile, winningContext(seat), score);
        }
        if (win != null) {
            var supplier = players[claimFrom];
            if (pendingKong != null) {
                // The added tile stays with its owner until the robbing window closes.
                supplier.hand.remove(Integer.valueOf(claimTile));
            } else {
                int index = supplier.river.size() - 1;
                supplier.river.set(index, supplier.river.get(index).markCalled());
            }
            // The winner owns the one physical tile; result/history entries only reference it.
            // Robbing a kong never invents a discard in the supplier's river.
            players[win.winner()].hand.add(claimTile);
            players[win.winner()].hand.sort(Tile.ORDER);
            supplier.drawn = Tile.ABSENT;
            pendingKong = null;
            finish(win);
            return;
        }
        if (pendingKong != null) { completeAddedKong(); return; }
        int selected = -1, priority = 0;
        for (int distance = 1; distance < 4; distance++) {
            int seat = (claimFrom + distance) % 4;
            int candidate = replies[seat] == null ? 0 : switch (replies[seat].type()) {
                case PON, OPEN_KAN -> 2;
                case CHI -> 1;
                default -> 0;
            };
            if (candidate > priority) { selected = seat; priority = candidate; }
        }
        if (selected >= 0) { claim(selected, replies[selected]); return; }
        turn = (claimFrom + 1) % 4;
        if (wall.remaining() == 0) finish(new McrSettlement.Draw());
        else changePhase(Phase.DRAW);
    }

    private void claim(int seat, Action action) {
        var player = players[seat];
        var tiles = new ArrayList<>(action.tiles());
        player.hand.removeAll(tiles);
        tiles.add(claimTile);
        tiles.sort(Tile.ORDER);
        Meld.Type type = switch (action.type()) {
            case CHI -> Meld.Type.CHI;
            case PON -> Meld.Type.PON;
            case OPEN_KAN -> Meld.Type.OPEN_KAN;
            default -> throw new IllegalStateException("Not a meld claim");
        };
        player.melds.add(new Meld(type, tiles, claimFrom, claimTile));
        var river = players[claimFrom].river;
        river.set(river.size() - 1, river.get(river.size() - 1).markCalled());
        player.drawn = Tile.ABSENT;
        turn = seat;
        drawWallLast = false;
        drawKong = McrWinContext.KongWin.NONE;
        if (type == Meld.Type.OPEN_KAN) draw(true);
        else changePhase(Phase.TURN);
    }

    private void concealedKong(Action action) {
        var player = players[turn];
        player.hand.removeAll(action.tiles());
        player.melds.add(new Meld(Meld.Type.CLOSED_KAN, action.tiles(), turn, Tile.ABSENT));
        draw(true);
    }

    private void completeAddedKong() {
        var player = players[turn];
        int kind = Tile.kind(claimTile);
        for (int i = 0; i < player.melds.size(); i++) {
            var pung = player.melds.get(i);
            if (pung.type() != Meld.Type.PON || pung.kind() != kind) continue;
            var tiles = new ArrayList<>(pung.tiles());
            tiles.add(claimTile);
            tiles.sort(Tile.ORDER);
            player.hand.remove(Integer.valueOf(claimTile));
            player.melds.set(i, new Meld(Meld.Type.ADDED_KAN, tiles, pung.fromSeat(), pung.calledTile()));
            pendingKong = null;
            draw(true);
            return;
        }
        throw new IllegalStateException("Added kong lost its original pung");
    }

    private void declareSelfDraw() {
        var score = Objects.requireNonNull(score(turn));
        int tile = players[turn].drawn;
        if (score.meetsMinimum()) finish(McrSettlement.win(turn, -1, tile, winningContext(turn), score));
        else {
            wrongWin(turn, tile);
            changePhase(Phase.TURN);
        }
    }

    private void wrongWin(int seat, int tile) {
        var penalty = McrSettlement.wrongWin(handNumber(), seat, tile);
        apply(penalty.deltas());
        penalties.add(penalty);
        winForbidden[seat] = true;
    }

    private void finish(McrSettlement.Result outcome) {
        result = outcome;
        apply(outcome.deltas());
        changePhase(handIndex == 15 ? Phase.MATCH_END : Phase.HAND_END);
    }

    private void apply(List<Integer> deltas) {
        for (int seat = 0; seat < 4; seat++) players[seat].points = Math.addExact(players[seat].points, deltas.get(seat));
    }

    private void changePhase(Phase next) {
        phase = next;
        decision++;
        Arrays.fill(replies, null);
        var actions = new ArrayList<List<Action>>(4);
        for (int seat = 0; seat < 4; seat++) actions.add(McrLegalActions.forSeat(this, seat));
        choices = List.copyOf(actions);
    }

    /** Facts are derived here, never supplied with a player's declaration. */
    public McrWinContext winningContext(int seat) {
        checkSeat(seat);
        boolean self = phase == Phase.TURN && seat == turn && players[seat].drawn >= 0;
        boolean discard = phase == Phase.REACTION && seat != claimFrom;
        if (!self && !discard) throw new IllegalStateException("No winning-tile source for this seat");
        int tile = self ? players[seat].drawn : claimTile;
        var kong = self ? drawKong : pendingKong != null ? McrWinContext.KongWin.ROBBED : McrWinContext.KongWin.NONE;
        return new McrWinContext(self ? McrWinContext.Method.SELF_DRAW : McrWinContext.Method.DISCARD,
            seatWind(seat), roundWind(), drawWallLast && (self || pendingKong == null), kong,
            lastCopy(tile), players[seat].flowers.size());
    }

    private boolean lastCopy(int winningTile) {
        var visible = new HashSet<Integer>();
        for (var player : players) {
            // Called discards alias exposed melds; concealed tiles cannot establish this fan.
            player.river.forEach(discard -> visible.add(discard.tile()));
            player.melds.stream().filter(meld -> !meld.closed()).forEach(meld -> visible.addAll(meld.tiles()));
        }
        visible.remove(winningTile);
        int kind = Tile.kind(winningTile);
        return visible.stream().filter(tile -> Tile.kind(tile) == kind).count() == 3;
    }

    McrHandScore score(int seat) {
        if (winForbidden[seat]) return null;
        var player = players[seat];
        if (phase == Phase.TURN && seat == turn && player.drawn >= 0) {
            var concealed = new ArrayList<>(player.hand);
            concealed.remove(Integer.valueOf(player.drawn));
            return McrHandAnalyzer.score(concealed, player.melds, seat, player.drawn, winningContext(seat));
        }
        if (phase == Phase.REACTION && seat != claimFrom)
            return McrHandAnalyzer.score(player.hand, player.melds, seat, claimTile, winningContext(seat));
        return null;
    }

    PlayerState player(int seat) { checkSeat(seat); return players[seat]; }
    int claimFrom() { return claimFrom; }
    int claimTile() { return claimTile; }
    boolean robbingKong() { return pendingKong != null; }

    /** Private integrity check, including all 144 physical identities and zero-sum points. */
    public void validate() {
        wall.assertConservation(players);
        if (Arrays.stream(players).mapToLong(player -> player.points).sum() != 0)
            throw new IllegalStateException("MCR point conservation failed");
        for (int seat = 0; seat < 4; seat++) {
            var player = players[seat];
            boolean extra = phase == Phase.TURN && seat == turn
                || phase == Phase.REACTION && pendingKong != null && seat == claimFrom
                || result instanceof McrSettlement.Win win && win.winner() == seat;
            if (player.hand.size() + 3 * player.melds.size() != (extra ? 14 : 13))
                throw new IllegalStateException("Invalid MCR concealed hand size at seat " + seat);
            if (player.drawn != Tile.ABSENT && !player.hand.contains(player.drawn))
                throw new IllegalStateException("Drawn tile is not owned by its player");
        }
    }

    private static void checkSeat(int seat) {
        if (seat < 0 || seat >= 4) throw new IllegalArgumentException("MCR seats must be in 0..3");
    }
}
