package top.skyeyefast.mchjong.smoke;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.RiichiTableScene;
import top.skyeyefast.mchjong.client.RiichiTableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.RiichiDiscard;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Exercise selection and cancellation only: never send a fabricated action to the server. */
final class InputSmoke {
    private InputSmoke() {}

    static void pointer(Minecraft client, double x, double y) {
        long window = client.getWindow().getWindow();
        var cursor = GLFW.glfwSetCursorPosCallback(window, null);
        if (cursor == null) throw new IllegalStateException("Missing native cursor callback");
        try { cursor.invoke(window, x * client.getWindow().getScreenWidth() / client.screen.width,
            y * client.getWindow().getScreenHeight() / client.screen.height); }
        finally { GLFW.glfwSetCursorPosCallback(window, cursor); }
    }

    static void pointerWidget(Minecraft client, AbstractWidget widget) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        if (client.screen instanceof RiichiTableScreen table && table.immersive()) {
            double scale = Math.min(client.screen.width / (double) top.skyeyefast.mchjong.client.TableCanvas.WIDTH,
                client.screen.height / (double) top.skyeyefast.mchjong.client.TableCanvas.HEIGHT);
            x = (client.screen.width - top.skyeyefast.mchjong.client.TableCanvas.WIDTH * scale) / 2.0 + x * scale;
            y = (client.screen.height - top.skyeyefast.mchjong.client.TableCanvas.HEIGHT * scale) / 2.0 + y * scale;
        }
        pointer(client, x, y);
    }

    static void verify(Minecraft client, MahjongTableBlockEntity table) {
        boolean active = client.isWindowActive();
        client.setWindowActive(true);
        try { verifyFocused(client, table); }
        finally { client.setWindowActive(active); }
    }

    private static void verifyFocused(Minecraft client, MahjongTableBlockEntity table) {
        RiichiView base = table.clientView();
        var seats = new ArrayList<>(base.seats());
        seats.set(0, new RiichiView.Seat(false, "Input test", true, false, false, 25000,
            IntStream.range(0, 14).boxed().toList(), 13, List.of(), List.of(), List.of(), false, false, false));
        var actions = new ArrayList<RiichiAction>();
        for (int tile = 0; tile < 14; tile++) actions.add(new RiichiAction(RiichiAction.Type.DISCARD, tile));
        actions.add(new RiichiAction(RiichiAction.Type.RIICHI, 0));
        actions.add(new RiichiAction(RiichiAction.Type.RIICHI, 4));
        RiichiView fixture = new RiichiView(base.tableId(), base.revision() + 1, base.decision() + 1,
            base.handNumber(), base.rules(), RiichiView.Phase.TURN, 0, 0, 0, 0, 0, 0, base.remaining(), base.wallBreak(),
            base.wall(), null, seats, actions, List.of(), "playing", List.of(), List.of(), List.of(), base.timeControl(), base.clocks(), List.of(), top.skyeyefast.mchjong.engine.PlayerHandVisibility.SELF, false, null, null, base.autoPlay(), false, 1, java.util.Map.of(), false, List.of(), 0, 0);
        table.acceptView(fixture);
        RiichiTableScreen screen = new RiichiTableScreen(table.getBlockPos());
        client.setScreen(screen);
        TableSettings.get().animations = false;
        screen.resetView();
        screen.keyPressed(GLFW.GLFW_KEY_C, 0, 0);
        require(screen.inspecting(), "Holding C did not inspect the seated view");
        screen.keyReleased(GLFW.GLFW_KEY_C, 0, 0);
        require(!screen.inspecting(), "Releasing C retained inspect input");
        if (client.player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat) {
            var before = TableSettings.get().cameraPosition(seat);
            float pitch = TableSettings.get().camera().pitch();
            screen.mouseClicked(screen.width / 2.0, screen.height / 2.0, 1);
            screen.mouseDragged(screen.width / 2.0, screen.height / 2.0, 1, 1, -1);
            require(TableSettings.get().camera().pitch() == pitch, "Deadzone changed camera pitch");
            screen.mouseDragged(screen.width / 2.0, screen.height / 2.0 - 20, 1, 0, -20);
            screen.mouseReleased(screen.width / 2.0, screen.height / 2.0 - 20, 1);
            require(TableSettings.get().camera().pitch() < pitch, "Right-drag did not tilt the view");
            require(TableSettings.get().cameraPosition(seat).distanceTo(before) < 1e-6, "Free look moved the eye");
            screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, 2);
            require(TableSettings.get().cameraPosition(seat).distanceTo(before) > .1, "Wheel did not move the eye");
            float yaw = TableSettings.get().camera().yaw(seat.seat());
            screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
            for (int i = 0; i < 3; i++) screen.tick();
            screen.keyReleased(GLFW.GLFW_KEY_RIGHT, 0, 0);
            require(TableSettings.get().camera().yaw(seat.seat()) > yaw + 2, "Held arrow did not continuously turn");
            yaw = TableSettings.get().camera().yaw(seat.seat());
            screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
            client.setWindowActive(false);
            screen.tick();
            client.setWindowActive(true);
            screen.tick();
            require(TableSettings.get().camera().yaw(seat.seat()) == yaw, "Unfocused camera retained held arrows");
            require(fixture.seats().get(0).hand().stream().noneMatch(tile -> selected(screen, fixture, tile)), "Arrow selected a tile");
            screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
            require(TableSettings.get().cameraPosition(seat).distanceTo(before) < 1e-6, "Home did not restore the eye");
            require(TableSettings.get().camera().pitch() == pitch, "Home did not restore pitch");
        }
        for (var mapping : top.skyeyefast.mchjong.client.TableKeys.ALL)
            require(java.util.Arrays.asList(client.options.keyMappings).contains(mapping), "Unregistered table binding: " + mapping.getName());
        var inspectKey = top.skyeyefast.mchjong.client.TableKeys.INSPECT;
        inspectKey.setKey(com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_I));
        net.minecraft.client.KeyMapping.resetMapping();
        screen.keyPressed(GLFW.GLFW_KEY_C, 0, 0);
        require(!screen.inspecting(), "Old inspect binding still active");
        screen.keyPressed(GLFW.GLFW_KEY_I, 0, 0);
        require(screen.inspecting(), "Rebound inspect key did not work");
        screen.keyReleased(GLFW.GLFW_KEY_I, 0, 0);
        inspectKey.setKey(inspectKey.getDefaultKey());
        net.minecraft.client.KeyMapping.resetMapping();
        screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
        screen.keyPressed(GLFW.GLFW_KEY_R, 0, 0);
        require(button(screen, "ui.mchjong.cancel_riichi"), "Riichi selection is not discoverable");
        clickHand(screen, fixture, 8);
        require(!selected(screen, fixture, 8), "Riichi selection accepted an illegal discard");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        require(client.screen == screen && !button(screen, "ui.mchjong.cancel_riichi"), "Esc closed the table instead of cancelling riichi");
        TableSettings.get().discardMode = TableSettings.DiscardMode.CONFIRM;
        clickHand(screen, fixture, 13);
        require(selected(screen, fixture, 13), "Mouse did not select the drawn tile");
        var cancelPoint = screenPoint(screen, new HandPoint(top.skyeyefast.mchjong.client.TableCanvas.WIDTH / 2.0,
            top.skyeyefast.mchjong.client.TableCanvas.HEIGHT - 19));
        screen.mouseClicked(cancelPoint.x(), cancelPoint.y(), 1);
        screen.mouseReleased(cancelPoint.x(), cancelPoint.y(), 1);
        require(!selected(screen, fixture, 13), "Right-click failed to cancel the selected tile");
        screen.keyPressed(GLFW.GLFW_KEY_R, 0, 0);
        require(button(screen, "ui.mchjong.cancel_riichi"), "Riichi could not be reopened after cancellation");
        screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
        require(!screen.immersive() && button(screen, "ui.mchjong.cancel_riichi"), "Switching view lost riichi selection mode");
        screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
        screen.tick();
        screen.keyReleased(GLFW.GLFW_KEY_RIGHT, 0, 0);
        require(button(screen, "ui.mchjong.cancel_riichi"), "Camera arrow cancelled riichi selection mode");
        screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
        require(screen.immersive() && button(screen, "ui.mchjong.cancel_riichi"), "Returning to immersive lost riichi selection mode");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        screen.resetView();
    }

    static void clickHand(RiichiTableScreen screen, RiichiView view, int tile) {
        var point = screenPoint(screen, handPoint(screen, view, tile));
        screen.mouseClicked(point.x(), point.y(), 0);
    }

    static void pointerHand(Minecraft client, RiichiTableScreen screen, RiichiView view, int tile) {
        var point = screenPoint(screen, handPoint(screen, view, tile));
        pointer(client, point.x(), point.y());
    }

    private record HandPoint(double x, double y) {}
    private static HandPoint handPoint(RiichiTableScreen screen, RiichiView view, int tile) {
        int layoutWidth = screen.immersive() ? top.skyeyefast.mchjong.client.TableCanvas.WIDTH : screen.width;
        int layoutHeight = screen.immersive() ? top.skyeyefast.mchjong.client.TableCanvas.HEIGHT : screen.height;
        int tileWidth = Math.min(screen.immersive() ? 58 : layoutHeight < 360 ? 24 : 32, (layoutWidth - 36) / 14);
        var hand = view.seats().get(view.viewerSeat()).hand();
        int drawGap = screen.immersive() ? Math.max(18, tileWidth / 2) : Math.max(4, tileWidth / 3);
        int x = (layoutWidth - Math.max(14, hand.size()) * tileWidth - drawGap) / 2
            + hand.indexOf(tile) * tileWidth + tileWidth / 2;
        if (tile == view.seats().get(view.viewerSeat()).drawn()) x += drawGap;
        int footer = view.autoPlay() != null ? screen.immersive() ? 48 : 24 : 0;
        return new HandPoint(x, layoutHeight - footer - 15 - Math.round(tileWidth * 1.53846f) / 2.0);
    }

    private static HandPoint screenPoint(RiichiTableScreen screen, HandPoint point) {
        if (!screen.immersive()) return point;
        double scale = Math.min(screen.width / (double) top.skyeyefast.mchjong.client.TableCanvas.WIDTH,
            screen.height / (double) top.skyeyefast.mchjong.client.TableCanvas.HEIGHT);
        return new HandPoint((screen.width - top.skyeyefast.mchjong.client.TableCanvas.WIDTH * scale) / 2.0 + point.x() * scale,
            (screen.height - top.skyeyefast.mchjong.client.TableCanvas.HEIGHT * scale) / 2.0 + point.y() * scale);
    }

    private static boolean selected(RiichiTableScreen screen, RiichiView view, int tile) {
        return RiichiTableScene.build(view).stream().filter(piece -> piece.area() == RiichiTableScene.Area.HAND && piece.seat() == 0 && piece.tile() == tile)
            .anyMatch(piece -> screen.selected(screen.tablePos(), piece));
    }

    /** Focus real call buttons while mouse highlighting is disabled; never submit a fixture action. */
    static void verifyCallFocus(Minecraft client, MahjongTableBlockEntity table, RiichiAction.Type type) {
        var base = table.clientView();
        var consumed = switch (type) {
            case CHI -> List.of(4, 8);
            case PON -> List.of(12, 13);
            case OPEN_KAN -> List.of(12, 13, 15);
            default -> throw new IllegalArgumentException("Expected an open call");
        };
        var seats = new ArrayList<>(base.seats());
        seats.set(0, new RiichiView.Seat(false, "Keyboard focus", true, false, false, 25000,
            List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 13, 15), Tile.ABSENT,
            List.of(), List.of(), List.of(), false, false, false));
        int from = base.rules().players() - 1;
        var source = seats.get(from);
        seats.set(from, new RiichiView.Seat(false, source.name(), true, false, false, 25000,
            java.util.Collections.nCopies(13, Tile.HIDDEN), Tile.ABSENT,
            List.of(), List.of(new RiichiDiscard(14, false, false, false)), List.of(), false, false, false));
        var action = new RiichiAction(type, consumed);
        var fixture = new RiichiView(base.tableId(), base.revision() + 1, base.decision() + 1,
            base.handNumber(), base.rules(), RiichiView.Phase.REACTION, 0, 0, 0, 0, 0, from, base.remaining(), base.wallBreak(),
            base.wall(), new RiichiView.Focus(from, 14, false, 0), seats, List.of(new RiichiAction(RiichiAction.Type.PASS), action),
            List.of(), "playing", List.of(), List.of(), List.of(), base.timeControl(), base.clocks(), List.of(), top.skyeyefast.mchjong.engine.PlayerHandVisibility.SELF, false, null, null, base.autoPlay(), false, 1, java.util.Map.of(), false, List.of(), 0, 0);
        table.acceptView(fixture);
        TableSettings.get().animations = false;
        TableSettings.get().highlightTiles = false;
        var screen = new RiichiTableScreen(table.getBlockPos());
        client.setScreen(screen);
        screen.resetView();
        String label = Component.translatable(action.translationKey()).getString();
        var button = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
        screen.setFocused(button);
        for (var piece : RiichiTableScene.build(fixture)) if (piece.area() == RiichiTableScene.Area.HAND && piece.seat() == 0)
            require((screen.highlight(table.getBlockPos(), piece) != 0) == consumed.contains(piece.tile()),
                "Keyboard call focus highlighted the wrong physical tiles: " + type);
    }

    private static boolean button(RiichiTableScreen screen, String key) {
        String label = Component.translatable(key).getString();
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .anyMatch(widget -> widget.getMessage().getString().equals(label));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
