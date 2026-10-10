package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Projects table-relative block coordinates to GUI pixels using the rendered world camera and FOV. */
record SeatedTableProjection(Vec3 origin, Vec3 forward, Vec3 right, double focal, int width, int height) {
    record Point(double x, double y, double scale) {}
    record Pointer(Vec3 origin, Vec3 ray) {}

    static SeatedTableProjection capture(BlockPos pos, int width, int height, float partialTick) {
        var client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera();
        double yaw = Math.toRadians(camera.yRot()), pitch = Math.toRadians(camera.xRot());
        var forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        var right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        double fov = camera.getFov();
        return new SeatedTableProjection(camera.position().subtract(TableGeometry.world(pos, Vec3.ZERO)),
            forward, right, height / (2 * Math.tan(Math.toRadians(fov) / 2)), width, height);
    }

    /** Returns null at or behind the near depth, measured in blocks along the camera's forward axis. */
    Point project(Vec3 relative, double near) {
        var delta = relative.subtract(origin);
        double depth = delta.dot(forward);
        if (depth <= near) return null;
        double scale = focal / depth;
        return new Point(width / 2.0 + delta.dot(right) * scale,
            height / 2.0 - delta.dot(right.cross(forward)) * scale, scale);
    }

    /** Builds a table-relative ray from GUI pixels measured from the top-left; the ray is not normalized. */
    Pointer pointer(double x, double y) {
        return new Pointer(origin, forward.add(right.scale((x - width / 2.0) / focal))
            .add(right.cross(forward).scale((height / 2.0 - y) / focal)));
    }
}
