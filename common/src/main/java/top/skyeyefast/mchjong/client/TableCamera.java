package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.SeatEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** A table-facing overhead camera; closing the controls restores the seated eye. */
public final class TableCamera {
    static final double OVERHEAD_RISE = 6;

    private TableCamera() {}

    public static boolean overhead() {
        var client = Minecraft.getInstance();
        var screen = TableScreen.active(client.screen);
        return screen != null && screen.overhead() && client.options.getCameraType().isFirstPerson()
            && screen.view() != null && screen.view().viewerSeat() >= 0
            && client.player != null && client.player.getVehicle() instanceof SeatEntity seat
            && screen.tablePos().equals(seat.tablePos());
    }

    public static Vec3 position(SeatEntity seat) {
        var window = Minecraft.getInstance().getWindow();
        return overhead()
            ? TableGeometry.world(seat.tablePos(), TableGeometry.orient(0, TableGeometry.FELT_Y + OVERHEAD_RISE,
                overheadOffset((double) window.getWidth() / window.getHeight(), window.getGuiScaledHeight()), seat.seat()))
            : TableSettings.get().cameraPosition(seat);
    }

    public static double fov(double requested, double aspectRatio) {
        if (!overhead()) return TableSettings.get().cameraFov(requested, aspectRatio);
        return overheadFov(aspectRatio, Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }

    private static double overheadHalfSpan(double aspectRatio, int height) {
        int width = Math.max(1, (int) Math.round(height * aspectRatio));
        double availableHeight = Math.max(1, TableHand.top(width, height) - 8 - 56);
        return TableGeometry.OUTER_HALF_WIDTH * Math.max(height / availableHeight, height / Math.max(1.0, width - 16));
    }

    static double overheadOffset(double aspectRatio, int height) {
        int width = Math.max(1, (int) Math.round(height * aspectRatio));
        double center = (56 + TableHand.top(width, height) - 8) / 2.0;
        return (1 - 2 * center / height) * overheadHalfSpan(aspectRatio, height);
    }

    static double overheadFov(double aspectRatio, int height) {
        // Fit the complete table between the HUD and private hand. A higher eye reduces perspective distortion.
        return Math.toDegrees(2 * Math.atan(overheadHalfSpan(aspectRatio, height) / OVERHEAD_RISE));
    }
}
