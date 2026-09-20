package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Complete hands use the engine scorer. Incomplete hands have explicitly heuristic potential. */
final class BotValue {
    private final TableView view;
    final int[] dora;
    private final Map<ScoreKey, HandScore> scores = new HashMap<>();
    private final Map<Double, Double> potentialPayments = new HashMap<>();
    private record ScoreKey(List<Integer> hand, List<String> melds, int bonus, int winning, boolean tsumo, int riichiHan, boolean replacement) {}
    record Potential(boolean viable, double estimate, double retention) {}
    record Waits(double ron, double tsumo, int ronTiles, int tsumoTiles) {
        static final Waits EMPTY = new Waits(0, 0, 0, 0);
        double quality() { return (ronTiles + tsumoTiles) / 2.0; }
        double average() { return (ron + tsumo) / Math.max(1, ronTiles + tsumoTiles); }
    }
    BotValue(TableView view) {
        this.view = view;
        dora = HandBonuses.indicators(view.wall(), view.rules().sanma());
    }
    int bonus(int tile) { return HandBonuses.tile(tile, dora); }
    int bonus(BotAnalysis.State state, int winning) {
        return HandBonuses.count(state.hand(), state.melds(), state.norths(), winning, dora);
    }
    private List<Integer> all(BotAnalysis.State state) {
        var all = new ArrayList<>(state.hand());
        state.melds().forEach(m -> all.addAll(m.tiles()));
        return all;
    }
    int wind() { return Math.floorMod(view.viewerSeat() - view.dealer(), view.rules().players()); }
    int yakuhai(int kind) {
        return (kind >= Tile.WHITE ? 1 : 0) + (kind == Tile.EAST + wind() ? 1 : 0)
            + (kind == Tile.EAST + view.round() / view.rules().players() ? 1 : 0);
    }
    Potential potential(BotAnalysis.State state) {
        var all = all(state);
        int[] counts = new int[34];
        for (int tile : all) counts[Tile.kind(tile)]++;
        boolean closed = state.melds().stream().allMatch(Meld::closed);
        double han = 0;
        for (int k = 27; k < 34; k++) if (counts[k] >= 2) han += yakuhai(k) * (counts[k] >= 3 ? 1 : .45);
        if ((closed || view.rules().kuitan()) && all.stream().noneMatch(t -> Tile.terminalOrHonor(Tile.kind(t)))) han++;
        for (int suit = 0; suit < 3; suit++) {
            int off = 0, suited = 0;
            for (int k = 0; k < 27; k++) { if (k / 9 == suit) suited += counts[k]; else off += counts[k]; }
            final int chosen = suit;
            boolean compatible = state.melds().stream().flatMap(m -> m.tiles().stream())
                .allMatch(t -> Tile.kind(t) >= 27 || Tile.kind(t) / 9 == chosen);
            if (compatible && suited >= 7 && off <= 2) han += (closed ? 3 : 2) * (3 - off) / 3.0;
        }
        if (closed && state.melds().isEmpty() && java.util.Arrays.stream(counts).filter(c -> c >= 2).count() >= 5) han += 1.5;
        int bonuses = bonus(state, Tile.ABSENT);
        // Retained bonuses have value only alongside a plausible yaku path; none satisfy minHan.
        boolean viable = han + (closed ? 1 : 0) >= view.rules().minHan();
        double potentialHan = han + (closed ? 1 : 0) + bonuses;
        double estimate = viable ? potentialPayments.computeIfAbsent(potentialHan,
            h -> HandAnalyzer.estimatedPayment(h, wind() == 0, false, view.rules())) : 0;
        return new Potential(viable, estimate, Math.min(8, han) * 2 + bonuses * 3);
    }
    HandScore score(BotAnalysis.State state, int winning, boolean tsumo, boolean replacement) {
        int bonus = bonus(state, winning);
        var key = new ScoreKey(state.hand().stream().map(BotAnalysis::face).sorted().toList(),
            state.melds().stream().map(Meld::libraryNotation).sorted().toList(), bonus, BotAnalysis.face(winning), tsumo,
            state.riichi() ? state.riichiHan() : 0, replacement);
        var extra = new ArrayList<String>();
        if (state.riichi()) extra.add(state.riichiHan() == 2 ? "WRichi" : "Richi");
        if (replacement && tsumo) extra.add("Rinshan");
        if (!scores.containsKey(key)) scores.put(key, HandAnalyzer.score(state.hand(), state.melds(), winning, tsumo,
            wind(), view.round() / view.rules().players(), bonus, extra, view.rules()));
        return scores.get(key);
    }
    int payment(HandScore score) {
        if (score.ron() > 0) return score.ron();
        return wind() == 0 ? score.tsumoDealer() * (view.rules().players() - 1)
            : score.tsumoDealer() + score.tsumoChild() * (view.rules().players() - 2);
    }
    Waits waits(BotAnalysis.State state, Set<Integer> kinds, int[] remaining) {
        // Any discarded structural wait makes the entire ron wait set furiten, including
        // waits that fail a custom yaku minimum. Tsumo is evaluated independently.
        boolean furiten = state.ronBlocked() || kinds.stream().anyMatch(k -> (state.river() & 1L << k) != 0);
        double ron = 0, tsumo = 0;
        int ronTiles = 0, tsumoTiles = 0;
        for (int kind : kinds) for (int face : new int[]{kind, kind + 34}) {
            int count = remaining[face];
            if (count == 0) continue;
            int tile = BotAnalysis.tile(face);
            var r = furiten ? null : score(state, tile, false, false);
            var t = score(state, tile, true, false);
            if (r != null) { ron += count * payment(r); ronTiles += count; }
            if (t != null) { tsumo += count * payment(t); tsumoTiles += count; }
        }
        return new Waits(ron, tsumo, ronTiles, tsumoTiles);
    }
}
