package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import top.skyeyefast.mchjong.compat.patchouli.ManualClient;
import top.skyeyefast.mchjong.compat.patchouli.ManualRecommendationScreen;
import top.skyeyefast.mchjong.compat.patchouli.PatchouliBook;
import top.skyeyefast.mchjong.world.MahjongContent;
import vazkii.patchouli.api.PatchouliAPI;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.common.book.BookRegistry;

/** Internal Patchouli inspection is confined to the development-only smoke fixture. */
final class InstalledManualSmoke {
    private static int stage, language, entry, page, ticks;
    private static final List<String> LANGUAGES = List.of("en_us", "zh_cn");
    private static List<vazkii.patchouli.client.book.BookEntry> entries;
    private static CompletableFuture<Void> reload;
    private InstalledManualSmoke() {}

    static boolean tick(Minecraft client, Path output) {
        if (stage == 0) {
            ManualSmoke.require(!(client.screen instanceof ManualRecommendationScreen), "Installed Patchouli prompted for installation");
            client.setScreen(null);
            net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(ManualClient.OPEN.saveString()));
            ManualClient.tick(true, PatchouliBook::open);
            ManualSmoke.require(client.screen instanceof GuiBook, "Manual binding did not open Patchouli");
            stage = 1;
        }
        if (stage == 1) {
            client.setScreen(null);
            client.getLanguageManager().setSelected(LANGUAGES.get(language));
            reload = client.reloadResourcePacks();
            stage = 2;
            return false;
        }
        if (stage == 2) {
            if (!reload.isDone() || client.getOverlay() != null) return false;
            reload.join();
            PatchouliBook.open();
            var book = BookRegistry.INSTANCE.books.get(MahjongContent.id("guide"));
            ManualSmoke.require(book != null && !book.getContents().isErrored(), "Book failed to load");
            ManualSmoke.require(book.getContents().categories.size() == 4, "Missing manual chapters");
            entries = book.getContents().entries.values().stream().sorted(java.util.Comparator.comparing(e -> e.getId().toString())).toList();
            ManualSmoke.require(entries.size() == 13, "Missing manual entries");
            ManualSmoke.require(!book.getBookItem().isEmpty(), "Missing book item");
            entry = 0; page = 0; ticks = 0; stage = 3;
            show();
            return false;
        }
        if (++ticks < 5) return false;
        ManualSmoke.require(client.screen instanceof GuiBook, "Entry failed to render");
        if (entries.get(entry).getId().getPath().equals("tiles") && page == 0)
            SmokeScreenshots.grab(output.toFile(), "manual-" + LANGUAGES.get(language) + ".png", client.getMainRenderTarget(), message -> {});
        page += 2;
        if (page >= entries.get(entry).getPages().size()) { page = 0; entry++; }
        if (entry == entries.size()) {
            if (++language == LANGUAGES.size()) { client.setScreen(null); return true; }
            stage = 1;
        } else show();
        ticks = 0;
        return false;
    }

    private static void show() {
        var selected = entries.get(entry);
        ManualSmoke.require(!selected.getName().getString().startsWith("manual."), "Untranslated manual entry");
        PatchouliAPI.get().openBookEntry(MahjongContent.id("guide"), selected.getId(), page);
    }
}
