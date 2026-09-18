package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.Tile;

/** Shared ordering and geometry for the table mesh, HUD and action previews. */
public record MeldLayout(List<Part> parts, double width) {
    public record Part(int tile, double x, double z, boolean sideways, boolean back) {}
    public MeldLayout { parts = List.copyOf(parts); }

    public static MeldLayout of(Meld meld, int owner) {
        var tiles = new ArrayList<>(meld.tiles());
        Integer added = meld.type() == Meld.Type.ADDED_KAN ? tiles.removeLast() : null;
        if (!meld.closed()) tiles.remove(Integer.valueOf(meld.calledTile()));
        tiles.sort(Tile.ORDER);
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
            double width = sideways ? TileMesh.HEIGHT : TileMesh.WIDTH;
            double z = sideways ? ((double) TileMesh.HEIGHT - TileMesh.WIDTH) / 2 : 0;
            parts.add(new Part(tiles.get(i), x + width / 2, z, sideways,
                meld.closed() && (i == 0 || i == tiles.size() - 1)));
            x += width;
        }
        if (added != null) {
            Part original = parts.get(called);
            parts.add(new Part(added, original.x(), original.z() - TileMesh.WIDTH, true, false));
        }
        return new MeldLayout(parts, x);
    }
}
