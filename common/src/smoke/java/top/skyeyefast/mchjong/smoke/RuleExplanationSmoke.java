package top.skyeyefast.mchjong.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.TableHelpScreen;

final class RuleExplanationSmoke {
    private RuleExplanationSmoke() {}
    static void check(Minecraft client, Component label) {
        var parent = client.screen;
        var name = Component.translatable("rules.mchjong.explanation", label).getString();
        var button = parent.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
            .filter(widget -> widget.getMessage().getString().equals(name)).findFirst().orElseThrow();
        parent.setFocused(button); parent.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
        ManualSmoke.require(client.screen instanceof TableHelpScreen, "Rule explanation failed: " + name);
        AutomationControlsSmoke.checkBounds(client);
        client.screen.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        ManualSmoke.require(client.screen == parent, "Rule explanation lost its draft parent");
    }
}
