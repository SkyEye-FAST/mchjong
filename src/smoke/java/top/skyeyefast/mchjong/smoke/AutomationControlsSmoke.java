package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.engine.AutoPlay;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Exercise every preference through its real button, C2S packet and authoritative S2C snapshot. */
final class AutomationControlsSmoke {
    private static final String[] KEYS = {"ui.mchjong.auto_sort", "ui.mchjong.auto_win",
        "ui.mchjong.no_calls", "ui.mchjong.auto_discard"};
    private int stage, ticks, totalTicks, toggle;
    private long decision;
    private AutoPlay initial, expected;
    private final SettingsLanguageSmoke languages = new SettingsLanguageSmoke();

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        require(++totalTicks < 1400, "Automatic controls timed out at stage " + stage + ", toggle " + toggle);
        ticks++;
        var view = table.clientView();
        require(view != null && view.autoPlay() != null, "Seated automatic-table preferences are missing");
        if (stage == 0) {
            initial = view.autoPlay();
            var parent = new TableScreen(table.getBlockPos());
            client.setScreen(parent);
            parent.resetView();
            click(client, Component.translatable("ui.mchjong.automation_show").getString());
            next(1);
        } else if (stage == 1 && ticks > 12) {
            checkBounds(client);
            Screenshot.grab(output.toFile(), "52-automatic-controls.png", client.getMainRenderTarget(), ignored -> {});
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeDisplay();
            client.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_V, 0, 0);
            next(2);
        } else if (stage == 2 && ticks > 12) {
            checkBounds(client);
            require(client.screen.width == 320 && client.screen.height == 240, "Automatic controls did not reflow to 320x240");
            require(((TableScreen) client.screen).overhead(), "Small controls did not retain the overhead hand");
            Screenshot.grab(output.toFile(), "53-automatic-controls-small.png", client.getMainRenderTarget(), ignored -> {});
            next(3);
        } else if (stage == 3 && ticks > 2) {
            var option = AutoPlay.Option.values()[toggle / 2];
            boolean enabled = view.autoPlay().enabled(option);
            String label = Component.translatable("settings.mchjong.toggle", Component.translatable(KEYS[toggle / 2]),
                Component.translatable(enabled ? "options.on" : "options.off")).getString();
            expected = view.autoPlay().with(option, !enabled);
            decision = view.decision();
            click(client, label);
            next(4);
        } else if (stage == 4 && ticks > 2) {
            if (!expected.equals(view.autoPlay())) {
                // A live opponent may advance the decision between the rendered frame and the packet.
                if (decision != view.decision()) next(3);
                return false;
            }
            checkBounds(client);
            if (++toggle == 8) {
                require(initial.equals(view.autoPlay()), "Preference round-trip changed another option");
                next(5);
                return false;
            }
            next(3);
        } else if (stage == 5 && languages.tick(client, output)) {
            click(client, Component.translatable("ui.mchjong.automation_hide").getString());
            require(client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .noneMatch(widget -> widget.getMessage().getString().contains(Component.translatable(KEYS[0]).getString())),
                "Collapsed controls still expose the option buttons");
            client.screen.onClose();
            return true;
        }
        return false;
    }

    private void next(int value) { stage = value; ticks = 0; }

    private static void click(Minecraft client, String label) {
        var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast).filter(widget -> widget.getMessage().getString().equals(label))
            .findFirst().orElseThrow(() -> new IllegalStateException("Missing automatic control: " + label));
        require(button.active, "Automatic control is disabled: " + label);
        client.screen.mouseClicked(button.getX() + button.getWidth() / 2.0, button.getY() + 10, 0);
        client.screen.mouseReleased(button.getX() + button.getWidth() / 2.0, button.getY() + 10, 0);
    }

    static void checkBounds(Minecraft client) {
        var widgets = client.screen.children().stream().filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast).filter(widget -> widget.visible).toList();
        for (int i = 0; i < widgets.size(); i++) {
            var a = widgets.get(i);
            require(a.getX() >= 0 && a.getY() >= 0 && a.getRight() <= client.screen.width
                && a.getBottom() <= client.screen.height, "Automatic control exceeds viewport");
            for (int j = i + 1; j < widgets.size(); j++) {
                var b = widgets.get(j);
                require(a.getRight() <= b.getX() || b.getRight() <= a.getX()
                    || a.getBottom() <= b.getY() || b.getBottom() <= a.getY(), "Automatic controls overlap");
            }
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
