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
    private static final TiltedWallLayout WALL = TiltedWallLayout.compact(McrWallLayout.STACKS_PER_SIDE,
        McrWallLayout.STACKS_PER_SIDE, WIDTH, HEIGHT);
    public static final double HAND_Z = RiichiTableScene.HAND_Z;
    public static final double MELD_RIGHT = RiichiTableScene.MELD_RIGHT;
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
        var position = WALL.position(McrWallLayout.column(stack));
        return piece(tile, McrWallLayout.seat(stack), Area.WALL, physicalSlot,
            position.x, (upper ? 1.5 : .5) * DEPTH, position.z, WALL.yaw(), true, true, TILE_SCALE);
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
            double flowerLeft = -HAND_Z + HEIGHT / 2 + .035;
            double handWidth = hand.size() * WIDTH + (drawn ? RiichiTableScene.DRAW_GAP : 0);
            double gap = hand.isEmpty() ? 0 : RiichiTableScene.HAND_MELD_GAP;
            double flowerWidth = player.flowers().size() * WIDTH;
            double rowWidth = flowerWidth + handWidth + meldWidth + (flowerWidth > 0 ? gap : 0) + (meldWidth > 0 ? gap : 0);
            float rowScale = (float) (TILE_SCALE * Math.min(1, (MELD_RIGHT - flowerLeft) / Math.max(WIDTH, rowWidth)));
            double fit = rowScale / (double) TILE_SCALE;
            double handLeft = -(handWidth - WIDTH) * fit / 2;
            if (meldWidth > 0) handLeft = Math.min(handLeft, MELD_RIGHT - (meldWidth + gap + handWidth - WIDTH / 2) * fit);
            if (flowerWidth > 0) handLeft = Math.max(handLeft, flowerLeft + (flowerWidth + gap + WIDTH / 2) * fit);
            for (int index = 0; index < hand.size(); index++) {
                boolean flat = ended;
                int original = indices.get(index);
                result.add(piece(hand.get(original), seat, Area.HAND, original,
                    handLeft + (index * WIDTH + (drawn && index == hand.size() - 1 ? RiichiTableScene.DRAW_GAP : 0)) * fit,
                    (flat ? DEPTH : HEIGHT) * fit / 2, HAND_Z, 0, flat, false, rowScale));
            }
            for (var part : McrRiverLayout.of(player.river()))
                result.add(piece(part.tile(), seat, Area.RIVER, part.historyIndex(), part.x() * TILE_SCALE,
                    DEPTH / 2, RIVER_Z + part.z() * TILE_SCALE, 0, true, false, TILE_SCALE));
            double right = MELD_RIGHT;
            for (int group = 0; group < player.melds().size(); group++) {
                var layout = melds.get(group);
                double left = right - layout.width() * rowScale;
                for (int index = 0; index < layout.parts().size(); index++) {
                    var part = layout.parts().get(index);
                    result.add(piece(part.tile(), seat, Area.MELD, group * 4 + index, left + part.x() * rowScale,
                        DEPTH * fit / 2, HAND_Z + part.z() * rowScale, part.sideways() ? 90 : 0, true, part.back(), rowScale));
                }
                right = left;
            }
            for (var part : McrFlowerLayout.of(player.flowers()))
                result.add(piece(part.tile(), seat, Area.FLOWER, part.index(), flowerLeft + part.x() * rowScale,
                    DEPTH * fit / 2, HAND_Z, 0, true, false, rowScale));
        }
        return List.copyOf(result);
    }

    public static List<Piece> immersive(McrView view) {
        return build(view).stream().filter(piece -> piece.area() != Area.WALL).toList();
    }
}
