package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

/** Server-only MCR hand zones and match points. */
final class McrPlayerState {
    int points;
    final List<Integer> hand = new ArrayList<>();
    final List<Meld> melds = new ArrayList<>();
    final List<McrDiscard> river = new ArrayList<>();
    final List<Integer> flowers = new ArrayList<>();
    int drawn = Tile.ABSENT;

    /** Drawn tiles and called discards alias tiles in the owned zones. */
    List<Integer> physicalTiles() {
        var tiles = new ArrayList<>(hand);
        melds.forEach(meld -> tiles.addAll(meld.tiles()));
        tiles.addAll(flowers);
        river.stream().filter(discard -> !discard.called()).forEach(discard -> tiles.add(discard.tile()));
        return tiles;
    }

    void resetHand() {
        hand.clear();
        melds.clear();
        river.clear();
        flowers.clear();
        drawn = Tile.ABSENT;
    }
}
