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

    static void verify(Minecraft client, MahjongTableBlockEntity table) {
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
            base.wall(), null, seats, actions, List.of(), "playing", List.of(), List.of(), base.timeControl(), base.clocks(), List.of(), false, null, null, base.autoPlay());
        table.acceptView(fixture);
        TableScreen screen = new TableScreen(table.getBlockPos());
        client.setScreen(screen);
        TableSettings.get().animations = false;
        screen.keyPressed(GLFW.GLFW_KEY_R, 0, 0);
        require(button(screen, "ui.mchjong.cancel_riichi"), "Riichi selection is not discoverable");
        screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
        require(selected(screen, fixture, 0), "Riichi did not select its first legal discard");
        require(button(screen, "ui.mchjong.confirm_riichi"), "Riichi has no explicit confirmation");
        screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
        require(selected(screen, fixture, 4), "Riichi selection did not skip illegal discards");
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        require(client.screen == screen && !button(screen, "ui.mchjong.confirm_riichi"), "Esc closed the table instead of cancelling riichi");
        screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        require(selected(screen, fixture, 13), "Initial Left selection did not choose the last tile");
        screen.mouseClicked(screen.width / 2.0, screen.height - 19, 1);
        screen.mouseReleased(screen.width / 2.0, screen.height - 19, 1);
        require(!selected(screen, fixture, 13), "Right-click failed to cancel the selected tile");
        screen.keyPressed(GLFW.GLFW_KEY_R, 0, 0);
        screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
        screen.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
        require(button(screen, "ui.mchjong.confirm_riichi"), "Riichi could not be reopened after cancellation");
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
            List.of(), "playing", List.of(), List.of(), base.timeControl(), base.clocks(), List.of(), false, null, null, base.autoPlay());
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
