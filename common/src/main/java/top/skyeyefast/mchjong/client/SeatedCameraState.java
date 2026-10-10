package top.skyeyefast.mchjong.client;

import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.TableGeometry;

/** One seat-local camera pose, shared by rendering, projection and picking. */
public final class SeatedCameraState {
    private double distance, height, targetX, targetZ;
    private float yaw, pitch;

    public SeatedCameraState(double distance, double height) { reset(distance, height); }

    public void reset(double distance, double height) {
        configure(distance, height);
        targetX = targetZ = 0;
        yaw = 0;
        pitch = (float) Math.toDegrees(Math.atan2(height - TableGeometry.FELT_Y,
            distance - TableSettings.CAMERA_TARGET_Z));
    }

    /** Changing the preferred eye position does not overwrite the current look direction. */
    public void configure(double distance, double height) {
        this.distance = net.minecraft.util.Mth.clamp(distance, TableSettings.MIN_CAMERA_DISTANCE, TableSettings.MAX_CAMERA_DISTANCE);
        this.height = net.minecraft.util.Mth.clamp(height, TableSettings.MIN_CAMERA_HEIGHT, TableSettings.MAX_CAMERA_HEIGHT);
    }

    public void look(double horizontal, double vertical) {
        yaw = (float) Math.IEEEremainder(yaw + horizontal, 360);
        pitch = (float) net.minecraft.util.Mth.clamp(pitch + vertical, -25, 85);
    }
    public void pan(double x, double z) {
        targetX = net.minecraft.util.Mth.clamp(targetX + x, -.55, .55);
        targetZ = net.minecraft.util.Mth.clamp(targetZ + z, -.55, .55);
    }
    public void scroll(double steps) {
        distance = net.minecraft.util.Mth.clamp(distance - steps * .12,
            TableSettings.MIN_CAMERA_DISTANCE, TableSettings.MAX_CAMERA_DISTANCE);
    }
    public void raise(double steps) {
        height = net.minecraft.util.Mth.clamp(height + steps * .05,
            TableSettings.MIN_CAMERA_HEIGHT, TableSettings.MAX_CAMERA_HEIGHT);
    }
    public float yaw(int seat) { return TableGeometry.yaw(seat) + yaw; }
    public float pitch() { return pitch; }
    public double distance() { return distance; }
    public Vec3 localEye() {
        return new Vec3(targetX, height, distance + targetZ);
    }
    public Vec3 eye(int seat) {
        Vec3 eye = localEye();
        return TableGeometry.orient(eye.x, eye.y, eye.z, seat);
    }
}
