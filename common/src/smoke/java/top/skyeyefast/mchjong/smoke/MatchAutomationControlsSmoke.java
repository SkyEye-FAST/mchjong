package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.McrTableScreen;
import top.skyeyefast.mchjong.client.SichuanTableScreen;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** One shared UI/packet check for the three common options on the non-Riichi tables. */
final class MatchAutomationControlsSmoke {
    private int stage, ticks;
    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output, String prefix) {
        if (stage == 11) return true;
        var room = table.clientTableRoom();
        if (stage == 0 && client.screen.children().stream().filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast).noneMatch(widget -> widget.getMessage().getString()
                .startsWith(Component.translatable("ui.mchjong.auto_win").getString()))) return false;
        if (++ticks < 4) return false;
        switch (stage) {
            case 0 -> {
                client.getWindow().setWindowed(960, 720); client.options.guiScale().set(3); client.resizeDisplay();
                next();
            }
            case 1 -> {
                bounds(client);
                capture(client, output, prefix + "-automation-seated.png");
                press(client, "ui.mchjong.automation_show"); next();
            }
            case 2 -> {
                bounds(client);
                var button = option(client);
                client.screen.setFocused(button);
                require(client.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0), "Automation rejected native keyboard input");
                next();
            }
            case 3 -> {
                if (!room.automation().win() || !option(client).active) return false;
                require(client.screen.getFocused() == option(client), "Automation lost focus on acknowledgement");
                var button = option(client);
                double x = button.getX() + 2, y = button.getY() + 2;
                require(client.screen.mouseClicked(x, y, 0), "Automation control rejected pointer input");
                client.screen.mouseReleased(x, y, 0);
                next();
            }
            case 4 -> {
                if (room.automation().win() || !option(client).active) return false;
                client.screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0); next();
            }
            case 5 -> {
                require(immersive(client), "Automation lost the immersive view");
                bounds(client);
                capture(client, output, prefix + "-automation-immersive.png");
                press(client, "ui.mchjong.automation_hide"); next();
            }
            case 6 -> {
                bounds(client);
                require(!room.automation().win() && !room.automation().noCalls() && !room.automation().discard(),
                    "Automation round trip changed another preference");
                press(client, "replay.mchjong.title"); next();
            }
            case 7 -> {
                if (!(client.screen instanceof top.skyeyefast.mchjong.client.ReplayBrowserScreen browser)) {
                    require(ticks < 80, "Immersive replay button did not open the browser");
                    return false;
                }
                if (ticks < 16) return false;
                browser.onClose(); next();
            }
            case 8 -> {
                require(immersive(client), "Replay browser lost its immersive parent");
                client.screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
                press(client, "replay.mchjong.title.short"); next();
            }
            case 9 -> {
                if (!(client.screen instanceof top.skyeyefast.mchjong.client.ReplayBrowserScreen browser)) {
                    require(ticks < 80, "Seated replay button did not open the browser");
                    return false;
                }
                if (ticks < 16) return false;
                browser.onClose(); next();
            }
            case 10 -> {
                require(!immersive(client), "Replay browser lost its seated parent");
                client.getWindow().setWindowed(1280, 800); client.options.guiScale().set(2); client.resizeDisplay();
                next();
            }
            default -> throw new IllegalStateException("Unexpected automation smoke stage");
        }
        return stage == 11;
    }
    private void next() { stage++; ticks = 0; }
    private static AbstractWidget option(Minecraft client) {
        String label = Component.translatable("ui.mchjong.auto_win").getString();
        return client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().startsWith(label)).findFirst().orElseThrow();
    }
    private static boolean immersive(Minecraft client) {
        return client.screen instanceof McrTableScreen screen ? screen.immersive() : ((SichuanTableScreen) client.screen).immersive();
    }
    private static void press(Minecraft client, String key) {
        String label = Component.translatable(key).getString();
        var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
        double x = button.getX() + button.getWidth() / 2.0, y = button.getY() + button.getHeight() / 2.0;
        if (immersive(client)) {
            double scale = Math.min(client.screen.width / 1280.0, client.screen.height / 800.0);
            x = (client.screen.width - 1280 * scale) / 2 + x * scale;
            y = (client.screen.height - 800 * scale) / 2 + y * scale;
        }
        require(client.screen.mouseClicked(x, y, 0), "Automation control rejected pointer input");
        client.screen.mouseReleased(x, y, 0);
    }
    private static void bounds(Minecraft client) {
        int width = immersive(client) ? 1280 : client.screen.width, height = immersive(client) ? 800 : client.screen.height;
        var widgets = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.visible && !widget.getClass().getSimpleName().equals("HandTarget")
                && !widget.getClass().getSimpleName().equals("TableHints")).toList();
        for (int index = 0; index < widgets.size(); index++) {
            var a = widgets.get(index);
            require(a.getX() >= 0 && a.getY() >= 0 && a.getRight() <= width && a.getBottom() <= height, "Automation exceeds canvas");
            for (int other = index + 1; other < widgets.size(); other++) {
                var b = widgets.get(other);
                require(a.getRight() <= b.getX() || b.getRight() <= a.getX() || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(),
                    "Automation overlaps another control");
            }
        }
        for (String key : new String[]{"auto_win", "no_calls", "auto_discard"}) {
            var button = widgets.stream().filter(widget -> widget.getMessage().getString().startsWith(
                Component.translatable("ui.mchjong." + key).getString())).findFirst().orElseThrow();
            require(!immersive(client) || button.getBottom() <= height - 32, "Automation overlaps the help line");
        }
        require(widgets.stream().noneMatch(widget -> widget.getMessage().getString().startsWith(
            Component.translatable("ui.mchjong.auto_sort").getString()) || widget.getMessage().getString().startsWith(
            Component.translatable("ui.mchjong.auto_kita").getString())), "Riichi controls leaked into another rule");
    }
    private static void capture(Minecraft client, Path output, String name) {
        SmokeScreenshots.grab(output.toFile(), name, client.getMainRenderTarget(), ignored -> {});
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
