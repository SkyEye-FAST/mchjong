package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.SichuanGame;
import top.skyeyefast.mchjong.engine.SichuanView;
import top.skyeyefast.mchjong.engine.SichuanWallLayout;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;

public final class SichuanTableScene {
    public static final float TILE_SCALE = 1.0f;
    public static final double WIDTH = TileMesh.WIDTH * (double) TILE_SCALE;
    public static final double HEIGHT = TileMesh.HEIGHT * (double) TILE_SCALE;
    public static final double DEPTH = TileMesh.DEPTH * (double) TILE_SCALE;
    public static final double HAND_Z = McrTableScene.HAND_Z;
    public static final double RIVER_Z = TableIndicator.HALF_WIDTH + HEIGHT / 2 + .005;
    public static final double MELD_LEFT = McrTableScene.MELD_LEFT;
    public static final double RIVER_X = McrTableScene.RIVER_X;
    public enum Area { WALL, HAND, RIVER, MELD }
    public record Piece(int tile, int seat, Area area, int index, Vec3 position,
                        float yaw, boolean flat, boolean back, float scale) {}
    public record Claim(int tile, int supplier, List<Integer> winners) {
        public Claim { winners = List.copyOf(winners); }
    }

    private SichuanTableScene() {}

    private static Piece piece(int tile, int seat, Area area, int index, double horizontal, double height,
                               double depth, float yaw, boolean flat, boolean back, float scale) {
        return new Piece(tile, seat, area, index, TableGeometry.orient(horizontal, TableGeometry.FELT_Y + height, depth, seat),
            seat * 90 + yaw, flat, back, scale);
    }

    public static List<Piece> fullWall(boolean eastWestLongWall) {
        return wall(java.util.Collections.nCopies(108, Tile.HIDDEN), eastWestLongWall);
    }

    private static List<Piece> wall(List<Integer> slots, boolean eastWestLongWall) {
        var pieces = new ArrayList<Piece>();
        for (int seat = 0; seat < 4; seat++) {
            int stacks = SichuanWallLayout.stacks(seat, eastWestLongWall);
            var wall = TiltedWallLayout.compact(stacks, WIDTH, HEIGHT);
            for (int column = 0; column < stacks; column++)
            for (int layer = 0; layer < 2; layer++) {
                int slot = SichuanWallLayout.slot(seat, column, layer, eastWestLongWall);
                var position = wall.position(column);
                if (slots.get(slot) != Tile.ABSENT) pieces.add(piece(slots.get(slot), seat, Area.WALL, slot,
                    position.x, (layer == 0 ? 1.5 : .5) * DEPTH, position.z, wall.yaw(), true, true, TILE_SCALE));
            }
        }
        return List.copyOf(pieces);
    }

    public static List<Piece> build(SichuanView view) {
        boolean ended = view.phase() == SichuanGame.Phase.HAND_END || view.phase() == SichuanGame.Phase.MATCH_END;
        return build(view.wall().slots(), view.seats(), ended, view.wall().eastWestLongWall());
    }

    static List<Piece> replay(top.skyeyefast.mchjong.engine.SichuanReplayPlayback.Frame frame) {
        return build(frame.state().wall().slots(), frame.seats(), true, frame.state().wall().eastWestLongWall()).stream().filter(piece -> piece.area() != Area.WALL).toList();
    }

    private static List<Piece> build(List<Integer> slots, List<SichuanView.Seat> seats, boolean ended, boolean eastWestLongWall) {
        var pieces = new ArrayList<>(wall(slots, eastWestLongWall));
        var occupied = new ArrayList<>(pieces.stream().map(p -> ChineseTableLayout.bounds(p.position(), p.yaw(), p.flat())).toList());
        occupied.add(new ChineseTableLayout.Bounds(Vec3.ZERO, 0, 2 * TableIndicator.HALF_WIDTH, 2 * TableIndicator.HALF_WIDTH));
        for (int seat = 0; seat < 4; seat++) {
            var player = seats.get(seat);
            int owner = seat;
            var layouts = player.melds().stream().map(meld -> MeldLayout.of(meld, owner,
                top.skyeyefast.mchjong.engine.MahjongVariant.SICHUAN, ended)).toList();
            var origins = ChineseTableLayout.melds(layouts, seat, occupied);
            boolean drawn = player.drawn() != Tile.ABSENT && !player.hand().isEmpty();
            double handWidth = player.hand().size() * WIDTH + (drawn ? RiichiTableScene.DRAW_GAP : 0);
            double handLeft = -(handWidth - WIDTH) / 2;
            for (int group = 0; group < origins.size(); group++) {
                var origin = origins.get(group);
                if (Math.abs(origin.z - HAND_Z) < HEIGHT)
                    handLeft = Math.max(handLeft, origin.x + layouts.get(group).width() + .04 + WIDTH / 2);
            }
            var indices = new ArrayList<>(java.util.stream.IntStream.range(0, player.hand().size()).boxed().toList());
            int drawnIndex = player.drawn() >= 0 ? player.hand().indexOf(player.drawn()) : -1;
            if (drawnIndex >= 0) { indices.remove(Integer.valueOf(drawnIndex)); indices.add(drawnIndex); }
            for (int index = 0; index < indices.size(); index++) {
                int original = indices.get(index);
                pieces.add(piece(player.hand().get(original), seat, Area.HAND, original,
                    handLeft + index * WIDTH + (drawn && index == indices.size() - 1 ? RiichiTableScene.DRAW_GAP : 0),
                    (ended ? DEPTH : HEIGHT) / 2, HAND_Z, 0, ended, false, TILE_SCALE));
            }
            for (int group = 0; group < player.melds().size(); group++) {
                var layout = layouts.get(group);
                for (int index = 0; index < layout.parts().size(); index++) {
                    var part = layout.parts().get(index);
                    pieces.add(piece(part.tile(), seat, Area.MELD, group * 4 + index,
                        origins.get(group).x + part.x(), DEPTH / 2, origins.get(group).z + part.z(),
                        part.sideways() ? 90 : 0, true, part.back(), TILE_SCALE));
                }
            }
        }
        var rivers = seats.stream().map(player -> player.river().stream().filter(discard -> !discard.claimed()).toList()).toList();
        occupied = new ArrayList<>(pieces.stream().map(p -> ChineseTableLayout.bounds(p.position(), p.yaw(), p.flat())).toList());
        occupied.add(new ChineseTableLayout.Bounds(Vec3.ZERO, 0, 2 * TableIndicator.HALF_WIDTH, 2 * TableIndicator.HALF_WIDTH));
        var positions = ChineseTableLayout.rivers(rivers.stream().map(List::size).toList(), occupied);
        for (int seat = 0; seat < 4; seat++) {
            int index = 0;
            for (int history = 0; history < seats.get(seat).river().size(); history++) {
                var discard = seats.get(seat).river().get(history);
                if (discard.claimed()) continue;
                var position = positions.get(seat).get(index++);
                pieces.add(piece(discard.tile(), seat, Area.RIVER, history, position.x,
                    DEPTH / 2, position.z, 0, true, false, TILE_SCALE));
            }
        }
        return List.copyOf(pieces);
    }

    public static List<Piece> immersive(SichuanView view) {
        return build(view).stream().filter(piece -> piece.area() != Area.WALL).toList();
    }

    public static List<Claim> claims(SichuanView view) {
        var claims = new ArrayList<Claim>();
        for (var win : view.winners()) if (!win.selfDraw()) {
            if (claims.stream().anyMatch(claim -> claim.tile() == win.tile() && claim.supplier() == win.supplier())) continue;
            claims.add(new Claim(win.tile(), win.supplier(), view.winners().stream()
                .filter(other -> !other.selfDraw() && other.tile() == win.tile() && other.supplier() == win.supplier())
                .map(SichuanView.Winner::seat).toList()));
        }
        return List.copyOf(claims);
    }

    public static Component status(SichuanView.Seat player) {
        var label = Component.translatable(player.voidSuit() < 0 ? "sichuan.mchjong.void_pending"
            : "sichuan.mchjong.suit." + player.voidSuit());
        if (player.won()) label = Component.translatable("ui.mchjong.annotation", label, Component.translatable("sichuan.mchjong.won"));
        return label;
    }
}
