package top.skyeyefast.mchjong.smoke;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.MahjongButton;
import top.skyeyefast.mchjong.client.MahjongEditBox;
import top.skyeyefast.mchjong.client.MahjongSlider;
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettingsScreen;

/** Exercise the themed widgets with the actual game's input and font implementations. */
final class UiControlsSmoke {
    static void verify(Minecraft client) {
        var settings = new TableSettingsScreen(new TableScreen(BlockPos.ZERO));
        settings.init(320, 240);
        for (int tab = 0; tab < 4; tab++) {
            String label = Component.translatable("settings.mchjong.tab." + tab).getString();
            var button = settings.children().stream().filter(MahjongButton.class::isInstance).map(MahjongButton.class::cast)
                .filter(widget -> widget.getMessage().getString().equals(label)).findFirst().orElseThrow();
            require(settings.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(
                button.getX() + 5, button.getY() + 5, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false),
                "Themed tab is not clickable");
            for (var child : settings.children()) if (child instanceof AbstractWidget widget) {
                require(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= 320 && widget.getBottom() <= 240,
                    "Settings control outside minimum viewport: " + widget.getMessage().getString());
                require(!(widget instanceof Button) || widget instanceof MahjongButton, "Settings still uses a vanilla button");
            }
        }
        var presses = new AtomicInteger();
        var button = new MahjongButton(0, 0, 100, 20, Component.translatable("gui.done"), ignored -> presses.incrementAndGet());
        button.setFocused(true);
        require(button.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)) && presses.get() == 1, "Themed button lost keyboard activation");
        button.active = false;
        button.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
        button.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(
            5, 5, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
        require(presses.get() == 1, "Disabled button activated");
        var field = new MahjongEditBox(client.font, 0, 0, 100, 20, Component.translatable("ui.mchjong.reserve_time"));
        field.setFilter(value -> value.matches("[0-9]{0,3}"));
        field.setValue("12");
        field.setFocused(true);
        field.charTyped(new net.minecraft.client.input.CharacterEvent('3'));
        require(field.getValue().equals("123"), "Themed field lost text entry");
        field.charTyped(new net.minecraft.client.input.CharacterEvent('x'));
        require(field.getValue().equals("123"), "Themed field bypasses its filter");
        field.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
        require(field.getValue().equals("12"), "Themed field lost cursor editing");
        var changes = new AtomicInteger();
        var slider = new MahjongSlider(0, 0, 100, 20, Component.translatable("replay.mchjong.title"), 0.5) {
            @Override protected void updateMessage() {}
            @Override protected void applyValue() { changes.incrementAndGet(); }
        };
        slider.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(
            90, 10, new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
        slider.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(
            20, 10, new net.minecraft.client.input.MouseButtonInfo(0, 0)), -70, 0);
        slider.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(
            20, 10, new net.minecraft.client.input.MouseButtonInfo(0, 0)));
        require(changes.get() >= 2, "Themed slider lost native clicking or dragging");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
