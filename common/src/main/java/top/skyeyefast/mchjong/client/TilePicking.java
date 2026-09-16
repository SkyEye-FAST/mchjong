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
        var piece = frame.piece();
        var inverse = new Matrix4f().translation((float) piece.position().x,
            (float) (piece.position().y + (lifted ? 0.035 : 0)), (float) piece.position().z)
            .rotateY((float) Math.toRadians(piece.yaw())).rotateX((float) Math.toRadians(frame.pitch()))
            .scale(TableScene.TILE_SCALE).invert();
        Vec3 start = local(inverse, origin);
        Vec3 end = local(inverse, origin.add(direction.normalize().scale(32)));
        if (TILE.contains(start)) return 0;
        return TILE.clip(start, end).map(hit -> start.distanceToSqr(hit) * TableScene.TILE_SCALE * TableScene.TILE_SCALE)
            .orElse(Double.POSITIVE_INFINITY);
    }

    private static Vec3 local(Matrix4f inverse, Vec3 point) {
        var transformed = inverse.transformPosition(new Vector3f((float) point.x, (float) point.y, (float) point.z));
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }
}
