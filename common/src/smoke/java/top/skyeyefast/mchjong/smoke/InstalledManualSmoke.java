package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import top.skyeyefast.mchjong.compat.patchouli.ManualClient;
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
    private static net.minecraft.world.item.ItemStack crafted;
    private InstalledManualSmoke() {}

    static boolean tick(Minecraft client, Path output) {
        if (stage == 0) {
            if (ticks++ < 30) return false;
            ManualSmoke.require(client.screen == null, "Installed Patchouli opened a screen");
            var reminder = net.minecraft.network.chat.Component.translatable("manual.mchjong.recommend.text").getString();
            ManualSmoke.require(ManualSmoke.messages(client).stream().noneMatch(message -> message.getString().startsWith(reminder)),
                "Installed Patchouli sent an automatic recommendation");
            int count = ManualSmoke.messages(client).size();
            ManualClient.tick(true);
            ManualSmoke.require(ManualSmoke.messages(client).size() == count, "Installed Patchouli prompted for installation");
            crafted = client.getSingleplayerServer().submit(() -> {
                var server = client.getSingleplayerServer();
                var recipe = (net.minecraft.world.item.crafting.CraftingRecipe) server.getRecipeManager()
                    .byKey(MahjongContent.id("mahjong_manual")).orElseThrow();
                var player = server.getPlayerList().getPlayer(client.player.getUUID());
                var expected = PatchouliAPI.get().getBookStack(MahjongContent.id("guide"));
                int books = player.getInventory().countItem(expected.getItem());
                ManualSmoke.require(books == 1, "First join did not give one handbook");
                var storage = server.overworld().getDataStorage();
                var gifts = storage.computeIfAbsent(top.skyeyefast.mchjong.compat.patchouli.ManualGift::load,
                    top.skyeyefast.mchjong.compat.patchouli.ManualGift::new, "mchjong_manual_gifts");
                storage.set("mchjong_manual_gifts", top.skyeyefast.mchjong.compat.patchouli.ManualGift.load(
                    gifts.save(new net.minecraft.nbt.CompoundTag())));
                top.skyeyefast.mchjong.compat.patchouli.ManualGift.give(player);
                ManualSmoke.require(player.getInventory().countItem(expected.getItem()) == books, "Repeated join duplicated the handbook");
                var input = grid(List.of(
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GREEN_DYE),
                    top.skyeyefast.mchjong.item.MahjongSupplies.tile(new top.skyeyefast.mchjong.item.TileData(
                        4, top.skyeyefast.mchjong.item.TileMaterial.BONE, true), 1)));
                ManualSmoke.require(recipe.matches(input, server.overworld()), "Handbook ingredients did not match");
                var blank = grid(List.of(
                    input.getItem(0), input.getItem(1), top.skyeyefast.mchjong.item.MahjongSupplies.tile(
                        new top.skyeyefast.mchjong.item.TileData(-1, top.skyeyefast.mchjong.item.TileMaterial.BONE, false), 1)));
                ManualSmoke.require(recipe.matches(blank, server.overworld()), "Blank tile was rejected");
                ManualSmoke.require(!recipe.matches(grid(
                    List.of(input.getItem(0), input.getItem(1))), server.overworld()), "Handbook recipe did not require a tile");
                return recipe.assemble(input, server.registryAccess());
            }).join();
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
            var book = BookRegistry.INSTANCE.books.get(MahjongContent.id("guide"));
            ManualSmoke.require(book != null && !book.getContents().isErrored(), "Book failed to load");
            ManualSmoke.require(book.getContents().categories.size() == 4, "Missing manual chapters");
            entries = book.getContents().entries.values().stream().sorted(java.util.Comparator.comparing(e -> e.getId().toString())).toList();
            ManualSmoke.require(entries.size() == 13, "Missing manual entries");
            ManualSmoke.require(!book.getBookItem().isEmpty(), "Missing book item");
            ManualSmoke.require(net.minecraft.world.item.ItemStack.matches(crafted, book.getBookItem()), "Recipe produced the wrong handbook");
            client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, crafted.copy());
                crafted.getItem().use(player.serverLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
            }).join();
            entry = 0; page = 0; ticks = 0; stage = 4;
            return false;
        }
        if (stage == 4) {
            if (!(client.screen instanceof GuiBook)) {
                ManualSmoke.require(++ticks < 100, "Crafted handbook did not open");
                return false;
            }
            ticks = 0; stage = 3;
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

    private static net.minecraft.world.inventory.CraftingContainer grid(List<net.minecraft.world.item.ItemStack> items) {
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
            @Override public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return false; }
        }, items.size(), 1);
        for (int index = 0; index < items.size(); index++) grid.setItem(index, items.get(index));
        return grid;
    }
}
