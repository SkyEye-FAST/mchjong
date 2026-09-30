package top.skyeyefast.mchjong.client;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Pick the rendered oriented tile box, including its animated pitch and selection lift. */
public final class TilePicking {
    private static final AABB TILE = new AABB(-TileMesh.WIDTH / 2.0, -TileMesh.HEIGHT / 2.0, -TileMesh.DEPTH / 2.0,
        TileMesh.WIDTH / 2.0, TileMesh.HEIGHT / 2.0, TileMesh.DEPTH / 2.0);
    private TilePicking() {}

    public static double distanceSquared(TableAnimation.Frame frame, Vec3 origin, Vec3 direction, boolean lifted) {
        return distanceSquared(frame, origin, direction, lifted, 0);
    }

    public static double distanceSquared(TableAnimation.Frame frame, Vec3 origin, Vec3 direction, boolean lifted, double padding) {
        var piece = frame.piece();
        return distanceSquared(piece.position(), piece.yaw(), frame.pitch(), TableScene.TILE_SCALE, origin, direction, lifted, padding);
    }

    public static double distanceSquared(McrTableScene.Piece piece, Vec3 origin, Vec3 direction, boolean lifted) {
        return distanceSquared(piece.position(), piece.yaw(), piece.flat() ? piece.back() ? 90 : -90 : 0,
            piece.scale(), origin, direction, lifted, 0);
    }

    private static double distanceSquared(Vec3 position, float yaw, float pitch, float scale,
                                          Vec3 origin, Vec3 direction, boolean lifted, double padding) {
        var inverse = new Matrix4f().translation((float) position.x,
            (float) (position.y + (lifted ? 0.035 : 0)), (float) position.z)
            .rotateY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(pitch))
            .scale(scale).invert();
        Vec3 start = local(inverse, origin);
        Vec3 end = local(inverse, origin.add(direction.normalize().scale(32)));
        AABB target = padding > 0 ? TILE.inflate(padding) : TILE;
        if (target.contains(start)) return 0;
        return target.clip(start, end).map(hit -> start.distanceToSqr(hit) * scale * scale)
            .orElse(Double.POSITIVE_INFINITY);
    }

    private static Vec3 local(Matrix4f inverse, Vec3 point) {
        var transformed = inverse.transformPosition(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }
}
