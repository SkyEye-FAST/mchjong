package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettingsScreen;
import top.skyeyefast.mchjong.engine.AutoPlay;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Click real controls, wait for authoritative packets, and restore all preferences. */
final class AutoPlayControlSmoke {
    private static final String[] KEYS = {"settings.mchjong.auto_sort", "settings.mchjong.auto_win",
        "settings.mchjong.no_calls", "settings.mchjong.auto_discard"};
    private int stage, ticks, toggles, windowWidth, windowHeight, guiScale;
    private long decision;
    private AutoPlay expected;
    private TableScreen parent;

    boolean tick(Minecraft client, MahjongTableBlockEntity table, Path output) {
        if (++ticks > 100) throw new IllegalStateException("Automatic controls timed out at " + stage + "/" + toggles);
        var view = table.clientView();
        if (stage == 0) {
            check(AutoPlay.DEFAULT.equals(view.autoPlay()), "Automatic table did not expose its seat's default preferences");
            parent = (TableScreen) client.screen;
            client.setScreen(new TableSettingsScreen(parent));
            next(1);
        } else if (stage == 1 && ticks > 5) {
            click(client, "settings.mchjong.tab.4");
            next(2);
        } else if (stage == 2 && ticks > 5) {
            int option = toggles % 4;
            var preference = AutoPlay.Option.values()[option];
            expected = view.autoPlay().with(preference, !view.autoPlay().enabled(preference));
            decision = view.decision();
            click(client, KEYS[option]);
            next(3);
        } else if (stage == 3 && expected.equals(view.autoPlay()) && ticks > 5) {
            check(decision == view.decision(), "A preference invalidated the lobby decision");
            if (++toggles == 4) {
                Screenshot.grab(output.toFile(), "27-automatic-options.png", client.getMainRenderTarget(), ignored -> {});
                windowWidth = client.getWindow().getScreenWidth(); windowHeight = client.getWindow().getScreenHeight();
                guiScale = client.options.guiScale().get();
                client.getWindow().setWindowed(960, 720); client.options.guiScale().set(3); client.resizeDisplay();
                next(4);
            } else if (toggles == 8) {
                check(AutoPlay.DEFAULT.equals(view.autoPlay()), "Automatic controls did not restore their original state");
                client.setScreen(parent);
                return true;
            } else next(2);
        } else if (stage == 4 && ticks > 10) {
            for (var child : client.screen.children()) if (child instanceof AbstractWidget widget && widget.visible)
                check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= client.screen.width
                    && widget.getBottom() <= client.screen.height, "Automatic settings escaped the small screen");
            Screenshot.grab(output.toFile(), "27-automatic-options-small.png", client.getMainRenderTarget(), ignored -> {});
            client.getWindow().setWindowed(windowWidth, windowHeight); client.options.guiScale().set(guiScale); client.resizeDisplay();
            next(2);
        }
        return false;
    }

    private void next(int value) { stage = value; ticks = 0; }
    private static void click(Minecraft client, String key) {
        String label = Component.translatable(key).getString();
        var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().contains(label)).findFirst().orElseThrow();
        check(button.active, "Disabled automatic control: " + key);
        client.screen.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
