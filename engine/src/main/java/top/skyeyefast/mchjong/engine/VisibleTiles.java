package top.skyeyefast.mchjong.engine;

import java.util.HashSet;

/** Public table information and the recipient's own hand, never opponents' concealed tiles. */
public final class VisibleTiles {
    private VisibleTiles() {}

    public static int[] counts(TableView view) {
        var visible = new HashSet<Integer>();
        if (view.viewerSeat() >= 0 && view.viewerSeat() < view.seats().size())
            visible.addAll(view.seats().get(view.viewerSeat()).hand());
        for (var seat : view.seats()) {
            seat.river().forEach(discard -> visible.add(discard.tile()));
            seat.melds().forEach(meld -> visible.addAll(meld.tiles()));
            visible.addAll(seat.norths());
        }
        for (int tile : view.wall()) if (tile >= 0) visible.add(tile);
        if (view.focus() != null) visible.add(view.focus().tile());
        int[] counts = new int[34];
        for (int tile : visible) if (tile >= 0) counts[Tile.kind(tile)]++;
        return counts;
    }
}
