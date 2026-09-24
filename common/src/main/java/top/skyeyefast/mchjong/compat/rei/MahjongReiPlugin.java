package top.skyeyefast.mchjong.compat.rei;

import java.util.List;
import java.util.function.Function;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.Items;
import top.skyeyefast.mchjong.client.MahjongBoxScreen;
import top.skyeyefast.mchjong.client.MahjongTableScreen;
import top.skyeyefast.mchjong.client.PointStickScreen;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExamples;
import top.skyeyefast.mchjong.item.MahjongCatalog;

/** REI supply recipes, entry catalogue, component discrimination and screen exclusion zones. */
public class MahjongReiPlugin implements REIClientPlugin {
    @Override public void registerCategories(CategoryRegistry registry) {
        registry.add(new SupplyReiCategory());
        registry.addWorkstations(SupplyReiDisplay.CATEGORY, EntryStacks.of(Items.CRAFTING_TABLE));
    }

    @Override public void registerDisplays(DisplayRegistry registry) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        SupplyRecipeExamples.create(level).forEach(example -> registry.add(new SupplyReiDisplay(example)));
    }

    @Override public void registerEntries(EntryRegistry registry) {
        MahjongCatalog.entries().forEach(stack -> {
            var entry = EntryStacks.of(stack);
            if (!registry.alreadyContain(entry)) registry.addEntry(entry);
        });
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
