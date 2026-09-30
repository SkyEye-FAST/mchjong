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
    public static final float TILE_SCALE = .82f;
    public static final double WIDTH = TileMesh.WIDTH * (double) TILE_SCALE;
    public static final double HEIGHT = TileMesh.HEIGHT * (double) TILE_SCALE;
    public static final double DEPTH = TileMesh.DEPTH * (double) TILE_SCALE;
    public static final double HAND_Z = 1.15;
    public static final double WALL_Z = .76;
    public static final double RIVER_Z = .34;
    private static final double MELD_RIGHT = 1.02;
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

    public static List<Piece> fullWall() {
        return wall(java.util.Collections.nCopies(108, Tile.HIDDEN));
    }

    private static List<Piece> wall(List<Integer> slots) {
        var pieces = new ArrayList<Piece>();
        for (int seat = 0; seat < 4; seat++) for (int column = 0; column < SichuanWallLayout.stacks(seat); column++)
            for (int layer = 0; layer < 2; layer++) {
                int slot = SichuanWallLayout.slot(seat, column, layer);
                if (slots.get(slot) != Tile.ABSENT) pieces.add(piece(slots.get(slot), seat, Area.WALL, slot,
                    ((SichuanWallLayout.stacks(seat) - 1) / 2.0 - column) * WIDTH,
                    (layer == 0 ? 1.5 : .5) * DEPTH, WALL_Z, 0, true, true, TILE_SCALE));
            }
        return List.copyOf(pieces);
    }

    public static List<Piece> build(SichuanView view) {
        boolean ended = view.phase() == SichuanGame.Phase.HAND_END || view.phase() == SichuanGame.Phase.MATCH_END;
        return build(view.wall().slots(), view.seats(), ended);
    }

    static List<Piece> replay(top.skyeyefast.mchjong.engine.SichuanReplayPlayback.Frame frame) {
        return build(frame.state().wall().slots(), frame.seats(), true).stream().filter(piece -> piece.area() != Area.WALL).toList();
    }

    private static List<Piece> build(List<Integer> slots, List<SichuanView.Seat> seats, boolean ended) {
        var pieces = new ArrayList<>(wall(slots));
        for (int seat = 0; seat < 4; seat++) {
            var player = seats.get(seat);
            double meldWidth = player.melds().stream().mapToDouble(meld -> meld.tiles().size() * WIDTH + DEPTH / 4).sum();
            boolean drawn = player.drawn() != Tile.ABSENT && !player.hand().isEmpty();
            double handWidth = player.hand().size() * WIDTH + (drawn ? DEPTH / 2 : 0);
            double handLeft = -(handWidth - WIDTH) / 2;
            if (meldWidth > 0) handLeft = Math.min(handLeft, MELD_RIGHT - meldWidth - DEPTH / 2 - handWidth + WIDTH / 2);
            var indices = new ArrayList<>(java.util.stream.IntStream.range(0, player.hand().size()).boxed().toList());
            int drawnIndex = player.drawn() >= 0 ? player.hand().indexOf(player.drawn()) : -1;
            if (drawnIndex >= 0) { indices.remove(Integer.valueOf(drawnIndex)); indices.add(drawnIndex); }
            for (int index = 0; index < indices.size(); index++) {
                int original = indices.get(index);
                pieces.add(piece(player.hand().get(original), seat, Area.HAND, original,
                    handLeft + index * WIDTH + (drawn && index == indices.size() - 1 ? DEPTH / 2 : 0),
                    (ended ? DEPTH : HEIGHT) / 2, HAND_Z, 0, ended, false, TILE_SCALE));
            }
            int visible = 0;
            for (int history = 0; history < player.river().size(); history++) {
                var discard = player.river().get(history);
                if (discard.claimed()) continue;
                pieces.add(piece(discard.tile(), seat, Area.RIVER, history, (visible % 6 - 2.5) * WIDTH,
                    DEPTH / 2, RIVER_Z + visible / 6 * HEIGHT, 0, true, false, TILE_SCALE));
                visible++;
            }
            double right = MELD_RIGHT;
            for (int group = 0; group < player.melds().size(); group++) {
                var meld = player.melds().get(group);
                for (int index = 0; index < meld.tiles().size(); index++) {
                    boolean back = meld.closed() && !ended && (index == 0 || index == 3);
                    pieces.add(piece(meld.tiles().get(index), seat, Area.MELD, group * 4 + index,
                        right - (meld.tiles().size() - index - .5) * WIDTH, DEPTH / 2, HAND_Z,
                        0, true, back, TILE_SCALE));
                }
                right -= meld.tiles().size() * WIDTH + DEPTH / 4;
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
        if (player.won()) label.append(" · ").append(Component.translatable("sichuan.mchjong.won"));
        return label;
    }
}
