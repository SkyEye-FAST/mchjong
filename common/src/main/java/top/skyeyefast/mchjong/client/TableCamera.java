package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.SeatEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** A table-facing overhead camera; closing the controls restores the seated eye. */
public final class TableCamera {
    private static final double OVERHEAD_RISE = 2.4;

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
        return overhead()
            ? TableGeometry.world(seat.tablePos(), TableGeometry.orient(0, TableGeometry.FELT_Y + OVERHEAD_RISE,
                .44, seat.seat()))
            : TableSettings.get().cameraPosition(seat);
    }

    public static double fov(double requested, double aspectRatio) {
        if (!overhead()) return TableSettings.get().cameraFov(requested, aspectRatio);
        return overheadFov(aspectRatio, Minecraft.getInstance().getWindow().getGuiScaledHeight());
    }

    private static double viewportRoom(int height) {
        return Math.clamp((height - 240) / 160.0, 0, 1);
    }

    static double overheadFov(double aspectRatio, int height) {
        double depth = OVERHEAD_RISE - (0.081 + TileMesh.HEIGHT / 2.0) * TableScene.TILE_SCALE;
        // Larger viewports can zoom further without moving the near meld rail into the private hand.
        double fill = .80 + .10 * viewportRoom(height);
        return Math.toDegrees(2 * Math.atan(TableGeometry.FELT_HALF_WIDTH / (depth * Math.min(fill, aspectRatio * .95))));
    }
}
