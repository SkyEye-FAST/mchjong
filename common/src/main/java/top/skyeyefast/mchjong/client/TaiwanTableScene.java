package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.TaiwanView;
import top.skyeyefast.mchjong.engine.TaiwanWallLayout;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Taiwan physical scene, preserving the engine fixed-slot contract. The same seat-local geometry is rotated for every player. */
public final class TaiwanTableScene {
    public static final float TILE_SCALE = 1.0f;
    public static final double WIDTH = (double) TileMesh.WIDTH * TILE_SCALE;
    public static final double HEIGHT = (double) TileMesh.HEIGHT * TILE_SCALE;
    public static final double DEPTH = (double) TileMesh.DEPTH * TILE_SCALE;
    public static final double HAND_Z = TableGeometry.FELT_HALF_WIDTH - HEIGHT / 2 - .015;
    public static final double MELD_LEFT = -TableGeometry.FELT_HALF_WIDTH + .015;
    public static final double RIVER_Z = TableIndicator.HALF_WIDTH + HEIGHT / 2 + .005;
    public static final double RIVER_X = WIDTH / 2;
    public enum Area { WALL, HAND, RIVER, MELD, FLOWER }
    public record Piece(int tile, int seat, Area area, int index, Vec3 position,
                        float yaw, boolean flat, boolean back, float scale) {}

    private TaiwanTableScene() {}

    private static Piece piece(int tile, int seat, Area area, int index, double x, double height, double z,
                               float yaw, boolean flat, boolean back, float scale) {
        return new Piece(tile, seat, area, index, TableGeometry.orient(x, TableGeometry.FELT_Y + height, z, seat),
            seat * 90 + yaw, flat, back, scale);
    }

    public static Piece wallPiece(int size, int physicalSlot, int tile) {
        boolean upper = TaiwanWallLayout.layer(physicalSlot) == TaiwanWallLayout.Layer.UPPER;
        var rail = TiltedWallLayout.compact(TaiwanWallLayout.stacks(size), WIDTH, HEIGHT);
        var position = rail.position(TaiwanWallLayout.stack(size, physicalSlot));
        return piece(tile, TaiwanWallLayout.side(size, physicalSlot), Area.WALL, physicalSlot,
            position.x, (upper ? 1.5 : .5) * DEPTH, position.z, rail.yaw(), true, true, TILE_SCALE);
    }
    public static List<Piece> fullWall(int size) { return List.copyOf(wall(Collections.nCopies(size, Tile.HIDDEN))); }

    private static List<Piece> wall(List<Integer> slots) {
        var result = new ArrayList<Piece>();
        for (int slot = 0; slot < slots.size(); slot++)
            if (slots.get(slot) != Tile.ABSENT) result.add(wallPiece(slots.size(), slot, slots.get(slot)));
        return result;
    }

    public static List<Piece> build(TaiwanView view) {
        var result = new ArrayList<>(wall(view.wall()));
        var occupied = new ArrayList<>(result.stream().map(p -> ChineseTableLayout.bounds(p.position(), p.yaw(), p.flat())).toList());
        occupied.add(new ChineseTableLayout.Bounds(Vec3.ZERO, 0, 2 * TableIndicator.HALF_WIDTH, 2 * TableIndicator.HALF_WIDTH));
        for (int seat = 0; seat < 4; seat++) {
            var player = view.seats().get(seat);
            var hand = seat == view.recipient() ? player.concealed() : Collections.nCopies(player.concealedCount(), Tile.HIDDEN);
            var indices = new ArrayList<>(java.util.stream.IntStream.range(0, hand.size()).boxed().toList());
            boolean drawn = player.drawn() != Tile.ABSENT && !hand.isEmpty();
            if (drawn && player.drawn() >= 0) {
                int original = hand.indexOf(player.drawn());
                indices.remove(Integer.valueOf(original));
                indices.add(original);
            }
            var melds = new ArrayList<MeldLayout>();
            for (var meld : player.melds()) melds.add(MeldLayout.of(meld, seat, top.skyeyefast.mchjong.engine.MahjongVariant.TAIWAN, false));
            var origins = ChineseTableLayout.melds(melds, seat, occupied);
            double handWidth = hand.size() * WIDTH + (drawn ? RiichiTableScene.DRAW_GAP : 0);
            // The longer Taiwan hand starts beyond the adjacent tilted wall's corner.
            double handLeft = Math.max(-.65, -(handWidth - WIDTH) / 2);
            for (int group = 0; group < origins.size(); group++) {
                var origin = origins.get(group);
                if (Math.abs(origin.z - HAND_Z) < HEIGHT)
                    handLeft = Math.max(handLeft, origin.x + melds.get(group).width() + .04 + WIDTH / 2);
            }
            for (int index = 0; index < hand.size(); index++) {
                boolean flat = false;
                int original = indices.get(index);
                result.add(piece(hand.get(original), seat, Area.HAND, original,
                    handLeft + (index * WIDTH + (drawn && index == hand.size() - 1 ? RiichiTableScene.DRAW_GAP : 0)),
                    (flat ? DEPTH : HEIGHT) / 2, HAND_Z, 0, flat, false, TILE_SCALE));
            }
            for (int group = 0; group < player.melds().size(); group++) {
                var layout = melds.get(group);
                for (int index = 0; index < layout.parts().size(); index++) {
                    var part = layout.parts().get(index);
                    result.add(piece(part.tile(), seat, Area.MELD, group * 4 + index, origins.get(group).x + part.x(),
                        DEPTH / 2, origins.get(group).z + part.z(), part.sideways() ? 90 : 0, true, part.back(), TILE_SCALE));
                }
            }
            for (var part : ChineseFlowerLayout.of(player.flowers().stream().map(top.skyeyefast.mchjong.engine.FlowerTile::id).toList()))
                result.add(piece(part.tile(), seat, Area.FLOWER, part.index(), .30 + part.x(),
                    DEPTH / 2, HAND_Z - HEIGHT - .02, 0, true, false, TILE_SCALE));
        }
        var rivers = view.seats().stream().map(TaiwanView.Seat::river).toList();
        occupied = new ArrayList<>(result.stream().map(p -> ChineseTableLayout.bounds(p.position(), p.yaw(), p.flat())).toList());
        occupied.add(new ChineseTableLayout.Bounds(Vec3.ZERO, 0, 2 * TableIndicator.HALF_WIDTH, 2 * TableIndicator.HALF_WIDTH));
        var positions = ChineseTableLayout.rivers(rivers.stream().map(List::size).toList(), occupied);
        for (int seat = 0; seat < 4; seat++) for (int index = 0; index < rivers.get(seat).size(); index++) {
            var part = rivers.get(seat).get(index);
            var position = positions.get(seat).get(index);
            result.add(piece(part, seat, Area.RIVER, index, position.x,
                DEPTH / 2, position.z, 0, true, false, TILE_SCALE));
        }
        return List.copyOf(result);
    }

    public static List<Piece> immersive(TaiwanView view) {
        return build(view).stream().filter(piece -> piece.area() != Area.WALL).toList();
    }
}
