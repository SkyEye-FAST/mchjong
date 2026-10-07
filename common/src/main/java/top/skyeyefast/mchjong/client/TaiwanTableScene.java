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
    public static final TileDimensions DIMENSIONS = TileDimensions.SMALL;
    public static final double WIDTH = DIMENSIONS.width();
    public static final double HEIGHT = DIMENSIONS.height();
    public static final double DEPTH = DIMENSIONS.depth();
    public static final double HAND_Z = TableGeometry.FELT_HALF_WIDTH - HEIGHT / 2 - .015;
    public static final double MELD_LEFT = -TableGeometry.FELT_HALF_WIDTH + .015;
    public static final double RIVER_Z = TableIndicator.HALF_WIDTH + HEIGHT / 2 + .005;
    public static final double RIVER_X = WIDTH / 2;
    public enum Area { WALL, HAND, RIVER, MELD, FLOWER }
    public record Piece(int tile, int seat, Area area, int index, Vec3 position,
                        float yaw, boolean flat, boolean back) {
        public TileDimensions dimensions() { return DIMENSIONS; }
    }

    private TaiwanTableScene() {}

    private static Piece piece(int tile, int seat, Area area, int index, double x, double height, double z,
                               float yaw, boolean flat, boolean back) {
        return new Piece(tile, seat, area, index, TableGeometry.orient(x, TableGeometry.FELT_Y + height, z, seat),
            seat * 90 + yaw, flat, back);
    }

    public static Piece wallPiece(int size, int physicalSlot, int tile) {
        boolean upper = TaiwanWallLayout.layer(physicalSlot) == TaiwanWallLayout.Layer.UPPER;
        var rail = TiltedWallLayout.compact(TaiwanWallLayout.stacks(size), WIDTH, HEIGHT);
        var position = rail.position(TaiwanWallLayout.stack(size, physicalSlot));
        return piece(tile, TaiwanWallLayout.side(size, physicalSlot), Area.WALL, physicalSlot,
            position.x, (upper ? 1.5 : .5) * DEPTH, position.z, rail.yaw(), true, true);
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
        var occupied = new ArrayList<>(result.stream().map(p -> ChineseTableLayout.bounds(p.position(), p.yaw(), p.flat(), DIMENSIONS)).toList());
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
            var publicArea = ChineseTableLayout.publicArea(melds, player.flowers().size(), hand.size(), drawn, seat, occupied, DIMENSIONS);
            var origins = publicArea.melds();
            double handLeft = publicArea.handLeft();
            for (int index = 0; index < hand.size(); index++) {
                boolean flat = false;
                int original = indices.get(index);
                result.add(piece(hand.get(original), seat, Area.HAND, original,
                    handLeft + (index * WIDTH + (drawn && index == hand.size() - 1 ? RiichiTableScene.DRAW_GAP : 0)),
                    (flat ? DEPTH : HEIGHT) / 2, HAND_Z, 0, flat, false));
            }
            for (int group = 0; group < player.melds().size(); group++) {
                var layout = melds.get(group);
                for (int index = 0; index < layout.parts().size(); index++) {
                    var part = layout.parts().get(index);
                    result.add(piece(part.tile(), seat, Area.MELD, group * 4 + index, origins.get(group).x + part.x(),
                        DEPTH / 2, origins.get(group).z + part.z(), part.sideways() ? 90 : 0, true, part.back()));
                }
            }
            for (int index = 0; index < player.flowers().size(); index++) {
                var position = publicArea.flowers().get(index);
                result.add(piece(player.flowers().get(index).id(), seat, Area.FLOWER, index, position.x,
                    DEPTH / 2, position.z, 0, true, false));
            }
            result.stream().filter(piece -> piece.area() == Area.HAND).forEach(piece ->
                occupied.add(ChineseTableLayout.bounds(piece.position(), piece.yaw(), piece.flat(), DIMENSIONS)));
        }
        var rivers = view.seats().stream().map(TaiwanView.Seat::river).toList();
        var riverOccupied = new ArrayList<>(result.stream().map(p -> ChineseTableLayout.bounds(p.position(), p.yaw(), p.flat(), DIMENSIONS)).toList());
        riverOccupied.add(new ChineseTableLayout.Bounds(Vec3.ZERO, 0, 2 * TableIndicator.HALF_WIDTH, 2 * TableIndicator.HALF_WIDTH));
        var positions = ChineseTableLayout.rivers(rivers.stream().map(List::size).toList(), riverOccupied, DIMENSIONS);
        for (int seat = 0; seat < 4; seat++) for (int index = 0; index < rivers.get(seat).size(); index++) {
            var part = rivers.get(seat).get(index);
            var position = positions.get(seat).get(index);
            result.add(piece(part, seat, Area.RIVER, index, position.x,
                DEPTH / 2, position.z, 0, true, false));
        }
        return List.copyOf(result);
    }

    public static List<Piece> immersive(TaiwanView view) {
        return build(view).stream().filter(piece -> piece.area() != Area.WALL).toList();
    }
}
