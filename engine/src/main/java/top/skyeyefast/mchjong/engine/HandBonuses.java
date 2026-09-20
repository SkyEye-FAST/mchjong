package top.skyeyefast.mchjong.engine;

import java.util.List;

/** The same physical red, indicator and north bonuses for settlement and hypothetical wins. */
final class HandBonuses {
    private HandBonuses() {}
    static int[] indicators(List<Integer> indicators, boolean sanma) {
        int[] counts = new int[34];
        for (int tile : indicators) if (tile >= 0) counts[Tile.doraAfter(Tile.kind(tile), sanma)]++;
        return counts;
    }
    static int tile(int tile, int[] dora) { return dora[Tile.kind(tile)] + (Tile.red(tile) ? 1 : 0); }
    static int count(List<Integer> hand, List<Meld> melds, List<Integer> norths, int winning, int[] dora) {
        int count = norths.size();
        if (winning >= 0 && hand.size() + melds.size() * 3 == 13) count += tile(winning, dora);
        for (int tile : hand) count += tile(tile, dora);
        for (var meld : melds) for (int tile : meld.tiles()) count += tile(tile, dora);
        for (int tile : norths) count += tile(tile, dora);
        return count;
    }
}
