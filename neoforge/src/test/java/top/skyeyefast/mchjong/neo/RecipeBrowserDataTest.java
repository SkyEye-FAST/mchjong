package top.skyeyefast.mchjong.neo;

import java.util.ArrayList;
import java.util.HashSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import top.skyeyefast.mchjong.compat.recipes.SupplySubtype;
import top.skyeyefast.mchjong.engine.RedFives;
import top.skyeyefast.mchjong.item.MahjongCatalog;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class RecipeBrowserDataTest {
    @Test void componentIdentityIsImmutableAndKeepsAllRecipeRelevantDifferences(MinecraftServer server) {
        var keys = MahjongCatalog.entries().stream().map(SupplySubtype::of).toList();
        assertEquals(45, new HashSet<>(keys).size());
        var stocked = MahjongCatalog.entries().stream()
            .filter(stack -> stack.is(MahjongContent.BOX_ITEM) && stack.has(MahjongComponents.BOX_PRESET)).toList();
        assertEquals(3, stocked.size());
        for (var reds : RedFives.values()) {
            var box = stocked.stream().filter(stack -> stack.get(MahjongComponents.BOX_PRESET) == reds).findFirst().orElseThrow();
            var contents = MahjongSupplies.contents(box);
            assertEquals(144, MahjongSupplies.tileCount(contents));
            assertEquals(2, contents.get(MahjongSupplies.DICE_SLOT).getCount());
            assertNotNull(MahjongSupplies.deck(box, false, reds));
            assertTrue(box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().findAny().isEmpty());
        }
        var stock = MahjongSupplies.contents(stocked.getFirst());
        assertEquals(40, stickCount(stock, 100));
        assertEquals(16, stickCount(stock, 1000));
        assertEquals(8, stickCount(stock, 5000));
        assertEquals(4, stickCount(stock, 10000));
        assertEquals(4, stickCount(stock, -10000));
        var blank = new ItemStack(MahjongContent.TILE_ITEM);
        assertEquals(SupplySubtype.of(blank), SupplySubtype.of(MahjongSupplies.tile(TileData.BLANK, 1)));
        var named = blank.copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("A named tile"));
        assertEquals(SupplySubtype.of(blank), SupplySubtype.of(named));
        var immutable = SupplySubtype.of(blank);
        blank.set(DataComponents.BASE_COLOR, DyeColor.RED);
        assertNotEquals(immutable, SupplySubtype.of(blank));
        var box = MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE);
        var full = SupplySubtype.of(box);
        var items = new ArrayList<>(MahjongSupplies.contents(box));
        items.getFirst().shrink(1);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        assertNotEquals(full, SupplySubtype.of(box));
        var red = MahjongSupplies.tile(new TileData(4, TileMaterial.BONE, true), DyeColor.BLUE, 1);
        var five = MahjongSupplies.tile(new TileData(4, TileMaterial.BONE, false), DyeColor.BLUE, 1);
        assertNotEquals(SupplySubtype.of(red), SupplySubtype.of(five));
        five.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        assertNotEquals(SupplySubtype.of(five), SupplySubtype.of(MahjongSupplies.tile(new TileData(4, TileMaterial.BONE, false), DyeColor.BLUE, 1)));
    }

    private static int stickCount(java.util.List<ItemStack> items, int points) {
        return items.stream().filter(stack -> stack.is(MahjongContent.POINT_STICK)
            && stack.getOrDefault(MahjongComponents.POINTS, 0) == points).mapToInt(ItemStack::getCount).sum();
    }
}
