package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Render the actual settings tabs at both acceptance sizes with native language reloads. */
final class SettingsLanguageSmoke {
    private static final String[] LANGUAGES = {"zh_cn", "en_us"};
    private int sample = -1, size, ticks, width, height, scale;
    private String original;
    private CompletableFuture<Void> reload;

    boolean tick(Minecraft client, Path output) {
        if (sample == -1) {
            original = client.getLanguageManager().getSelected();
            width = client.getWindow().getScreenWidth(); height = client.getWindow().getScreenHeight();
            scale = client.options.guiScale().get();
            sample = 0;
            resize(client);
            select(client, LANGUAGES[sample]);
            return false;
        }
        if (!reload.isDone() || client.getOverlay() != null) return false;
        reload.join();
        if (++ticks == 1 && sample < LANGUAGES.length) chooseTab(client);
        if (ticks < 10) return false;
        if (sample == LANGUAGES.length) return true;
        AutomationControlsSmoke.checkBounds(client);
        int logicalWidth = size == 0 ? 640 : 320, logicalHeight = size == 0 ? 400 : 240;
        if (client.screen.width != logicalWidth || client.screen.height != logicalHeight)
            throw new IllegalStateException("Settings language viewport mismatch");
        Screenshot.grab(output.toFile(), "54-settings-" + LANGUAGES[sample] + "-" + logicalWidth + "x"
            + logicalHeight + "-automatic.png", client.getMainRenderTarget(), ignored -> {});
        ticks = 0;
        if (++size == 2 || sample == 1) {
            size = 0;
            sample++;
            select(client, sample == LANGUAGES.length ? original : LANGUAGES[sample]);
        }
        if (sample == LANGUAGES.length) {
            client.getWindow().setWindowed(width, height);
            client.options.guiScale().set(scale);
            client.resizeDisplay();
        } else resize(client);
        return false;
    }

    private void select(Minecraft client, String language) {
        client.getLanguageManager().setSelected(language);
        reload = client.reloadResourcePacks();
        ticks = 0;
    }

    private void resize(Minecraft client) {
        client.getWindow().setWindowed(size == 0 ? 1280 : 960, size == 0 ? 800 : 720);
        client.options.guiScale().set(size == 0 ? 2 : 3);
        client.resizeDisplay();
    }

    private void chooseTab(Minecraft client) {
        String label = Component.translatable("settings.mchjong.tab.4").getString();
        var button = client.screen.children().stream().filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast).filter(widget -> widget.getMessage().getString().equals(label))
            .findFirst().orElseThrow();
        client.screen.mouseClicked(button.getX() + 5, button.getY() + 5, 0);
        client.screen.mouseReleased(button.getX() + 5, button.getY() + 5, 0);
    }
}
