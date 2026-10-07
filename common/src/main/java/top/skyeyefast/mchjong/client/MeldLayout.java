package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.Tile;

/** Shared ordering and geometry for the table mesh, HUD and action previews. */
public record MeldLayout(List<Part> parts, double width, TileDimensions dimensions) {
    public record Part(int tile, double x, double z, boolean sideways, boolean back) {}
    public MeldLayout { parts = List.copyOf(parts); }

    public static MeldLayout of(Meld meld, int owner, top.skyeyefast.mchjong.engine.MahjongVariant variant, boolean reveal) {
        if (variant == top.skyeyefast.mchjong.engine.MahjongVariant.TAIWAN) {
            var layout = ChineseMeldLayout.of(meld, owner, !meld.tiles().contains(Tile.HIDDEN), TileDimensions.of(variant));
            return new MeldLayout(layout.parts().stream().map(p -> new Part(p.tile(), p.x(), p.z(), p.sideways(), p.back())).toList(), layout.width(), TileDimensions.of(variant));
        }
        if (variant != top.skyeyefast.mchjong.engine.MahjongVariant.MCR) {
            var layout = nativeMeld(meld, owner, TileDimensions.of(variant));
            if (variant != top.skyeyefast.mchjong.engine.MahjongVariant.SICHUAN || !reveal) return layout;
            return new MeldLayout(layout.parts().stream().map(part -> new Part(part.tile(), part.x(), part.z(), part.sideways(), false)).toList(), layout.width(), TileDimensions.of(variant));
        }
        var nativeLayout = ChineseMeldLayout.of(meld, owner, reveal, TileDimensions.of(variant));
        return new MeldLayout(nativeLayout.parts().stream().map(part -> new Part(part.tile(), part.x(), part.z(), part.sideways(), part.back())).toList(), nativeLayout.width(), TileDimensions.of(variant));
    }
    public static MeldLayout of(Meld meld, int owner) {
        return nativeMeld(meld, owner, TileDimensions.SMALL);
    }

    private static MeldLayout nativeMeld(Meld meld, int owner, TileDimensions dimensions) {
        var tiles = new ArrayList<>(meld.tiles());
        Integer added = meld.type() == Meld.Type.ADDED_QUAD ? tiles.remove(tiles.size() - 1) : null;
        if (!meld.closed()) tiles.remove(Integer.valueOf(meld.calledTile()));
        if (!meld.closed()) tiles.sort(Tile.ORDER);
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
            double width = sideways ? dimensions.height() : dimensions.width();
            double z = sideways ? ((double) dimensions.height() - dimensions.width()) / 2 : 0;
            parts.add(new Part(tiles.get(i), x + width / 2, z, sideways,
                meld.closed() && (i == 0 || i == tiles.size() - 1)));
            x += width;
        }
        if (added != null) {
            Part original = parts.get(called);
            parts.add(new Part(added, original.x(), original.z() - dimensions.width(), true, false));
        }
        return new MeldLayout(parts, x, dimensions);
    }
}
