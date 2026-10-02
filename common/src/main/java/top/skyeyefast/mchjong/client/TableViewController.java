package top.skyeyefast.mchjong.client;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Composed by each rule screen; owns presentation mode and seated camera input only. */
final class TableViewController {
    private boolean immersive, inspecting, dragging;
    private double dragDistance;
    private final boolean[] lookKeys = new boolean[4];

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
    boolean inspecting() { return inspecting && cameraEnabled(); }
    void clearInput() { inspecting = dragging = false; Arrays.fill(lookKeys, false); }

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
        if (cameraEnabled()) {
            TableSettings.get().camera().look((lookKeys[1] ? 1 : 0) - (lookKeys[0] ? 1 : 0),
                (lookKeys[3] ? 1 : 0) - (lookKeys[2] ? 1 : 0));
            syncCamera();
        }
    }

    boolean keyPressed(int key, int scanCode, Runnable toggle, Runnable reset) {
        if (TableKeys.RESET.matches(key, scanCode)) { reset.run(); return true; }
        if (TableKeys.VIEW.matches(key, scanCode)) { toggle.run(); return true; }
        if (TableKeys.INSPECT.matches(key, scanCode) && cameraEnabled()) { inspecting = true; return true; }
        return false;
    }

    boolean lookPressed(int key) {
        int arrow = arrow(key);
        if (arrow < 0 || !cameraEnabled()) return false;
        lookKeys[arrow] = true;
        return true;
    }

    boolean keyReleased(int key, int scanCode) {
        if (TableKeys.INSPECT.matches(key, scanCode)) { inspecting = false; return true; }
        int arrow = arrow(key);
        if (arrow < 0) return false;
        lookKeys[arrow] = false;
        return true;
    }

    boolean mouseBinding(int button, Runnable toggle, Runnable reset) {
        if (TableKeys.INSPECT.matchesMouse(button) && cameraEnabled()) { inspecting = true; return true; }
        if (TableKeys.RESET.matchesMouse(button)) { reset.run(); return true; }
        if (TableKeys.VIEW.matchesMouse(button)) { toggle.run(); return true; }
        return false;
    }

    boolean releaseInspect(int button) {
        if (!TableKeys.INSPECT.matchesMouse(button)) return false;
        inspecting = false;
        return true;
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

    private static int arrow(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT -> 0;
            case GLFW.GLFW_KEY_RIGHT -> 1;
            case GLFW.GLFW_KEY_UP -> 2;
            case GLFW.GLFW_KEY_DOWN -> 3;
            default -> -1;
        };
    }
}
