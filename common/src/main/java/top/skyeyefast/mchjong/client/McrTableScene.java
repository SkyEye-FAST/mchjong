package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.McrSettlement;
import top.skyeyefast.mchjong.engine.McrView;
import top.skyeyefast.mchjong.engine.McrWallLayout;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;

/** MCR-only physical scene. The same seat-local geometry is rotated for every player. */
public final class McrTableScene {
    public static final float TILE_SCALE = .82f;
    public static final double WIDTH = (double) TileMesh.WIDTH * TILE_SCALE;
    public static final double HEIGHT = (double) TileMesh.HEIGHT * TILE_SCALE;
    public static final double DEPTH = (double) TileMesh.DEPTH * TILE_SCALE;
    public static final double WALL_STEP = WIDTH + DEPTH / 16;
    public static final double WALL_LENGTH = (McrWallLayout.STACKS_PER_SIDE - 1) * WALL_STEP + WIDTH;
    public static final double WALL_Z = (WALL_LENGTH - HEIGHT + DEPTH) / 2;
    // The short end meets the next wall with the same seam as adjacent stacks.
    public static final double WALL_OFFSET = WALL_LENGTH / 2 - WALL_Z + HEIGHT / 2 + (WALL_STEP - WIDTH);
    public static final double HAND_Z = TableGeometry.FELT_HALF_WIDTH - DEPTH;
    public static final double PUBLIC_Z = HAND_Z - (HEIGHT + DEPTH) / 2 - DEPTH;
    public static final double PUBLIC_LEFT = -PUBLIC_Z + HEIGHT / 2;
    public static final double MELD_LEFT = PUBLIC_LEFT + McrFlowerLayout.COLUMNS * WIDTH + DEPTH;
    // A uniform local offset leaves four complete discard rows inside the wall footprint.
    public static final double RIVER_X = -HEIGHT;
    public static final double RIVER_Z = (McrRiverLayout.COLUMNS * WIDTH - HEIGHT) / 2 + DEPTH / 4;
    public enum Area { WALL, HAND, RIVER, MELD, FLOWER }
    public record Piece(int tile, int seat, Area area, int index, Vec3 position,
                        float yaw, boolean flat, boolean back) {}

    private McrTableScene() {}

    private static Piece piece(int tile, int seat, Area area, int index, double x, double height, double z,
                               float yaw, boolean flat, boolean back) {
        return new Piece(tile, seat, area, index, TableGeometry.orient(x, TableGeometry.FELT_Y + height, z, seat),
            seat * 90 + yaw, flat, back);
    }

    public static Piece wallPiece(int physicalSlot, int tile) {
        int stack = McrWallLayout.stackOfSlot(physicalSlot);
        boolean upper = McrWallLayout.layer(physicalSlot) == McrWallLayout.Layer.UPPER;
        return piece(tile, McrWallLayout.seat(stack), Area.WALL, physicalSlot,
            ((McrWallLayout.STACKS_PER_SIDE - 1) / 2.0 - McrWallLayout.column(stack)) * WALL_STEP - WALL_OFFSET,
            (upper ? 1.5 : .5) * DEPTH, WALL_Z, 0, true, true);
    }

    /** A built, unopened wall contains no private tile identities. */
    public static List<Piece> fullWall() { return List.copyOf(wall(Collections.nCopies(McrWallLayout.SLOTS, Tile.HIDDEN))); }

    private static List<Piece> wall(List<Integer> slots) {
        var result = new ArrayList<Piece>();
        for (int slot = 0; slot < slots.size(); slot++)
            if (slots.get(slot) != Tile.ABSENT) result.add(wallPiece(slot, slots.get(slot)));
        return result;
    }

    public static List<Piece> build(McrView view) {
        var result = new ArrayList<>(wall(view.wall()));
        int winner = view.result() instanceof McrSettlement.Win win ? win.winner() : -1;
        for (int seat = 0; seat < 4; seat++) {
            var player = view.seats().get(seat);
            var hand = player.hand();
            var indices = new ArrayList<>(java.util.stream.IntStream.range(0, hand.size()).boxed().toList());
            boolean drawn = player.drawn() != Tile.ABSENT && !hand.isEmpty();
            if (drawn && player.drawn() >= 0) {
                int original = hand.indexOf(player.drawn());
                indices.remove(Integer.valueOf(original));
                indices.add(original);
            }
            double handLeft = -(hand.size() - 1) * WIDTH / 2;
            for (int index = 0; index < hand.size(); index++) {
                boolean flat = seat == winner;
                int original = indices.get(index);
                result.add(piece(hand.get(original), seat, Area.HAND, original,
                    handLeft + index * WIDTH + (drawn && index == hand.size() - 1 ? DEPTH / 2 : 0),
                    (flat ? DEPTH : HEIGHT) / 2, HAND_Z, 0, flat, false));
            }
            for (var part : McrRiverLayout.of(player.river()))
                result.add(piece(part.tile(), seat, Area.RIVER, part.historyIndex(), RIVER_X + part.x() * TILE_SCALE,
                    DEPTH / 2, RIVER_Z + part.z() * TILE_SCALE, 0, true, false));
            double left = MELD_LEFT;
            for (int group = 0; group < player.melds().size(); group++) {
                var layout = McrMeldLayout.of(player.melds().get(group), seat, seat == winner);
                for (int index = 0; index < layout.parts().size(); index++) {
                    var part = layout.parts().get(index);
                    result.add(piece(part.tile(), seat, Area.MELD, group * 4 + index, left + part.x() * TILE_SCALE,
                        DEPTH / 2, PUBLIC_Z + part.z() * TILE_SCALE, part.sideways() ? 90 : 0, true, part.back()));
                }
                left += layout.width() * TILE_SCALE + DEPTH / 4;
            }
            for (var part : McrFlowerLayout.of(player.flowers()))
                result.add(piece(part.tile(), seat, Area.FLOWER, part.index(), PUBLIC_LEFT + part.x() * TILE_SCALE,
                    DEPTH / 2, PUBLIC_Z + part.z() * TILE_SCALE, 0, true, false));
        }
        return List.copyOf(result);
    }
}
