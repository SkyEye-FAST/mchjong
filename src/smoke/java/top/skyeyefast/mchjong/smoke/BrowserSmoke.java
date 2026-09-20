package top.skyeyefast.mchjong.smoke;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.GameType;
import top.skyeyefast.mchjong.client.MahjongBoxScreen;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExample;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExamples;
import top.skyeyefast.mchjong.compat.recipes.SupplySubtype;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.item.MahjongCatalog;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.recipe.SupplyCraftingRecipe;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Real viewer lookups, one recipe page and one server-backed container. */
final class BrowserSmoke {
    private final String browser = System.getProperty("mchjong.smoke.browser", "none");
    private BrowserDriver driver;
    private CompletableFuture<Boolean> serverWork;
    private SupplyRecipeExample flower;
    private int stage, ticks, width, height, scale;

    boolean tick(Minecraft client, Path output) throws java.io.IOException {
        if (stage == 4) return true;
        if (browser.equals("none")) {
            Files.writeString(output.resolve("browser-checks.txt"), "Base client ran with recipe browsers absent.\n");
            stage = 4;
            return true;
        }
        if (driver == null) driver = browser.equals("jei") ? new JeiBrowserSmoke() : new EmiBrowserSmoke();
        if (client.getOverlay() != null) return false;
        check(++ticks < 600, "Browser smoke timed out at stage " + stage);
        if (!driver.ready()) return false;
        if (stage == 0) {
            verifyCatalogue();
            var examples = SupplyRecipeExamples.create(client.level);
            flower = pick(examples, e -> e.output().is(MahjongContent.TILE_ITEM) && MahjongSupplies.tile(e.output()).flower());
            var red = pick(examples, e -> e.output().is(MahjongContent.TILE_ITEM) && MahjongSupplies.tile(e.output()).red());
            var upgrade = pick(examples, e -> e.source().value() instanceof SupplyCraftingRecipe recipe
                && recipe.operation() == SupplyCraftingRecipe.Operation.UPGRADE_TABLE);
            for (var example : List.of(flower, red, upgrade)) {
                check(driver.query(example.output(), true).contains(example.id()), "Missing output lookup: " + example.id());
                var ingredient = example == upgrade ? example.input().get(4) : example.input().getFirst();
                check(driver.query(ingredient, false).contains(example.id()), "Missing input lookup: " + example.id());
            }
            width = client.getWindow().getScreenWidth(); height = client.getWindow().getScreenHeight();
            scale = client.options.guiScale().get();
            serverWork = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                player.closeContainer();
                player.stopRiding();
                player.setGameMode(GameType.SURVIVAL);
                player.getInventory().setItem(0, MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE));
                return true;
            });
            stage = 1; ticks = 0;
        } else if (stage == 1) {
            if (!serverWork.isDone() || client.gameMode.hasInfiniteItems()) return false;
            serverWork.join();
            client.getWindow().setWindowed(1280, 800);
            client.options.guiScale().set(2); client.resizeDisplay();
            client.setScreen(new InventoryScreen(client.player));
            driver.showRecipe(flower.id());
            stage = 2; ticks = 0;
        } else if (stage == 2 && ticks >= 20) {
            Screenshot.grab(output.toFile(), "60-browser-" + browser + "-recipe.png", client.getMainRenderTarget(), ignored -> {});
            client.getWindow().setWindowed(960, 720);
            client.options.guiScale().set(3); client.resizeDisplay();
            serverWork = client.getSingleplayerServer().submit(() -> {
                var player = client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());
                player.openMenu(new SimpleMenuProvider((id, inventory, owner) -> new MahjongBoxMenu(id, inventory, 0),
                    Component.translatable("item.mchjong.mahjong_box")));
                return true;
            });
            stage = 3; ticks = 0;
        } else if (stage == 3 && ticks >= 20) {
            if (!serverWork.isDone()) return false;
            serverWork.join();
            if (!(client.screen instanceof MahjongBoxScreen screen)) return false;
            var bounds = screen.browserBounds();
            check(bounds.top() >= 0 && bounds.bottom() <= screen.height - 24, "Container overlaps browser controls");
            Screenshot.grab(output.toFile(), "60-browser-" + browser + "-container.png", client.getMainRenderTarget(), ignored -> {});
            Files.writeString(output.resolve("browser-checks.txt"), browser + ": catalogue order, point denominations, flower/red back dyes, component-preserving upgrade lookups and native container bounds passed.\n");
            client.player.closeContainer();
            client.getWindow().setWindowed(width, height);
            client.options.guiScale().set(scale); client.resizeDisplay();
            stage = 4;
            return true;
        }
        return false;
    }

    private void verifyCatalogue() {
        var entries = driver.catalogue().stream().filter(stack -> SupplySubtype.items().contains(stack.getItem())).toList();
        for (var stack : MahjongCatalog.entries())
            check(entries.stream().anyMatch(actual -> SupplySubtype.of(actual).equals(SupplySubtype.of(stack))), "Missing catalogue subtype: " + stack);
        var boxes = new ArrayList<Integer>();
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).is(MahjongContent.BOX_ITEM)) boxes.add(i);
        check(boxes.size() == 2 && boxes.get(1) == boxes.get(0) + 1, "Empty/full boxes are not adjacent");
        check(MahjongSupplies.tileCount(MahjongSupplies.contents(entries.get(boxes.getFirst()))) == 0, "Full box precedes empty box");
        check(entries.stream().filter(stack -> stack.is(MahjongContent.POINT_STICK)).map(stack -> stack.getOrDefault(MahjongComponents.POINTS, 0)).toList()
            .equals(List.of(-10000, 0, 100, 1000, 5000, 10000)), "Viewer merged or reordered point denominations");
    }

    private static SupplyRecipeExample pick(List<SupplyRecipeExample> examples, Predicate<SupplyRecipeExample> predicate) {
        return examples.stream().filter(predicate).findFirst().orElseThrow(() -> new IllegalStateException("Missing recipe example"));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
