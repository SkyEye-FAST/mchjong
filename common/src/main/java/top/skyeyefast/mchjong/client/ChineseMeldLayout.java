package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.Tile;

/** Flat Chinese public meld row. An upgraded triplet uses the same row as any other melded kong. */
public record ChineseMeldLayout(List<Part> parts, double width) {
    public record Part(int tile, double x, double z, boolean sideways, boolean back) {}
    public ChineseMeldLayout { parts = List.copyOf(parts); }

    public static ChineseMeldLayout of(Meld meld, int owner, boolean revealConcealed) {
        if (owner < 0 || owner > 3) throw new IllegalArgumentException("Invalid Chinese meld owner");
        boolean quad = switch (meld.type()) {
            case SEQUENCE, TRIPLET -> false;
            case OPEN_QUAD, CONCEALED_QUAD, ADDED_QUAD -> true;
        };
        var tiles = new ArrayList<>(meld.tiles());
        if (tiles.size() != (quad ? 4 : 3)) throw new IllegalArgumentException("Invalid Chinese meld size");
        if (meld.closed() && (meld.fromSeat() != owner || meld.calledTile() != Tile.ABSENT))
            throw new IllegalArgumentException("Invalid concealed kong source");
        int called = -1;
        if (!meld.closed()) {
            int relative = Math.floorMod(meld.fromSeat() - owner, 4);
            if (relative == 0 || meld.fromSeat() < 0 || meld.fromSeat() > 3
                || meld.type() == Meld.Type.SEQUENCE && relative != 3
                || !tiles.remove(Integer.valueOf(meld.calledTile())))
                throw new IllegalArgumentException("Invalid Chinese meld source");
            tiles.sort(Tile.ORDER);
            called = relative == 3 ? 0 : relative == 2 ? 1 : tiles.size();
            tiles.add(called, meld.calledTile());
        }
        var result = new ArrayList<Part>();
        double x = 0;
        for (int index = 0; index < tiles.size(); index++) {
            boolean sideways = index == called;
            double width = sideways ? TileMesh.HEIGHT : TileMesh.WIDTH;
            result.add(new Part(tiles.get(index), x + width / 2,
                sideways ? ((double) TileMesh.HEIGHT - TileMesh.WIDTH) / 2 : 0,
                sideways, meld.closed() && !revealConcealed));
            x += width;
        }
        return new ChineseMeldLayout(result, x);
    }
}
