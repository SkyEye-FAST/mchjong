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
    private static final List<String> LANGUAGES = List.of("en_us", "ja_jp", "zh_cn", "zh_tw");
    private static List<vazkii.patchouli.client.book.BookEntry> entries;
    private static final java.util.List<String> layoutErrors = new java.util.ArrayList<>();
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
                    .byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
                        MahjongContent.id("mahjong_manual"))).orElseThrow().value();
                var player = server.getPlayerList().getPlayer(client.player.getUUID());
                var expected = PatchouliAPI.get().getBookStack(MahjongContent.id("guide"));
                int books = player.getInventory().countItem(expected.getItem());
                ManualSmoke.require(books == 1, "First join did not give one handbook");
                var storage = server.overworld().getDataStorage();
                var gifts = storage.computeIfAbsent(top.skyeyefast.mchjong.compat.patchouli.ManualGift.TYPE);
                var codec = top.skyeyefast.mchjong.compat.patchouli.ManualGift.CODEC;
                storage.set(top.skyeyefast.mchjong.compat.patchouli.ManualGift.TYPE,
                    codec.parse(net.minecraft.nbt.NbtOps.INSTANCE,
                        codec.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, gifts).getOrThrow()).getOrThrow());
                top.skyeyefast.mchjong.compat.patchouli.ManualGift.give(player);
                ManualSmoke.require(player.getInventory().countItem(expected.getItem()) == books, "Repeated join duplicated the handbook");
                var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 1, List.of(
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK),
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GREEN_DYE),
                    top.skyeyefast.mchjong.item.MahjongSupplies.tile(new top.skyeyefast.mchjong.item.TileData(
                        4, top.skyeyefast.mchjong.item.TileMaterial.BONE, true), 1)));
                ManualSmoke.require(recipe.matches(input, server.overworld()), "Handbook ingredients did not match");
                var blank = net.minecraft.world.item.crafting.CraftingInput.of(3, 1, List.of(
                    input.getItem(0), input.getItem(1), top.skyeyefast.mchjong.item.MahjongSupplies.tile(
                        new top.skyeyefast.mchjong.item.TileData(-1, top.skyeyefast.mchjong.item.TileMaterial.BONE, false), 1)));
                ManualSmoke.require(recipe.matches(blank, server.overworld()), "Blank tile was rejected");
                ManualSmoke.require(!recipe.matches(net.minecraft.world.item.crafting.CraftingInput.of(2, 1,
                    List.of(input.getItem(0), input.getItem(1))), server.overworld()), "Handbook recipe did not require a tile");
                return recipe.assemble(input);
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
            var resources = client.getResourceManager();
            int categories = resources.listResources("patchouli_books/guide/en_us/categories", id -> id.getNamespace().equals("mchjong") && id.getPath().endsWith(".json")).size();
            ManualSmoke.require(book.getContents().categories.size() == categories, "Missing manual chapters");
            for (String chapter : List.of("rules", "mcr", "sichuan", "taiwan"))
                ManualSmoke.require(book.getContents().categories.containsKey(MahjongContent.id(chapter)), "Missing rules entrance: " + chapter);
            entries = book.getContents().entries.values().stream().sorted(java.util.Comparator.comparing(e -> e.getId().toString())).toList();
            int expected = resources.listResources("patchouli_books/guide/en_us/entries", id -> id.getNamespace().equals("mchjong") && id.getPath().endsWith(".json")).size();
            ManualSmoke.require(entries.size() == expected, "Missing manual entries");
            ManualSmoke.require(!book.getBookItem().isEmpty(), "Missing book item");
            ManualSmoke.require(client.getModelManager().getItemModel(book.model)
                != client.getModelManager().getItemModel(MahjongContent.id("missing_manual")), "Missing handbook item model");
            ManualSmoke.require(net.minecraft.world.item.ItemStack.matches(crafted, book.getBookItem()), "Recipe produced the wrong handbook");
            client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, crafted.copy());
                crafted.getItem().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
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
            checkExplanationLinks(client);
            show();
            return false;
        }
        if (++ticks < 5) return false;
        ManualSmoke.require(client.screen instanceof GuiBook, "Entry failed to render");
        if (entries.get(entry).getId().getPath().equals("tiles") && page == 0)
            SmokeScreenshots.grab(output.toFile(), "manual-" + LANGUAGES.get(language) + ".png", client.getMainRenderTarget(), 1, message -> {});
        checkSpread(client);
        SmokeScreenshots.grab(output.toFile(), "manual-" + LANGUAGES.get(language) + "-" + entries.get(entry).getId().getPath() + "-" + page + ".png", client.getMainRenderTarget(), 1, message -> {});
        page += 2;
        if (page >= entries.get(entry).getPages().size()) { page = 0; entry++; }
        if (entry == entries.size()) {
            if (++language == LANGUAGES.size()) {
                try { java.nio.file.Files.write(output.resolve("manual-layout.txt"), layoutErrors); }
                catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
                ManualSmoke.require(layoutErrors.isEmpty(), "Handbook clipping: " + String.join("; ", layoutErrors));
                client.setScreen(null); return true;
            }
            stage = 1;
        } else show();
        ticks = 0;
        return false;
    }

    private static void checkSpread(Minecraft client) {
        var selected = entries.get(entry);
        for (int index = page; index < Math.min(page + 2, selected.getPages().size()); index++) {
            var current = selected.getPages().get(index);
            if (!(current instanceof vazkii.patchouli.client.book.page.abstr.PageWithText)) continue;
            String location = LANGUAGES.get(language) + ":" + selected.getId() + ":" + index;
            try {
                var field = vazkii.patchouli.client.book.page.abstr.PageWithText.class.getDeclaredField("text");
                field.setAccessible(true);
                String key = ((vazkii.patchouli.api.IVariable) field.get(current)).as(net.minecraft.network.chat.Component.class).getString();
                String text = net.minecraft.client.resources.language.I18n.get(key);
                ManualSmoke.require(!text.startsWith("manual."), "Untranslated page: " + key);
                var gui = (GuiBook) client.screen;
                int y = ((vazkii.patchouli.client.book.page.abstr.PageWithText) current).getTextHeight();
                var parser = new vazkii.patchouli.client.book.text.BookTextParser(gui, gui.book, 0, y,
                    GuiBook.PAGE_WIDTH, GuiBook.TEXT_LINE_HEIGHT, gui.book.getFontStyle());
                var layout = new vazkii.patchouli.client.book.text.TextLayouter(gui, 0, y, GuiBook.TEXT_LINE_HEIGHT,
                    GuiBook.PAGE_WIDTH, vazkii.patchouli.api.PatchouliConfigAccess.TextOverflowMode.OVERFLOW);
                layout.layout(client.font, parser.parse(net.minecraft.network.chat.Component.literal(text)));
                var words = layout.getWords();
                for (Object value : words) {
                    var word = (vazkii.patchouli.client.book.text.Word) value;
                    // Patchouli's hitbox width includes the unsplit span; measure rendered text.
                    var wordText = word.getClass().getDeclaredField("text");
                    wordText.setAccessible(true);
                    var renderedText = (net.minecraft.network.chat.Component) wordText.get(word);
                    int renderedWidth = client.font.width(net.minecraft.network.chat.Component.literal(renderedText.getString().stripTrailing()).withStyle(renderedText.getStyle()));
                    if (word.x < 0 || word.x + renderedWidth > GuiBook.PAGE_WIDTH || word.y + word.height > GuiBook.PAGE_HEIGHT) {
                        layoutErrors.add(location + " text bounds " + word.x + "," + word.y + "," + renderedWidth + " text=" + renderedText.getString());
                        break;
                    }
                }
                if (index == 0 && client.font.width(selected.getName()) > GuiBook.PAGE_WIDTH)
                    layoutErrors.add(location + " title too wide");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(location, failure); }
        }
    }

    private static void show() {
        var selected = entries.get(entry);
        ManualSmoke.require(!selected.getName().getString().startsWith("manual."), "Untranslated manual entry");
        PatchouliAPI.get().openBookEntry(MahjongContent.id("guide"), selected.getId(), page);
    }
    private static void checkExplanationLinks(Minecraft client) {
        var parent = client.screen;
        String[] keys = {"yaku.mchjong.double_riichi", "mcr.mchjong.fan.big_three_dragons", "sichuan.mchjong.fan.golden_single_wait", "taiwan.mchjong.pattern.earthly_win"};
        String[] targets = {"riichi_yaku", "mcr_fan_high", "sichuan_fan", "taiwan_tai"};
        for (int i = 0; i < keys.length; i++) {
            top.skyeyefast.mchjong.client.RuleHelp.openAward(parent, net.minecraft.network.chat.Component.translatable(keys[i]));
            ManualSmoke.require(client.screen instanceof top.skyeyefast.mchjong.client.TableHelpScreen, "Missing basic explanation: " + keys[i]);
            var help = client.screen;
            var button = help.children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance)
                .map(net.minecraft.client.gui.components.AbstractWidget.class::cast).filter(widget -> widget.getMessage().getString().equals(
                    net.minecraft.network.chat.Component.translatable("rules.mchjong.manual").getString())).findFirst().orElseThrow();
            help.setFocused(button); help.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
            ManualSmoke.require(client.screen instanceof vazkii.patchouli.client.book.gui.GuiBookEntry, "Explanation did not open handbook");
            var entryScreen = (vazkii.patchouli.client.book.gui.GuiBookEntry) client.screen;
            ManualSmoke.require(entryScreen.getEntry().getId().equals(MahjongContent.id(targets[i])) && entryScreen.getSpread() == 1, "Incorrect handbook destination: " + keys[i]);
            client.screen.onClose(); ManualClient.tick(true);
            ManualSmoke.require(client.screen == help, "Handbook did not return to explanation");
            help.onClose(); ManualSmoke.require(client.screen == parent, "Explanation did not return to its parent");
        }
    }
}
