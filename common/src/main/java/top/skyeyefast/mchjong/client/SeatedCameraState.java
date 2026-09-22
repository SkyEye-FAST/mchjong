package top.skyeyefast.mchjong.client;

import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.TableGeometry;

/** One seat-local camera pose, shared by rendering, projection and picking. */
public final class SeatedCameraState {
    private double distance, height, targetX, targetZ;
    private float yaw, pitch;
    private double inspect, previousInspect, renderedInspect;
    private static final double INSPECT_TRAVEL = .45;
    private static final double INSPECT_FOV = .90;

    public SeatedCameraState(double distance, double height) { reset(distance, height); }

    public void reset(double distance, double height) {
        configure(distance, height);
        targetX = targetZ = 0;
        yaw = 0;
        pitch = (float) Math.toDegrees(Math.atan2(height - TableGeometry.FELT_Y,
            distance - TableSettings.CAMERA_TARGET_Z));
        inspect = previousInspect = renderedInspect = 0;
    }

    /** Changing the preferred eye position does not overwrite the current look direction. */
    public void configure(double distance, double height) {
        this.distance = net.minecraft.util.Mth.clamp(distance, TableSettings.MIN_CAMERA_DISTANCE, TableSettings.MAX_CAMERA_DISTANCE);
        this.height = net.minecraft.util.Mth.clamp(height, TableSettings.MIN_CAMERA_HEIGHT, TableSettings.MAX_CAMERA_HEIGHT);
    }

    public double sensitivity() { return 1 - .6 * inspect; }
    public void look(double horizontal, double vertical) {
        yaw = (float) Math.IEEEremainder(yaw + horizontal * sensitivity(), 360);
        pitch = (float) net.minecraft.util.Mth.clamp(pitch + vertical * sensitivity(), -25, 85);
    }
    public void pan(double x, double z) {
        targetX = net.minecraft.util.Mth.clamp(targetX + x * sensitivity(), -.55, .55);
        targetZ = net.minecraft.util.Mth.clamp(targetZ + z * sensitivity(), -.55, .55);
    }
    public void scroll(double steps) {
        distance = net.minecraft.util.Mth.clamp(distance - steps * .12 * sensitivity(),
            TableSettings.MIN_CAMERA_DISTANCE, TableSettings.MAX_CAMERA_DISTANCE);
    }
    public void raise(double steps) {
        height = net.minecraft.util.Mth.clamp(height + steps * .05 * sensitivity(),
            TableSettings.MIN_CAMERA_HEIGHT, TableSettings.MAX_CAMERA_HEIGHT);
    }
    public void tick(boolean inspecting) {
        previousInspect = inspect;
        inspect += ((inspecting ? 1 : 0) - inspect) * .35;
        if (Math.abs(inspect - (inspecting ? 1 : 0)) < .001) inspect = inspecting ? 1 : 0;
    }
    public void sample(float partialTick) {
        renderedInspect = progress(partialTick);
    }
    private double progress(float partialTick) {
        return previousInspect + (inspect - previousInspect) * net.minecraft.util.Mth.clamp(partialTick, 0, 1);
    }
    public float yaw(int seat) { return TableGeometry.yaw(seat) + yaw; }
    public float pitch() { return pitch; }
    public double distance() { return distance; }
    public Vec3 localEye() {
        return localEye(renderedInspect);
    }
    private Vec3 localEye(double progress) {
        // Inspect moves toward the table, independently of the current free-look direction.
        double travel = INSPECT_TRAVEL * progress;
        return new Vec3(targetX, height - travel * .35, distance + targetZ - travel);
    }
    public Vec3 eye(int seat) {
        Vec3 eye = localEye();
        return TableGeometry.orient(eye.x, eye.y, eye.z, seat);
    }
    public Vec3 eye(int seat, float partialTick) {
        Vec3 eye = localEye(progress(partialTick));
        return TableGeometry.orient(eye.x, eye.y, eye.z, seat);
    }
    public double fov(double normal) {
        return Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(normal) / 2)
            / (1 + INSPECT_FOV * renderedInspect)));
    }
}
