package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Composed by each rule screen; owns presentation mode and seated camera input only. */
final class TableViewController {
    private boolean immersive, dragging;
    private double dragDistance;

    boolean immersive() { return immersive; }
    void immersive(boolean value) {
        if (immersive != value) { immersive = value; clearInput(); }
    }
    void toggle() { immersive(!immersive); }
    TableCanvas canvas(int width, int height) { return new TableCanvas(width, height, immersive); }

    boolean cameraEnabled() {
        var client = Minecraft.getInstance();
        return !immersive && client.player != null && client.player.getVehicle() instanceof SeatEntity
            && client.options.getCameraType().isFirstPerson();
    }
    void clearInput() { dragging = false; }

    private void syncCamera() {
        var client = Minecraft.getInstance();
        if (client.player != null && client.player.getVehicle() instanceof SeatEntity seat) SeatedCamera.sync(seat);
    }

    void reset() {
        var client = Minecraft.getInstance();
        if (client.player == null) return;
        if (client.player.getVehicle() instanceof SeatEntity seat) SeatedCamera.state(seat);
        var settings = TableSettings.get();
        settings.camera().reset(settings.cameraDistance, settings.cameraHeight);
        clearInput();
        syncCamera();
    }

    void tick() {
        if (!Minecraft.getInstance().isWindowActive()) clearInput();
        if (cameraEnabled()) syncCamera();
    }

    void startDrag() { dragging = true; dragDistance = 0; }
    boolean releaseDrag(int button, Runnable click) {
        if (button != 1 || !dragging) return false;
        dragging = false;
        if (dragDistance < 4) click.run();
        return true;
    }

    boolean drag(int button, double dx, double dy, boolean shift) {
        if (button != 1 || !dragging) return false;
        double before = dragDistance;
        dragDistance += Math.abs(dx) + Math.abs(dy);
        if (!cameraEnabled() || dragDistance <= 4) return true;
        double fraction = before >= 4 ? 1 : (dragDistance - 4) / (dragDistance - before);
        var camera = TableSettings.get().camera();
        if (shift) camera.pan(-dx * fraction * .004, -dy * fraction * .004);
        else camera.look(dx * fraction * .35, dy * fraction * .35);
        syncCamera();
        return true;
    }

    boolean scroll(double vertical, boolean shift) {
        if (!cameraEnabled()) return false;
        if (shift) TableSettings.get().camera().raise(vertical);
        else TableSettings.get().camera().scroll(vertical);
        return true;
    }

}
