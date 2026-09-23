package top.skyeyefast.mchjong.compat.rei;

import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import top.skyeyefast.mchjong.compat.recipes.SupplySubtype;

public class MahjongReiCommonPlugin implements REICommonPlugin {
    @Override public void registerItemComparators(ItemComparatorRegistry registry) {
        SupplySubtype.items().forEach(registry::registerComponents);
    }
}
