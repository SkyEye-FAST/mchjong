package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Client lifecycle and native free-look bridge. Third-person stays with Minecraft. */
public final class SeatedCamera {
    private static SeatEntity currentSeat;
    private SeatedCamera() {}

    public static SeatedCameraState state(SeatEntity seat) {
        var settings = TableSettings.get();
        if (currentSeat != seat) {
            currentSeat = seat;
            settings.camera().reset(settings.cameraDistance, settings.cameraHeight);
            sync(seat);
        }
        return settings.camera();
    }

    public static void sync(SeatEntity seat) {
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || !client.options.getCameraType().isFirstPerson()) return;
        var pose = TableSettings.get().camera();
        player.setYRot(pose.yaw(seat.seat()));
        player.setXRot(pose.pitch());
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
    }

    public static void tick() {
        var client = Minecraft.getInstance();
        if (client.player == null || !(client.player.getVehicle() instanceof SeatEntity seat)) {
            currentSeat = null;
            return;
        }
        var pose = state(seat);
        boolean firstPerson = client.options.getCameraType().isFirstPerson() && client.isWindowActive();
        if (firstPerson) sync(seat);
        boolean inspect = firstPerson && (client.screen instanceof TableScreen table
            ? table.inspecting() : client.screen == null && TableKeys.INSPECT.isDown());
        pose.tick(inspect);
        if (client.screen == null && firstPerson) {
            while (TableKeys.RESET.consumeClick()) {
                var settings = TableSettings.get();
                pose.reset(settings.cameraDistance, settings.cameraHeight);
                sync(seat);
            }
        }
    }

    public static boolean turn(double dx, double dy) {
        var client = Minecraft.getInstance();
        if (client.player == null || !client.options.getCameraType().isFirstPerson()
                || !(client.player.getVehicle() instanceof SeatEntity seat)) return false;
        state(seat).look(dx * .15, dy * .15);
        sync(seat);
        return true;
    }
}
