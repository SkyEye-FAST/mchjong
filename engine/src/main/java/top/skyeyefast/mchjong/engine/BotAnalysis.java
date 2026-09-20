package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Decision-local shape cache and bounded draw/discard search. All draws are face categories. */
final class BotAnalysis {
    static final int SEARCH_ROOTS = 3;
    private final TableView view;
    private final BotDifficulty level;
    final BotValue value;
    final int[] unseen = new int[68];
    private final Map<ShapeKey, Map<Integer, TileEfficiency>> discards = new HashMap<>();
    private final Map<ShapeKey, TileEfficiency> hands = new HashMap<>();
    private final Map<ShapeKey, Set<Integer>> waits = new HashMap<>();
    int drawNodes;

    record State(List<Integer> hand, List<Meld> melds, List<Integer> norths, long river, boolean riichi) {
        State { hand = List.copyOf(hand); melds = List.copyOf(melds); norths = List.copyOf(norths); }
        State discard(int tile, boolean declare) {
            var next = new ArrayList<>(hand);
            next.remove(Integer.valueOf(tile));
            return new State(next, melds, norths, river | 1L << Tile.kind(tile), riichi || declare);
        }
        State draw(int tile) {
            var next = new ArrayList<>(hand); next.add(tile);
            return new State(next, melds, norths, river, riichi);
        }
    }
    record Evaluation(int shanten, int live, int good, double points, double utility, BotValue.Waits waits) {}
    private record ShapeKey(List<Integer> hand, List<String> melds) {
        ShapeKey(State s) { this(s.hand.stream().map(Tile::kind).sorted().toList(),
            s.melds.stream().map(Meld::libraryNotation).sorted().toList()); }
    }

    BotAnalysis(TableView view, BotDifficulty level) {
        this.view = view; this.level = level; value = new BotValue(view);
        // The playing composition, not equipment stock or physical-copy identities.
        for (int tile : Tile.set(view.rules().sanma(), view.rules().redFives())) unseen[face(tile)]++;
        for (int tile : VisibleTiles.tiles(view)) unseen[face(tile)]--;
        for (int i = 0; i < unseen.length; i++) unseen[i] = Math.max(0, unseen[i]);
    }
    static int face(int tile) { return Tile.kind(tile) + (Tile.red(tile) ? 34 : 0); }
    static int tile(int face) { return Tile.id(face % 34, 0, face >= 34); }
    State initial() {
        var self = view.seats().get(view.viewerSeat());
        long river = 0;
        for (var discard : self.river()) river |= 1L << Tile.kind(discard.tile());
        return new State(self.hand(), self.melds(), self.norths(), river, self.riichi());
    }
    Map<Integer, TileEfficiency> discards(State state) {
        return discards.computeIfAbsent(new ShapeKey(state), ignored -> HandAnalyzer.discardEfficiency(state.hand, state.melds));
    }
    TileEfficiency shape(State state) {
        return hands.computeIfAbsent(new ShapeKey(state), ignored -> HandAnalyzer.handEfficiency(state.hand, state.melds));
    }
    static int live(Set<Integer> kinds, int[] remaining) {
        return kinds.stream().mapToInt(k -> remaining[k] + remaining[k + 34]).sum();
    }
    Evaluation evaluate(State state, TileEfficiency shape, int[] remaining) {
        int live = live(shape.improving(), remaining), good = live(shape.goodShape(), remaining);
        var potential = value.potential(state);
        hands.putIfAbsent(new ShapeKey(state), shape);
        var waits = shape.shanten() == 0 ? value.waits(state, this.waits.computeIfAbsent(new ShapeKey(state),
            ignored -> HandAnalyzer.waits(state.hand, state.melds)), remaining) : BotValue.Waits.EMPTY;
        double points = shape.shanten() == 0 ? waits.average() : potential.estimate();
        // Ordinal utilities, not fitted win/deal-in probabilities or expected monetary returns.
        double utility = -55 * shape.shanten() + live * 1.2 + potential.retention();
        if (shape.shanten() == 0) utility += waits.quality() * 2 + Math.log1p(points / 1000) * 10;
        if (level != BotDifficulty.EASY) {
            utility += good * .6 + Math.log1p(points / 1000) * 6;
            if (!potential.viable() && shape.shanten() > 0) utility -= 45;
        }
        if (shape.shanten() == 0 && waits.quality() == 0) utility -= 55;
        return new Evaluation(shape.shanten(), live, good, points, utility, waits);
    }

    /** One draw and best discard; unseen tiles are an exchangeable sampling approximation,
     * including opponents' tiles/dead wall, never a claim about the actual live wall. */
    double forward(State state, Evaluation baseline) {
        double sum = 0;
        int total = 0;
        for (int face = 0; face < unseen.length; face++) {
            int count = unseen[face];
            if (count == 0) continue;
            drawNodes++;
            var remaining = unseen.clone(); remaining[face]--;
            int drawn = tile(face);
            var withDraw = state.draw(drawn);
            double best = Double.NEGATIVE_INFINITY;
            if (baseline.shanten == 0 && shape(state).improving().contains(Tile.kind(drawn))) {
                var win = value.score(state, drawn, true);
                if (win != null) best = 85 + Math.log1p(value.payment(win) / 1000.0) * 16;
            }
            var shapes = discards(withDraw);
            // Evaluate all structural continuations; expensive scoring is restricted to the
            // two strongest continuations. Same-shanten improvements are included naturally.
            var candidates = new ArrayList<State>();
            var faces = new java.util.HashSet<Integer>();
            for (int discard : withDraw.hand) {
                if (state.riichi && discard != drawn || !faces.add(face(discard))) continue;
                candidates.add(withDraw.discard(discard, false));
            }
            candidates.sort(Comparator.<State>comparingDouble(s -> {
                int removed = removedKind(withDraw, s);
                var sh = shapes.get(removed);
                return -55 * sh.shanten() + live(sh.improving(), remaining) * 1.2 + value.potential(s).retention();
            }).reversed().thenComparing(s -> s.hand.stream().map(BotAnalysis::face).sorted().toList().toString()));
            for (var next : candidates.subList(0, Math.min(2, candidates.size()))) {
                var sh = shapes.get(removedKind(withDraw, next));
                best = Math.max(best, evaluate(next, sh, remaining).utility);
            }
            sum += count * (Double.isFinite(best) ? best : baseline.utility);
            total += count;
        }
        return total == 0 ? baseline.utility : sum / total;
    }
    private static int removedKind(State before, State after) {
        int sum = before.hand.stream().mapToInt(Tile::kind).sum();
        return sum - after.hand.stream().mapToInt(Tile::kind).sum();
    }
}
