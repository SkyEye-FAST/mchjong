package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.DeleteReplayScreen;
import top.skyeyefast.mchjong.client.ReplayBrowserScreen;
import top.skyeyefast.mchjong.engine.ReplayMatch;

/** Presentation-only fixtures; the separate replay smoke exercises real authenticated commands. */
final class ReplayInterfaceSmoke {
    private static final String[] LANGUAGES = {"en_us", "ja_jp", "zh_cn", "zh_tw"};
    private final ReplayMatch.Header header;
    private CompletableFuture<Void> reload;
    private ReplayBrowserScreen browser;
    private String language;
    private int sample = -1, stage, settled, windowWidth, windowHeight, scale;

    ReplayInterfaceSmoke(ReplayMatch.Header header) { this.header = header; }

    boolean tick(Minecraft client, Path output) {
        if (sample == -1) {
            language = client.getLanguageManager().getSelected();
            scale = client.options.guiScale().get();
            windowWidth = client.getWindow().getScreenWidth();
            windowHeight = client.getWindow().getScreenHeight();
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3);
            client.resizeDisplay();
            sample = 0;
            select(client, LANGUAGES[0]);
            return false;
        }
        if (!reload.isDone() || client.getOverlay() != null) return false;
        reload.join();
        if (++settled < 8) return false;
        if (sample == LANGUAGES.length) return true;
        if (stage == 0) {
            var matches = java.util.stream.IntStream.range(0, 12).mapToObj(i -> new ReplayMatch.Header(
                new java.util.UUID(45, i), header.startedAt() - i * 60_000L, header.updatedAt() - i * 60_000L,
                header.rules(), header.hands(), header.complete(), header.names(), header.finalScores(), header.finalRanks())).toList();
            browser = new ReplayBrowserScreen(null, new ReplayMatch.Index(0, "Replay player", false, matches, false));
            client.setScreen(browser);
            stage = 1; settled = 0;
            return false;
        }
        require(client.screen.width == 320 && client.screen.height == 240, "Replay viewport is not 320x240");
        UiControlsSmoke.verify(client);
        if (stage == 1) {
            Screenshot.grab(output.toFile(), "26-replay-manager-" + LANGUAGES[sample] + "-small.png", client.getMainRenderTarget(), ignored -> {});
            client.setScreen(new DeleteReplayScreen(browser, header));
            stage = 2; settled = 0;
        } else {
            require(86 + client.font.split(Component.translatable("replay.mchjong.delete_note"), 288).size() * 11 < 192,
                "Translated deletion notice overlaps the buttons");
            Screenshot.grab(output.toFile(), "27-replay-delete-" + LANGUAGES[sample] + "-small.png", client.getMainRenderTarget(), ignored -> {});
            sample++; stage = 0;
            if (sample == LANGUAGES.length) {
                client.setScreen(null);
                client.getWindow().setWindowed(windowWidth, windowHeight);
                client.options.guiScale().set(scale);
                client.resizeDisplay();
                select(client, language);
            } else select(client, LANGUAGES[sample]);
        }
        return false;
    }

    private void select(Minecraft client, String language) {
        client.getLanguageManager().setSelected(language);
        reload = client.reloadResourcePacks();
        settled = 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
