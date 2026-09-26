package top.skyeyefast.mchjong.smoke;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.compat.patchouli.ManualClient;

final class ManualSmoke {
    private static int ticks;
    private ManualSmoke() {}

    static boolean tick(Minecraft client, Path output) throws IOException {
        if (Boolean.getBoolean("mchjong.smoke.patchouli"))
            return InstalledManualSmoke.tick(client, output);
        if (ticks++ < 30) return false;
        require(client.screen == null, "Recommendation must not open a screen");
        var messages = messages(client);
        var text = Component.translatable("manual.mchjong.recommend.text").getString();
        var recommendation = messages.stream().filter(message -> message.getString().startsWith(text)).toList();
        require(recommendation.size() == 1, "Expected one automatic chat recommendation");
        require(hasDownload(recommendation.get(0)), "Missing clickable download URL");
        require(client.getSingleplayerServer().submit(() -> client.getSingleplayerServer().getRecipeManager()
            .byKey(top.skyeyefast.mchjong.world.MahjongContent.id("mahjong_manual")).isEmpty()).join(),
            "Handbook recipe loaded without Patchouli");
        int count = messages.size();
        ManualClient.tick(false);
        require(messages(client).size() == count, "Recommendation repeated in same session");
        SmokeScreenshots.grab(output.toFile(), "manual-recommendation.png", client.getMainRenderTarget(), message -> {});
        return true;
    }

    static List<Component> messages(Minecraft client) {
        try {
            var chat = client.gui.getChat();
            var field = chat.getClass().getDeclaredField("allMessages");
            field.setAccessible(true);
            var result = new java.util.ArrayList<Component>();
            for (Object message : (List<?>) field.get(chat))
                result.add((Component) message.getClass().getMethod("content").invoke(message));
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot inspect chat messages", failure);
        }
    }

    private static boolean hasDownload(Component message) {
        var click = message.getStyle().getClickEvent();
        return (click != null && click.getAction() == ClickEvent.Action.OPEN_URL
            && click.getValue().equals("https://modrinth.com/mod/patchouli/versions"))
            || message.getSiblings().stream().anyMatch(ManualSmoke::hasDownload);
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
