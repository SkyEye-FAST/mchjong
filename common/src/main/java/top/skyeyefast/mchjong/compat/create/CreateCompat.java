package top.skyeyefast.mchjong.compat.create;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Initialized by the loader's item registration only when Create is present. */
public final class CreateCompat {
    public static final Item PRINTING_PLATE = new Item(new Item.Properties().stacksTo(1)) {
        @Override public Component getName(ItemStack stack) {
            return Component.translatable("item.mchjong.mahjong_printing_plate",
                Component.translatable(MahjongSupplies.facePreset(stack).translationKey()));
        }
    };
    public static final Item INCOMPLETE_BOX = new SequencedAssemblyItem(new Item.Properties().stacksTo(1));

    private CreateCompat() {}

    public static ItemStack plate(TileFacePreset preset) {
        var stack = new ItemStack(PRINTING_PLATE);
        MahjongComponents.facePreset(stack, preset);
        return stack;
    }
}
