package top.skyeyefast.mchjong.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Intersects rendered tile boxes, including animated pitch and selection lift.
 * Poses and ray origins use table-relative block coordinates; directions need not be normalized.
 * Distances are squared blocks, zero inside the box and positive infinity for no hit within 32 blocks.
 */
public final class TilePicking {
    private TilePicking() {}

    public static double distanceSquared(RiichiAnimation.Frame frame, Vec3 origin, Vec3 direction, boolean lifted) {
        return distanceSquared(frame, origin, direction, lifted, 0);
    }

    public static double distanceSquared(RiichiAnimation.Frame frame, Vec3 origin, Vec3 direction, boolean lifted, double padding) {
        var piece = frame.piece();
        return distanceSquared(piece.position(), piece.yaw(), frame.pitch(), RiichiTableScene.DIMENSIONS, origin, direction, lifted, padding);
    }

    public static double distanceSquared(McrTableScene.Piece piece, Vec3 origin, Vec3 direction, boolean lifted) {
        return distanceSquared(piece.position(), piece.yaw(), piece.flat() ? piece.back() ? 90 : -90 : 0,
            piece.dimensions(), origin, direction, lifted, 0);
    }

    public static double distanceSquared(SichuanTableScene.Piece piece, Vec3 origin, Vec3 direction, boolean lifted) {
        return distanceSquared(piece.position(), piece.yaw(), piece.flat() ? piece.back() ? 90 : -90 : 0,
            piece.dimensions(), origin, direction, lifted, 0);
    }

    static double distanceSquared(TableAnimation.Pose pose, TileDimensions dimensions, Vec3 origin, Vec3 direction, boolean lifted) {
        return distanceSquared(pose.position(), pose.yaw(), pose.pitch(), dimensions, origin, direction, lifted, 0);
    }
    private static double distanceSquared(Vec3 position, float yaw, float pitch, TileDimensions dimensions,
                                          Vec3 origin, Vec3 direction, boolean lifted, double padding) {
        var inverse = new Matrix4f().translation((float) position.x,
            (float) (position.y + (lifted ? 0.035 : 0)), (float) position.z)
            .rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch))
            .invert();
        Vec3 start = local(inverse, origin);
        Vec3 end = local(inverse, origin.add(direction.normalize().scale(32)));
        AABB target = dimensions.bounds().inflate(padding);
        if (target.contains(start)) return 0;
        return target.clip(start, end).map(hit -> start.distanceToSqr(hit))
            .orElse(Double.POSITIVE_INFINITY);
    }

    private static Vec3 local(Matrix4f inverse, Vec3 point) {
        var transformed = inverse.transformPosition(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }
}
