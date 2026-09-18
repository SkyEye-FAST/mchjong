package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cached structural waits; availability is the number of unseen copies, not a peek at the wall. */
public final class TenpaiHints {
    public record Wait(int kind, int remaining) {}

    private List<Integer> hand = List.of();
    private List<Meld> melds = List.of();
    private final Map<Integer, Set<Integer>> byDiscard = new HashMap<>();
    private TableView snapshot;
    private int lastDiscard = Tile.ABSENT;
    private List<Wait> result = List.of();

    public List<Wait> waits(TableView view, int discard) {
        if (snapshot != view || lastDiscard != discard) {
            result = calculate(view, discard);
            snapshot = view;
            lastDiscard = discard;
        }
        return result;
    }

    private List<Wait> calculate(TableView view, int discard) {
        if (view.viewerSeat() < 0 || view.viewerSeat() >= view.seats().size()
            || view.exitVote() != null || !(view.phase() == Game.Phase.TURN
                || view.phase() == Game.Phase.REACTION || view.phase() == Game.Phase.DRAW)) return List.of();
        var self = view.seats().get(view.viewerSeat());
        int size = self.hand().size() + self.melds().size() * 3;
        if ((size != 13 && size != 14) || self.hand().stream().anyMatch(tile -> tile < 0)) return List.of();
        int key = Tile.ABSENT;
        if (size == 14) {
            if (!self.hand().contains(discard) || view.actions().stream().noneMatch(action ->
                (action.type() == Action.Type.DISCARD || action.type() == Action.Type.RIICHI)
                    && action.tiles().contains(discard))) return List.of();
            key = Tile.kind(discard);
        }
        if (!hand.equals(self.hand()) || !melds.equals(self.melds())) {
            hand = self.hand();
            melds = self.melds();
            byDiscard.clear();
        }
        var kinds = byDiscard.computeIfAbsent(key, kind -> {
            var concealed = new ArrayList<>(hand);
            if (kind >= 0) concealed.remove(concealed.stream().filter(tile -> Tile.kind(tile) == kind).findFirst().orElseThrow());
            return HandAnalyzer.waits(concealed, melds);
        });
        int[] known = VisibleTiles.counts(view);
        return kinds.stream().filter(kind -> !view.rules().sanma() || kind == 0 || kind >= 8).sorted()
            .map(kind -> new Wait(kind, Math.max(0, 4 - known[kind]))).toList();
    }
}
