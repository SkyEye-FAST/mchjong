package top.skyeyefast.mchjong.compat.rei;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCustomDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.client.MahjongBoxScreen;
import top.skyeyefast.mchjong.client.MahjongTableScreen;
import top.skyeyefast.mchjong.client.PointStickScreen;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExamples;
import top.skyeyefast.mchjong.compat.recipes.SupplySubtype;
import top.skyeyefast.mchjong.item.MahjongCatalog;

/** REI crafting examples, entry catalogue, NBT discrimination and screen exclusion zones. */
public class MahjongReiPlugin implements REIClientPlugin {
    @Override public void registerItemComparators(ItemComparatorRegistry registry) {
        // Stable NBT hashes keep equal recipe ingredients equal after REI copies their stacks.
        SupplySubtype.items().forEach(item -> registry.register((context, stack) -> {
            var copy = stack.copyWithCount(1);
            copy.resetHoverName();
            return copy.getTag() == null ? 0 : copy.getTag().hashCode();
        }, item));
    }

    @Override public void registerEntries(EntryRegistry registry) {
        registry.removeEntryIf(entry -> entry.getValue() instanceof ItemStack stack
            && SupplySubtype.items().contains(stack.getItem()));
        registry.addEntries(MahjongCatalog.entries().stream().map(EntryStacks::of).toList());
    }

    @Override public void registerDisplays(DisplayRegistry registry) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (var example : SupplyRecipeExamples.create(level)) {
            var inputs = IntStream.range(0, example.input().size())
                .mapToObj(index -> EntryIngredients.ofItemStacks(example.ingredients(index))).toList();
            registry.add(new DefaultCustomDisplay(example.id(), example.source(), inputs,
                List.of(EntryIngredients.of(example.output()))) {
                @Override public Optional<ResourceLocation> getDisplayLocation() {
                    return Optional.of(example.id());
                }
            });
        }
    }

    @Override public void registerExclusionZones(ExclusionZones zones) {
        bounds(zones, MahjongBoxScreen.class, MahjongBoxScreen::browserBounds);
        bounds(zones, MahjongTableScreen.class, MahjongTableScreen::browserBounds);
        bounds(zones, PointStickScreen.class, PointStickScreen::browserBounds);
    }

    private static <T extends AbstractContainerScreen<?>> void bounds(ExclusionZones zones,
            Class<T> type, Function<T, ScreenRectangle> bounds) {
        zones.register(type, screen -> {
            var area = bounds.apply(screen);
            return List.of(new Rectangle(area.left(), area.top(), area.width(), area.height()));
        });
    }
}
