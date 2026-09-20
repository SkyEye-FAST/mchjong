package top.skyeyefast.mchjong.compat.rei;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import top.skyeyefast.mchjong.item.MahjongCatalog;
import top.skyeyefast.mchjong.item.MahjongComponents;

/** REI only needs explicit catalogue entries; the physical stock remains loader-independent. */
public class MahjongReiPlugin implements REIClientPlugin {
    @Override public void registerEntries(EntryRegistry registry) {
        MahjongCatalog.entries().stream().filter(stack -> stack.has(MahjongComponents.BOX_PRESET)).forEach(stack -> {
            var entry = EntryStacks.of(stack);
            if (!registry.alreadyContain(entry)) registry.addEntry(entry);
        });
    }
}
