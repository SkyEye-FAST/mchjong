package top.skyeyefast.mchjong.smoke;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.TableScene;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Discard;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.TableView;
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

    static void verify(Minecraft client, MahjongTableBlockEntity table) {
        boolean active = client.isWindowActive();
        client.setWindowActive(true);
        try { verifyFocused(client, table); }
        finally { client.setWindowActive(active); }
    }

    private static void verifyFocused(Minecraft client, MahjongTableBlockEntity table) {
        TableView base = table.clientView();
        var seats = new ArrayList<>(base.seats());
        seats.set(0, new TableView.Seat("Input test", true, false, false, 25000,
            IntStream.range(0, 14).boxed().toList(), 13, List.of(), List.of(), List.of(), false, false));
        var actions = new ArrayList<Action>();
        for (int tile = 0; tile < 14; tile++) actions.add(new Action(Action.Type.DISCARD, tile));
        actions.add(new Action(Action.Type.RIICHI, 0));
        actions.add(new Action(Action.Type.RIICHI, 4));
        TableView fixture = new TableView(base.tableId(), base.revision() + 1, base.decision() + 1,
            base.handNumber(), base.rules(), Game.Phase.TURN, 0, 0, 0, 0, 0, 0, base.remaining(), base.wallBreak(),
            base.wall(), null, seats, actions, List.of(), "playing", List.of(), List.of(), base.timeControl(), base.clocks(), List.of(), false, null, null, base.autoPlay(), false, 1);
        table.acceptView(fixture);
        TableScreen screen = new TableScreen(table.getBlockPos());
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
        screen.mouseClicked(screen.width / 2.0, screen.height - 19, 1);
        screen.mouseReleased(screen.width / 2.0, screen.height - 19, 1);
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

    static void clickHand(TableScreen screen, TableView view, int tile) {
        int tileWidth = Math.min(screen.height < 360 ? 20 : 26, (screen.width - 36) / 14);
        var hand = view.seats().get(view.viewerSeat()).hand();
        int x = (screen.width - Math.max(14, hand.size()) * tileWidth - Math.max(4, tileWidth / 3)) / 2
            + hand.indexOf(tile) * tileWidth + tileWidth / 2;
        if (tile == view.seats().get(view.viewerSeat()).drawn()) x += Math.max(4, tileWidth / 3);
        int footer = view.autoPlay() != null ? 24 : 0;
        screen.mouseClicked(x, screen.height - footer - 20 - Math.round(tileWidth * 1.53846f) / 2, 0);
    }

    private static boolean selected(TableScreen screen, TableView view, int tile) {
        return TableScene.build(view).stream().filter(piece -> piece.area() == TableScene.Area.HAND && piece.seat() == 0 && piece.tile() == tile)
            .anyMatch(piece -> screen.selected(screen.tablePos(), piece));
    }

    /** Focus real call buttons while mouse highlighting is disabled; never submit a fixture action. */
    static void verifyCallFocus(Minecraft client, MahjongTableBlockEntity table, Action.Type type) {
        var base = table.clientView();
        var consumed = switch (type) {
            case CHI -> List.of(4, 8);
            case PON -> List.of(12, 13);
            case OPEN_KAN -> List.of(12, 13, 15);
            default -> throw new IllegalArgumentException("Expected an open call");
        };
        var seats = new ArrayList<>(base.seats());
        seats.set(0, new TableView.Seat("Keyboard focus", true, false, false, 25000,
            List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 13, 15), Tile.ABSENT,
            List.of(), List.of(), List.of(), false, false));
        int from = base.rules().players() - 1;
        var source = seats.get(from);
        seats.set(from, new TableView.Seat(source.name(), true, false, false, 25000,
            java.util.Collections.nCopies(13, Tile.HIDDEN), Tile.ABSENT,
            List.of(), List.of(new Discard(14, false, false, false)), List.of(), false, false));
        var action = new Action(type, consumed);
        var fixture = new TableView(base.tableId(), base.revision() + 1, base.decision() + 1,
            base.handNumber(), base.rules(), Game.Phase.REACTION, 0, 0, 0, 0, 0, from, base.remaining(), base.wallBreak(),
            base.wall(), new TableView.Focus(from, 14, false, 0), seats, List.of(new Action(Action.Type.PASS), action),
            List.of(), "playing", List.of(), List.of(), base.timeControl(), base.clocks(), List.of(), false, null, null, base.autoPlay(), false, 1);
        table.acceptView(fixture);
        TableSettings.get().animations = false;
        TableSettings.get().highlightTiles = false;
        var screen = new TableScreen(table.getBlockPos());
        client.setScreen(screen);
        screen.resetView();
        String label = Component.translatable(action.translationKey()).getString();
        var button = screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
        screen.setFocused(button);
        for (var piece : TableScene.build(fixture)) if (piece.area() == TableScene.Area.HAND && piece.seat() == 0)
            require((screen.highlight(table.getBlockPos(), piece) != 0) == consumed.contains(piece.tile()),
                "Keyboard call focus highlighted the wrong physical tiles: " + type);
    }

    private static boolean button(TableScreen screen, String key) {
        String label = Component.translatable(key).getString();
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .anyMatch(widget -> widget.getMessage().getString().equals(label));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
