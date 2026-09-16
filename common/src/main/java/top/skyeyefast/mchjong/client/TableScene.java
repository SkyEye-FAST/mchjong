package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.Discard;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;

/** One geometric description drives both the 3D meshes and the interaction anchors. */
public final class TableScene {
    public static final float TILE_SCALE = 0.82f;
    public static final double HAND_Z = 1.25;
    public static final double HAND_STEP = 0.1;
    public static final double HAND_LEFT = -1.12;
    public static final double RIVER_STEP = (double) TileMesh.WIDTH * TILE_SCALE;
    public static final double RIVER_ROW = (double) TileMesh.HEIGHT * TILE_SCALE;
    public static final double WALL_STEP = RIVER_STEP;
    private static final double FLAT_CENTER = TileMesh.DEPTH / 2.0;
    public enum Area { HAND, WALL, RIVER, MELD, NORTH }
    public record Piece(int tile, int seat, Area area, int index, Vec3 position, float yaw, boolean flat, boolean back) {}
    private TableScene() {}

    private static Piece piece(int tile, int seat, Area area, int index, double x, double y, double z,
            float additionalYaw, boolean flat, boolean back) {
        return new Piece(tile, seat, area, index, TableGeometry.orient(x, y, z, seat),
            seat * 90 + additionalYaw, flat, back);
    }

    public static List<Piece> build(TableView view) {
        List<Piece> result = new ArrayList<>(200);
        double top = TableGeometry.FELT_Y;
        for (int seat = 0; seat < view.seats().size(); seat++) {
            TableView.Seat player = view.seats().get(seat);
            // Reserve the right rail from the first deal, so calling never shifts the whole hand.
            double left = HAND_LEFT;
            for (int i = 0; i < player.hand().size(); i++) {
                boolean drawn = player.drawn() != Tile.ABSENT && i == player.hand().size() - 1;
                boolean declaration = view.focus() != null && view.focus().declaration()
                    && view.focus().seat() == seat && view.focus().index() == i;
                boolean flat = player.exposed() || declaration || view.openHands() && view.viewerSeat() >= 0 && seat != view.viewerSeat();
                result.add(piece(declaration ? view.focus().tile() : player.hand().get(i), seat, Area.HAND, i,
                    left + i * HAND_STEP + (drawn ? 0.035 : 0), top + (flat ? FLAT_CENTER : 0.081) * TILE_SCALE, HAND_Z, 0, flat, false));
            }
            int riverSlot = 0;
            double riverX = -2.5 * RIVER_STEP;
            for (int i = 0; i < player.river().size(); i++) {
                Discard discard = player.river().get(i);
                if (discard.called()) continue;
                if (riverSlot % 6 == 0) riverX = -2.5 * RIVER_STEP;
                double extra = discard.riichi() ? ((double) TileMesh.HEIGHT - TileMesh.WIDTH) * TILE_SCALE : 0;
                result.add(piece(discard.tile(), seat, Area.RIVER, i, riverX + extra / 2,
                    top + FLAT_CENTER * TILE_SCALE, 0.355 + riverSlot / 6 * RIVER_ROW, discard.riichi() ? 90 : 0, true, false));
                riverX += RIVER_STEP + extra;
                riverSlot++;
            }
            double meldRight = 1.12;
            for (int meldIndex = 0; meldIndex < player.melds().size(); meldIndex++) {
                Meld meld = player.melds().get(meldIndex);
                MeldLayout layout = MeldLayout.of(meld, seat);
                double start = meldRight - layout.width() * TILE_SCALE;
                for (int i = 0; i < layout.parts().size(); i++) {
                    var part = layout.parts().get(i);
                    result.add(piece(part.tile(), seat, Area.MELD, meldIndex * 4 + i, start + part.x() * TILE_SCALE,
                        top + (FLAT_CENTER + (part.stacked() ? TileMesh.DEPTH : 0)) * TILE_SCALE, HAND_Z - 0.015,
                        part.sideways() ? 90 : 0, true, part.back()));
                }
                meldRight = start - 0.035;
            }
            for (int i = 0; i < player.norths().size(); i++)
                result.add(piece(player.norths().get(i), seat, Area.NORTH, i, -1.17 + (i % 2) * 0.112,
                    top + FLAT_CENTER * TILE_SCALE, 0.68 - i / 2 * 0.18, 0, true, false));
        }
        int size = view.wall().size();
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                if (view.wall().get(i) != Tile.ABSENT) result.add(wallPiece(view, i, false));
            }
        }
        return List.copyOf(result);
    }

    /** A complete wall is reconstructed with hidden sentinels, never guessed tile identities. */
    public static Piece wallPiece(TableView view, int index, boolean complete) {
        int size = view.wall().size();
        int stacksPerSide = size / (view.rules().sanma() ? 6 : 8);
        int display = (index + view.wallBreak()) % size;
        int seat = display / 2 / stacksPerSide;
        int column = display / 2 % stacksPerSide;
        int tile = view.wall().get(index);
        int mate = view.wall().get(index ^ 1);
        if (complete) {
            if (tile == Tile.ABSENT) tile = Tile.HIDDEN;
            if (mate == Tile.ABSENT) mate = Tile.HIDDEN;
        }
        boolean upper = mate != Tile.ABSENT && (tile >= 0 || mate < 0 && index % 2 == 0);
        return piece(tile, seat, Area.WALL, index, (column - (stacksPerSide - 1) / 2.0) * WALL_STEP - 0.08,
            TableGeometry.FELT_Y + (FLAT_CENTER + (upper ? TileMesh.DEPTH : 0)) * TILE_SCALE, 1.00, 0, true, tile < 0);
    }
}
