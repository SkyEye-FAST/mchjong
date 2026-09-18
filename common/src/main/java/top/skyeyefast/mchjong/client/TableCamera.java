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
            && client.player != null && client.player.getVehicle() instanceof SeatEntity seat
            && screen.tablePos().equals(seat.tablePos());
    }

    public static Vec3 position(SeatEntity seat) {
        return overhead()
            ? TableGeometry.world(seat.tablePos(), TableGeometry.orient(0, TableGeometry.FELT_Y + OVERHEAD_RISE, -.15, seat.seat()))
            : TableSettings.get().cameraPosition(seat);
    }

    public static double fov(double requested, double aspectRatio) {
        if (!overhead()) return TableSettings.get().cameraFov(requested, aspectRatio);
        return overheadFov(aspectRatio);
    }

    static double overheadFov(double aspectRatio) {
        double depth = OVERHEAD_RISE - (0.081 + TileMesh.HEIGHT / 2.0) * TableScene.TILE_SCALE;
        return Math.toDegrees(2 * Math.atan(TableGeometry.FELT_HALF_WIDTH / (depth * Math.min(.82, aspectRatio * .95))));
    }
}
