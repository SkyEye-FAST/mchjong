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
    public static final double HAND_Z = RiichiTableScene.HAND_Z;
    public static final double MELD_LEFT = -RiichiTableScene.MELD_RIGHT;
    public static final double RIVER_Z = RiichiTableScene.RIVER_Z;
    public enum Area { WALL, HAND, RIVER, MELD, FLOWER }
    public record Piece(int tile, int seat, Area area, int index, Vec3 position,
                        float yaw, boolean flat, boolean back, float scale) {}

    private McrTableScene() {}

    private static Piece piece(int tile, int seat, Area area, int index, double x, double height, double z,
                               float yaw, boolean flat, boolean back, float scale) {
        return new Piece(tile, seat, area, index, TableGeometry.orient(x, TableGeometry.FELT_Y + height, z, seat),
            seat * 90 + yaw, flat, back, scale);
    }

    public static Piece wallPiece(int physicalSlot, int tile) {
        int stack = McrWallLayout.stackOfSlot(physicalSlot);
        boolean upper = McrWallLayout.layer(physicalSlot) == McrWallLayout.Layer.UPPER;
        return piece(tile, McrWallLayout.seat(stack), Area.WALL, physicalSlot,
            ((McrWallLayout.STACKS_PER_SIDE - 1) / 2.0 - McrWallLayout.column(stack)) * WALL_STEP - WALL_OFFSET,
            (upper ? 1.5 : .5) * DEPTH, WALL_Z, 0, true, true, TILE_SCALE);
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
        boolean ended = view.phase() == top.skyeyefast.mchjong.engine.McrGame.Phase.HAND_END
            || view.phase() == top.skyeyefast.mchjong.engine.McrGame.Phase.MATCH_END;
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
            var melds = new ArrayList<McrMeldLayout>();
            for (var meld : player.melds()) melds.add(McrMeldLayout.of(meld, seat, ended));
            double meldWidth = melds.stream().mapToDouble(McrMeldLayout::width).sum() * TILE_SCALE;
            double flowerStart = meldWidth + (!melds.isEmpty() && !player.flowers().isEmpty() ? DEPTH / 4 : 0);
            double publicWidth = flowerStart + player.flowers().size() * WIDTH;
            double handGap = publicWidth > 0 && !hand.isEmpty() ? RiichiTableScene.HAND_MELD_GAP : 0;
            double rowWidth = publicWidth + handGap + hand.size() * WIDTH + (drawn ? DEPTH / 2 : 0);
            // A full flower run and four kongs share one rail, clear of the adjacent seat's corner.
            double rowRight = HAND_Z - HEIGHT / 2 - .035;
            double fit = Math.min(1, (rowRight - MELD_LEFT) / rowWidth);
            float rowScale = (float) (TILE_SCALE * fit);
            fit = rowScale / (double) TILE_SCALE;
            double handLeft = Math.max(-(hand.size() - 1) * WIDTH * fit / 2,
                MELD_LEFT + (publicWidth + handGap + WIDTH / 2) * fit);
            for (int index = 0; index < hand.size(); index++) {
                boolean flat = seat == winner;
                int original = indices.get(index);
                result.add(piece(hand.get(original), seat, Area.HAND, original,
                    handLeft + (index * WIDTH + (drawn && index == hand.size() - 1 ? DEPTH / 2 : 0)) * fit,
                    (flat ? DEPTH : HEIGHT) * fit / 2, HAND_Z, 0, flat, false, rowScale));
            }
            for (var part : McrRiverLayout.of(player.river()))
                result.add(piece(part.tile(), seat, Area.RIVER, part.historyIndex(), part.x() * TILE_SCALE,
                    DEPTH / 2, RIVER_Z + part.z() * TILE_SCALE, 0, true, false, TILE_SCALE));
            double left = MELD_LEFT;
            for (int group = 0; group < player.melds().size(); group++) {
                var layout = melds.get(group);
                for (int index = 0; index < layout.parts().size(); index++) {
                    var part = layout.parts().get(index);
                    result.add(piece(part.tile(), seat, Area.MELD, group * 4 + index, left + part.x() * rowScale,
                        DEPTH * fit / 2, HAND_Z + part.z() * rowScale, part.sideways() ? 90 : 0, true, part.back(), rowScale));
                }
                left += layout.width() * rowScale;
            }
            for (var part : McrFlowerLayout.of(player.flowers()))
                result.add(piece(part.tile(), seat, Area.FLOWER, part.index(), MELD_LEFT + flowerStart * fit + part.x() * rowScale,
                    DEPTH * fit / 2, HAND_Z, 0, true, false, rowScale));
        }
        return List.copyOf(result);
    }

    public static List<Piece> immersive(McrView view) {
        return build(view).stream().filter(piece -> piece.area() != Area.WALL).toList();
    }
}
