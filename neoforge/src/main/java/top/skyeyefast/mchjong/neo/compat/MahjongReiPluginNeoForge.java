package top.skyeyefast.mchjong.neo.compat;

import com.simibubi.create.AllBlocks;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import net.neoforged.fml.ModList;
import top.skyeyefast.mchjong.compat.create.CreateWorkshopRei;
import top.skyeyefast.mchjong.compat.rei.MahjongReiPlugin;

/** NeoForge discovers REI plugins by annotation; Fabric uses the rei_client entrypoint. */
@REIPluginClient
public final class MahjongReiPluginNeoForge extends MahjongReiPlugin {
    @Override public void registerCategories(CategoryRegistry registry) {
        super.registerCategories(registry);
        if (!ModList.get().isLoaded("create")) return;
        registry.add(CreateWorkshopRei.category());
        registry.addWorkstations(CreateWorkshopRei.CATEGORY, EntryStacks.of(AllBlocks.MECHANICAL_PRESS.get()),
            EntryStacks.of(AllBlocks.MECHANICAL_MIXER.get()), EntryStacks.of(AllBlocks.DEPLOYER.get()),
            EntryStacks.of(AllBlocks.MECHANICAL_SAW.get()));
    }

    @Override public void registerDisplays(DisplayRegistry registry) {
        super.registerDisplays(registry);
        if (ModList.get().isLoaded("create")) CreateWorkshopRei.displays().forEach(registry::add);
    }
}
