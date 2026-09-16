package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import top.skyeyefast.mchjong.engine.Meld;

/** Shared ordering and geometry for the table mesh, HUD and action previews. */
public record MeldLayout(List<Part> parts, double width) {
    public record Part(int tile, double x, boolean sideways, boolean back, boolean stacked) {}
    public MeldLayout { parts = List.copyOf(parts); }

    public static MeldLayout of(Meld meld, int owner) {
        var tiles = new ArrayList<>(meld.tiles());
        Integer added = meld.type() == Meld.Type.ADDED_KAN ? tiles.removeLast() : null;
        if (!meld.closed()) tiles.remove(Integer.valueOf(meld.calledTile()));
        tiles.sort(Integer::compareTo);
        int called = -1;
        if (!meld.closed()) {
            int relative = Math.floorMod(meld.fromSeat() - owner, 4);
            called = Math.min(relative == 3 ? 0 : relative == 2 ? 1 : 2, tiles.size());
            tiles.add(called, meld.calledTile());
        }
        var parts = new ArrayList<Part>();
        double x = 0;
        for (int i = 0; i < tiles.size(); i++) {
            boolean sideways = i == called;
            double width = sideways ? 0.160 : 0.104;
            parts.add(new Part(tiles.get(i), x + width / 2, sideways,
                meld.closed() && (i == 0 || i == tiles.size() - 1), false));
            x += width + 0.008;
        }
        if (added != null) parts.add(new Part(added, parts.get(called).x(), true, false, true));
        return new MeldLayout(parts, x - 0.008);
    }
}
