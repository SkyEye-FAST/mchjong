package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/** Render the live table controls at both acceptance sizes with native language reloads. */
final class SettingsLanguageSmoke {
    private static final String[] LANGUAGES = {"zh_cn", "zh_tw", "ja_jp", "en_us"};
    private int sample = -1, size, ticks, width, height, scale;
    private boolean collapsed;
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
        if (++ticks < 10) return false;
        if (sample == LANGUAGES.length) return true;
        AutomationControlsSmoke.checkBounds(client);
        AutomationControlsSmoke.checkOptions(client, 5);
        int logicalWidth = size == 0 ? 640 : 320, logicalHeight = size == 0 ? 400 : 240;
        if (client.screen.width != logicalWidth || client.screen.height != logicalHeight)
            throw new IllegalStateException("Table controls language viewport mismatch");
        Screenshot.grab(output.toFile(), "54-table-options-" + LANGUAGES[sample] + "-" + logicalWidth + "x"
            + logicalHeight + (collapsed ? "-collapsed.png" : "-expanded.png"), client.getMainRenderTarget(), ignored -> {});
        ticks = 0;
        AutomationControlsSmoke.click(client, net.minecraft.network.chat.Component.translatable(
            collapsed ? "ui.mchjong.automation_show" : "ui.mchjong.automation_hide").getString());
        collapsed = !collapsed;
        if (collapsed) return false;
        if (++size == 2) {
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

}
