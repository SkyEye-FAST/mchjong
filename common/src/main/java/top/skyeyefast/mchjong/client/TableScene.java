package top.skyeyefast.mchjong.client;

import top.skyeyefast.mchjong.engine.HandVisibility;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.Discard;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.engine.WallLayout;
import top.skyeyefast.mchjong.world.TableGeometry;

/** One geometric description drives both the 3D meshes and the interaction anchors. */
public final class TableScene {
    public static final float TILE_SCALE = 0.82f;
    public static final double HAND_Z = TableGeometry.FELT_HALF_WIDTH - 0.09;
    public static final double HAND_STEP = (double) TileMesh.WIDTH * TILE_SCALE;
    public static final double DRAW_GAP = 0.035;
    public static final double MELD_RIGHT = TableGeometry.FELT_HALF_WIDTH - 1.0 / 16.0;
    public static final double HAND_MELD_GAP = 0.06;
    public static final double WALL_Z = 0.91;
    public static final double RIVER_STEP = (double) TileMesh.WIDTH * TILE_SCALE;
    public static final double RIVER_ROW = (double) TileMesh.HEIGHT * TILE_SCALE;
    public static final double WALL_STEP = RIVER_STEP;
    private static final double FLAT_CENTER = TileMesh.DEPTH / 2.0;
    public enum Area { HAND, WALL, RIVER, MELD, NORTH, LOOSE }
    public record Piece(int tile, int seat, Area area, int index, Vec3 position, float yaw, boolean flat, boolean back) {}
    private TableScene() {}

    private static Piece piece(int tile, int seat, Area area, int index, double x, double y, double z,
            float additionalYaw, boolean flat, boolean back) {
        return new Piece(tile, seat, area, index, TableGeometry.orient(x, y, z, seat),
            seat * 90 + additionalYaw, flat, back);
    }

    public static List<Piece> build(TableView view) {
        List<Piece> result = new ArrayList<>(200);
        if (view.handling() != null && (view.phase() == Game.Phase.SHUFFLE
            || view.phase() == Game.Phase.BUILD_WALL)) {
            int size = view.rules().sanma() ? 108 : 136;
            for (int index = 0; index < size; index++) {
                int side = WallLayout.side(index, view.wallBreak(), size, view.rules().players());
                if ((view.handling().builtWalls() & 1 << side) == 0) result.add(loosePiece(view, index, side));
            }
        }
        double top = TableGeometry.FELT_Y;
        for (int seat = 0; seat < view.seats().size(); seat++) {
            TableView.Seat player = view.seats().get(seat);
            var melds = new ArrayList<MeldLayout>(player.melds().size());
            double meldLeft = MELD_RIGHT;
            for (Meld meld : player.melds()) {
                var layout = MeldLayout.of(meld, seat);
                melds.add(layout);
                meldLeft -= layout.width() * TILE_SCALE;
            }
            // Stay centered whenever possible; move only far enough to clear the actual meld bounds.
            // Include the actual drawn tile and gap, without reserving empty meld or draw slots.
            int concealed = player.hand().size() - (player.drawn() != Tile.ABSENT ? 1 : 0);
            double left = -Math.max(0, concealed - 1) * HAND_STEP / 2;
            if (!melds.isEmpty() && !player.hand().isEmpty()) {
                double handRight = left + (player.hand().size() - 1) * HAND_STEP
                    + (player.drawn() != Tile.ABSENT ? DRAW_GAP : 0) + RIVER_STEP / 2;
                left += Math.min(0, meldLeft - HAND_MELD_GAP - handRight);
            }
            for (int i = 0; i < player.hand().size(); i++) {
                boolean drawn = player.drawn() != Tile.ABSENT && i == player.hand().size() - 1;
                boolean declaration = view.focus() != null && view.focus().declaration()
                    && view.focus().seat() == seat && view.focus().index() == i;
                boolean flat = player.exposed() || declaration
                    || view.handVisibility() == HandVisibility.OPEN;
                result.add(piece(declaration ? view.focus().tile() : player.hand().get(i), seat, Area.HAND, i,
                    left + i * HAND_STEP + (drawn ? DRAW_GAP : 0), top + (flat ? FLAT_CENTER : 0.081) * TILE_SCALE, HAND_Z, 0, flat, false));
            }
            int riverSlot = 0;
            double riverX = -2.5 * RIVER_STEP;
            for (int i = 0; i < player.river().size(); i++) {
                Discard discard = player.river().get(i);
                if (discard.called()) continue;
                if (riverSlot % 6 == 0) riverX = -2.5 * RIVER_STEP;
                double extra = discard.riichi() ? ((double) TileMesh.HEIGHT - TileMesh.WIDTH) * TILE_SCALE : 0;
                result.add(piece(discard.tile(), seat, Area.RIVER, i, riverX + extra / 2,
                    top + FLAT_CENTER * TILE_SCALE, 0.355 + riverSlot / 6 * RIVER_ROW - extra / 2, discard.riichi() ? 90 : 0, true, false));
                riverX += RIVER_STEP + extra;
                riverSlot++;
            }
            double meldRight = MELD_RIGHT;
            for (int meldIndex = 0; meldIndex < melds.size(); meldIndex++) {
                MeldLayout layout = melds.get(meldIndex);
                double start = meldRight - layout.width() * TILE_SCALE;
                for (int i = 0; i < layout.parts().size(); i++) {
                    var part = layout.parts().get(i);
                    result.add(piece(part.tile(), seat, Area.MELD, meldIndex * 4 + i, start + part.x() * TILE_SCALE,
                        top + FLAT_CENTER * TILE_SCALE, HAND_Z + part.z() * TILE_SCALE,
                        part.sideways() ? 90 : 0, true, part.back()));
                }
                meldRight = start;
            }
            // Two short rows leave the adjacent player's right-corner melds clear as well.
            double northLeft = -HAND_Z + RIVER_ROW / 2 + .035;
            for (int i = 0; i < player.norths().size(); i++)
                result.add(piece(player.norths().get(i), seat, Area.NORTH, i, northLeft + (i % 2 + .5) * RIVER_STEP,
                    top + FLAT_CENTER * TILE_SCALE, HAND_Z - i / 2 * RIVER_ROW, 0, true, false));
        }
        int size = view.wall().size();
        if (size > 0) {
            for (int i = 0; i < size; i++) {
                if (view.wall().get(i) != Tile.ABSENT) result.add(wallPiece(view, i, false));
            }
        }
        if (view.handling() != null && (view.phase() == Game.Phase.HAND_END
            || view.phase() == Game.Phase.MATCH_END)) {
            int[] collected = new int[4];
            for (int i = 0; i < result.size(); i++) {
                Piece old = result.get(i);
                if (old.area() == Area.WALL || !view.seats().get(old.seat()).ready()) continue;
                int index = collected[old.seat()]++;
                Vec3 position = TableGeometry.orient((index % 6 - 2.5) * .09,
                    top + (FLAT_CENTER + index / 18 * TileMesh.DEPTH) * TILE_SCALE,
                    .15 + index / 6 % 3 * .14, old.seat());
                result.set(i, new Piece(old.tile(), old.seat(), old.area(), old.index(), position,
                    old.yaw(), true, true));
            }
        }
        return List.copyOf(result);
    }

    /** Scatter a complete set in two shallow layers. All faces stay hidden before dealing. */
    private static Piece loosePiece(TableView view, int index, int side) {
        // A stable permutation distributes each wall's share over the pile without exposing its order.
        int slot = index * 53 % (view.rules().sanma() ? 108 : 136);
        int column = slot % 10, row = slot / 10 % 7, layer = slot / 70;
        java.util.Random random = new java.util.Random(index * 193L + view.handNumber() * 71L
            + (view.phase() == Game.Phase.SHUFFLE ? 0 : 3109));
        return new Piece(Tile.HIDDEN, side, Area.LOOSE, index,
            new Vec3((column - 4.5) * .135 + (random.nextDouble() - .5) * .02,
                TableGeometry.FELT_Y + (FLAT_CENTER + layer * TileMesh.DEPTH) * TILE_SCALE,
                (row - 3) * .17 + (random.nextDouble() - .5) * .02),
            (random.nextFloat() - .5f) * 50, true, true);
    }

    /** A complete wall is reconstructed with hidden sentinels, never guessed tile identities. */
    public static Piece wallPiece(TableView view, int index, boolean complete) {
        int size = view.wall().size();
        int stacksPerSide = size / (view.rules().sanma() ? 6 : 8);
        int stack = WallLayout.stack(index, view.wallBreak(), size);
        int seat = stack / stacksPerSide;
        int column = stack % stacksPerSide;
        int tile = view.wall().get(index);
        int mate = view.wall().get(index ^ 1);
        if (complete) {
            if (tile == Tile.ABSENT) tile = Tile.HIDDEN;
            if (mate == Tile.ABSENT) mate = Tile.HIDDEN;
        }
        // Live draws advance from the front; replacements and indicators count from the back.
        // Visibility must not change layers (in particular when both dora and ura are shown).
        boolean upper = mate != Tile.ABSENT && index % 2 == (index < size - 14 ? 0 : 1);
        return piece(tile, seat, Area.WALL, index, (column - (stacksPerSide - 1) / 2.0) * WALL_STEP - 0.08,
            TableGeometry.FELT_Y + (FLAT_CENTER + (upper ? TileMesh.DEPTH : 0)) * TILE_SCALE, WALL_Z, 0, true, tile < 0);
    }
}
