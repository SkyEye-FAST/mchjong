package top.skyeyefast.mchjong.compat.rei;

import java.util.List;
import java.util.function.Function;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import top.skyeyefast.mchjong.client.MahjongBoxScreen;
import top.skyeyefast.mchjong.client.MahjongTableScreen;
import top.skyeyefast.mchjong.client.PointStickScreen;
import top.skyeyefast.mchjong.compat.recipes.SupplySubtype;
import top.skyeyefast.mchjong.item.MahjongCatalog;

/** REI entry catalogue, NBT discrimination and screen exclusion zones. */
public class MahjongReiPlugin implements REIClientPlugin {
    @Override public void registerItemComparators(ItemComparatorRegistry registry) {
        SupplySubtype.items().forEach(registry::registerNbt);
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
