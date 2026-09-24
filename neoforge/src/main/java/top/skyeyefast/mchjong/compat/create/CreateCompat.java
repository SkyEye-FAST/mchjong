package top.skyeyefast.mchjong.compat.create;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Loaded on NeoForge only, and only with the supported Create release present. */
public final class CreateCompat {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MahjongContent.MOD_ID);
    public static final DeferredItem<Item> PRINTING_PLATE = ITEMS.register("mahjong_printing_plate", () ->
        new Item(new Item.Properties().stacksTo(1).component(MahjongComponents.FACE_PRESET, TileFacePreset.KANSAI)) {
            @Override public Component getName(ItemStack stack) {
                return Component.translatable("item.mchjong.mahjong_printing_plate",
                    Component.translatable(MahjongSupplies.facePreset(stack).translationKey()));
            }
        });
    public static final DeferredItem<SequencedAssemblyItem> INCOMPLETE_BOX = ITEMS.register("incomplete_mahjong_box", () ->
        new SequencedAssemblyItem(new Item.Properties().stacksTo(1)));

    private CreateCompat() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        NeoForge.EVENT_BUS.addListener(CreateProcessing::deployerRecipe);
        bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() != MahjongContent.TAB_KEY && event.getTabKey() != CreativeModeTabs.FUNCTIONAL_BLOCKS) return;
            event.accept(plate(TileFacePreset.KANSAI));
            event.accept(plate(TileFacePreset.KANTO));
        });
    }

    public static ItemStack plate(TileFacePreset preset) {
        var stack = new ItemStack(PRINTING_PLATE.get());
        stack.set(MahjongComponents.FACE_PRESET, preset);
        return stack;
    }
}
