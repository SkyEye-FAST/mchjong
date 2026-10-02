package top.skyeyefast.mchjong.smoke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;

/** Navigate the public lobby pages using the same native controls as players. */
final class LobbySmoke {
    private LobbySmoke() {}
    private static AbstractButton visible(Minecraft client, String text) {
        return client.screen.children().stream().filter(AbstractButton.class::isInstance).map(AbstractButton.class::cast)
            .filter(button -> button.getMessage().getString().startsWith(text))
            .findFirst().orElse(null);
    }
    static void settings(Minecraft client) {
        var tab = visible(client, Component.translatable("lobby.mchjong.match_settings").getString());
        if (tab != null) tab.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
    }
    static AbstractButton find(Minecraft client, String text) {
        var result = visible(client, text);
        if (result != null) return result;
        settings(client);
        for (int page = 0; page < 8; page++) {
            result = visible(client, text);
            if (result != null) return result;
            var next = visible(client, "›");
            if (next == null) next = visible(client, ">");
            if (next == null || !next.active) break;
            next.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
        }
        return null;
    }
}
