package top.skyeyefast.mchjong.compat.create;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Loaded on NeoForge only, and only with the supported Create release present. */
public final class CreateCompat {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MahjongContent.MOD_ID);
    public static final DeferredItem<Item> PRINTING_PLATE = ITEMS.register("mahjong_printing_plate", () ->
        new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_BOX = ITEMS.register("incomplete_mahjong_box", () ->
        new SequencedAssemblyItem(new Item.Properties().stacksTo(1)));

    private CreateCompat() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        NeoForge.EVENT_BUS.addListener(CreateProcessing::deployerRecipe);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() != MahjongContent.TAB_KEY && event.getTabKey() != CreativeModeTabs.FUNCTIONAL_BLOCKS) return;
            event.accept(PRINTING_PLATE.get());
        });
    }
}
