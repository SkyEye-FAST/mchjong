package top.skyeyefast.mchjong.smoke;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.compat.patchouli.ManualClient;
import top.skyeyefast.mchjong.compat.patchouli.ManualRecommendationScreen;

final class ManualSmoke {
    private static int ticks;
    private ManualSmoke() {}

    static boolean tick(Minecraft client, Path output) throws IOException {
        if (Boolean.getBoolean("mchjong.smoke.patchouli"))
            return InstalledManualSmoke.tick(client, output);
        if (ticks++ == 0) {
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeGui();
        }
        if (ticks < 30) return false;
        require(client.screen instanceof ManualRecommendationScreen, "Missing automatic install recommendation");
        SmokeScreenshots.grab(output.toFile(), "manual-recommendation.png", client.getMainRenderTarget(), 1, message -> {});
        var screen = client.screen;
        var buttons = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
        require(buttons.size() == 3, "Missing recommendation actions");
        var enter = new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
        buttons.getFirst().onPress(enter);
        require(client.screen instanceof ConfirmLinkScreen, "Download must ask before opening browser");
        client.setScreen(screen);
        buttons.get(1).onPress(enter);
        ManualClient.tick(false, () -> { throw new AssertionError("Absent API invoked"); });
        require(client.screen == null, "Recommendation repeated in same session");
        net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(ManualClient.OPEN.saveString()));
        ManualClient.tick(false, () -> { throw new AssertionError("Absent API invoked"); });
        require(client.screen instanceof ManualRecommendationScreen, "Manual binding must reopen recommendation");
        client.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList().get(2).onPress(enter);
        require(!TableSettings.load(TableSettings.configPath()).recommendPatchouli, "Dismissal was not persisted");
        return true;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
