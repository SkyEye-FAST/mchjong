package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Physical action targets derived exclusively from the recipient's public snapshot. */
public final class RiichiHandling {
    private RiichiHandling() {}

    public static boolean physical(RiichiView view, RiichiAction action) {
        if (view.handling() == null) return false;
        return switch (action.type()) {
            case SHUFFLE, BUILD_WALL, TAKE_PACKET, DRAW, NEXT -> true;
            default -> false;
        };
    }

    public static int action(RiichiView view) {
        if (view == null || view.viewerSeat() < 0 || view.exitVote() != null) return -1;
        for (int i = 0; i < view.actions().size(); i++) if (physical(view, view.actions().get(i))) return i;
        return -1;
    }

    public static boolean source(RiichiView view, RiichiTableScene.Piece piece) {
        int index = action(view);
        if (index < 0 || piece == null) return false;
        return switch (view.actions().get(index).type()) {
            case SHUFFLE -> piece.area() == RiichiTableScene.Area.LOOSE;
            case BUILD_WALL -> piece.area() == RiichiTableScene.Area.LOOSE && piece.seat() == view.viewerSeat();
            case TAKE_PACKET, DRAW -> piece.area() == RiichiTableScene.Area.WALL
                && piece.index() >= view.handling().sourceSlot()
                && piece.index() < view.handling().sourceSlot() + view.handling().packetSize() && piece.tile() < 0;
            case NEXT -> piece.seat() == view.viewerSeat() && piece.area() != RiichiTableScene.Area.WALL;
            default -> false;
        };
    }

    public static RiichiTableScene.Piece source(RiichiView view, List<RiichiTableScene.Piece> scene) {
        return scene.stream().filter(piece -> source(view, piece))
            .max(java.util.Comparator.comparingDouble(piece -> piece.position().y)).orElse(null);
    }

    /** Use the camera-facing long edge so the next tall stack cannot hide a lower single tile. */
    public static Vec3 grip(RiichiTableScene.Piece piece, Vec3 eye) {
        double height = (piece.flat() ? TileDimensions.SMALL.depth() : TileDimensions.SMALL.height());
        double depth = (piece.flat() ? TileDimensions.SMALL.height() : TileDimensions.SMALL.depth());
        double yaw = Math.toRadians(piece.yaw());
        Vec3 normal = new Vec3(Math.sin(yaw), 0, Math.cos(yaw));
        double edge = Math.copySign(depth / 2 - .003, eye.subtract(piece.position()).dot(normal));
        return piece.position().add(normal.scale(edge)).add(0, height / 2 - .001, 0);
    }

    public static Vec3 destination(RiichiView view) {
        int index = action(view);
        if (index < 0) return Vec3.ZERO;
        double z = switch (view.actions().get(index).type()) {
            case BUILD_WALL -> RiichiTableScene.WALL_Z;
            case TAKE_PACKET, DRAW -> RiichiTableScene.HAND_Z;
            default -> 0;
        };
        return TableGeometry.orient(0, TableGeometry.FELT_Y, z, view.viewerSeat());
    }

    public static boolean completes(RiichiView view, Vec3 start, Vec3 end) {
        int index = action(view);
        if (index < 0 || start == null || end == null || !Double.isFinite(start.x) || !Double.isFinite(start.z)
            || !Double.isFinite(end.x) || !Double.isFinite(end.z) || start.distanceToSqr(end) < .0144) return false;
        Vec3 local = TableGeometry.orient(end.x, end.y, end.z, (4 - view.viewerSeat()) % 4);
        RiichiAction.Type type = view.actions().get(index).type();
        if (type == RiichiAction.Type.SHUFFLE)
            return Math.abs(end.x) < .8 && Math.abs(end.z) < .8 && start.distanceToSqr(end) >= .09;
        if (type == RiichiAction.Type.NEXT) return Math.abs(end.x) < .65 && Math.abs(end.z) < .65;
        return Math.abs(local.x) <= 1.05 && Math.abs(local.z - destinationLocalZ(type)) <= .30;
    }

    private static double destinationLocalZ(RiichiAction.Type type) {
        return type == RiichiAction.Type.BUILD_WALL ? RiichiTableScene.WALL_Z : RiichiTableScene.HAND_Z;
    }

    public static String help(RiichiView view) {
        return switch (view.phase()) {
            case SHUFFLE -> "handling.mchjong.shuffle";
            case BUILD_WALL -> "handling.mchjong.wall";
            case DEAL, DRAW -> "handling.mchjong.take";
            case HAND_END, MATCH_END -> "handling.mchjong.collect";
            default -> "handling.mchjong.wait";
        };
    }
}
