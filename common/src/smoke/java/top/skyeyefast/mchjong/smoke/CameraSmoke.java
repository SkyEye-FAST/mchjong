package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.mixin.GameRendererAccessor;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Real renderer/picking evidence at narrow, normal and wide FOV, plus native third-person. */
final class CameraSmoke {
    private int sample = -1, ticks, originalFov;
    private net.minecraft.client.CameraType originalType;
    private boolean originalActive;
    private int originalWidth, originalHeight, originalScale;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        if (sample < 0) {
            originalFov = client.options.fov().get();
            originalType = client.options.getCameraType();
            originalActive = client.isWindowActive();
            originalWidth = client.getWindow().getScreenWidth();
            originalHeight = client.getWindow().getScreenHeight();
            originalScale = client.options.guiScale().get();
            client.setWindowActive(true);
            readabilityFixture(table);
            sample = 0;
            show(client, table);
        }
        if (++ticks < 24) return false;
        var camera = client.gameRenderer.getMainCamera();
        if (sample < 6) {
            var seat = (SeatEntity) client.player.getVehicle();
            var pose = TableSettings.get().camera();
            double fov = ((GameRendererAccessor) client.gameRenderer).mchjong$getFov(camera, 1, true);
            double normal = TableSettings.get().cameraFov(client.options.fov().get(),
                (double) client.getWindow().getWidth() / client.getWindow().getHeight());
            require(Math.abs(fov - pose.fov(normal)) < 1e-5, "Rendered inspect FOV differs from picking FOV");
            require(camera.getPosition().distanceTo(client.player.getEyePosition()) < 1e-6,
                "Native picking does not share the rendered inspect eye");
            require(camera.getPosition().distanceTo(TableSettings.get().cameraPosition(seat)) < 1e-6,
                "Overlay picking does not share the inspect eye");
            require(sample % 2 == 0 ? Math.abs(fov - normal) < 1e-5 : fov < normal - 10,
                "Inspect transition did not reach its expected FOV");
            Screenshot.grab(output.toFile(), "59-camera-fov-" + client.options.fov().get()
                + (sample % 2 == 0 ? "-normal.png" : "-inspect.png"), client.getMainRenderTarget(), ignored -> {});
        } else if (sample == 6) {
            require(camera.isDetached(), "Third-person camera was captured by seated controls");
            var pose = TableSettings.get().camera();
            double distance = pose.distance();
            client.screen.mouseScrolled(300, 100, 0, 3);
            require(pose.distance() == distance, "Third-person wheel changed the seated camera");
            client.player.turn(20, 10);
            float yaw = client.player.getYRot(), pitch = client.player.getXRot();
            ((TableScreen) client.screen).resetView();
            require(client.player.getYRot() == yaw && client.player.getXRot() == pitch,
                "Resetting the seated pose overwrote native third-person rotation");
            Screenshot.grab(output.toFile(), "59-camera-third-person.png", client.getMainRenderTarget(), ignored -> {});
        } else {
            require(((TableScreen) client.screen).immersive(), "Dense readability fixture lost immersive view");
            Screenshot.grab(output.toFile(), "59-readable-dense-" + client.screen.width + "x" + client.screen.height + ".png",
                client.getMainRenderTarget(), ignored -> {});
        }
        if (++sample == 9) {
            client.options.fov().set(originalFov);
            client.options.setCameraType(originalType);
            client.setWindowActive(originalActive);
            client.getWindow().setWindowed(originalWidth, originalHeight);
            client.options.guiScale().set(originalScale);
            client.resizeDisplay();
            ((TableScreen) client.screen).resetView();
            return true;
        }
        show(client, table);
        return false;
    }

    private void show(Minecraft client, MahjongTableBlockEntity table) {
        client.options.setCameraType(sample == 6 ? net.minecraft.client.CameraType.THIRD_PERSON_BACK : net.minecraft.client.CameraType.FIRST_PERSON);
        client.options.fov().set(new int[]{50, 70, 110}[Math.min(2, sample / 2)]);
        if (sample >= 7) {
            client.getWindow().setWindowed(sample == 7 ? 960 : 1280, sample == 7 ? 600 : 800);
            client.options.guiScale().set(2);
            client.resizeDisplay();
        }
        var screen = new TableScreen(table.getBlockPos());
        client.setScreen(screen);
        screen.resetView();
        if (sample >= 7) screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
        else if (sample % 2 == 1) screen.keyPressed(GLFW.GLFW_KEY_C, 0, 0);
        long window = client.getWindow().getWindow();
        var cursor = GLFW.glfwSetCursorPosCallback(window, null);
        if (cursor == null) throw new IllegalStateException("Missing native cursor callback");
        try { cursor.invoke(window, 4, 4); }
        finally { GLFW.glfwSetCursorPosCallback(window, cursor); }
        ticks = 0;
    }

    private static void readabilityFixture(MahjongTableBlockEntity table) {
        var base = table.clientView();
        var seats = new java.util.ArrayList<top.skyeyefast.mchjong.engine.TableView.Seat>();
        int first = 40;
        for (int seat = 0; seat < 4; seat++) {
            int owner = seat;
            int count = seat == 1 ? 4 : seat == 2 ? 2 : 0;
            var melds = java.util.stream.IntStream.range(0, count).mapToObj(i -> {
                int tile = owner == 1 ? 14 + 4 * i : 30 + 3 * i;
                var type = owner == 1 ? top.skyeyefast.mchjong.engine.Meld.Type.OPEN_KAN : top.skyeyefast.mchjong.engine.Meld.Type.PON;
                return new top.skyeyefast.mchjong.engine.Meld(type,
                    java.util.stream.IntStream.range(tile, tile + (owner == 1 ? 4 : 3)).boxed().toList(), 0, tile);
            }).toList();
            int riverSize = new int[]{24, 12, 20, 12}[seat];
            int start = first;
            var river = java.util.stream.IntStream.range(first, first + riverSize)
                .mapToObj(tile -> new top.skyeyefast.mchjong.engine.Discard(tile, tile == start + 2, false, false)).toList();
            first += riverSize;
            var hand = seat == 0 ? java.util.stream.IntStream.range(0, 14).boxed().toList()
                : java.util.Collections.nCopies(13 - count * 3, top.skyeyefast.mchjong.engine.Tile.HIDDEN);
            seats.add(new top.skyeyefast.mchjong.engine.TableView.Seat(false, "Player " + (seat + 1), true, false, false,
                25000, hand, seat == 0 ? 13 : -1, melds, river, java.util.List.of(), false, false, false));
        }
        table.acceptView(new top.skyeyefast.mchjong.engine.TableView(base.tableId(), base.revision() + 1, base.decision(),
            base.handNumber(), top.skyeyefast.mchjong.engine.RuleSet.MAHJONG_SOUL_4.config(),
            top.skyeyefast.mchjong.engine.Game.Phase.TURN, 0, 0, 0, 0, 0, 0, 2, base.wallBreak(), java.util.List.of(), null,
            seats, java.util.List.of(new top.skyeyefast.mchjong.engine.Action(top.skyeyefast.mchjong.engine.Action.Type.RIICHI, 0),
                new top.skyeyefast.mchjong.engine.Action(top.skyeyefast.mchjong.engine.Action.Type.PASS)),
            java.util.List.of(), "playing", java.util.List.of(), java.util.List.of(), java.util.List.of(), base.timeControl(),
            java.util.List.of(), java.util.List.of(), top.skyeyefast.mchjong.engine.HandVisibility.SELF, null, null, base.autoPlay(), false, 1));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
