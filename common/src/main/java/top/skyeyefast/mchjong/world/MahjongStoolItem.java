package top.skyeyefast.mchjong.world;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import top.skyeyefast.mchjong.item.MahjongSupplies;

public final class MahjongStoolItem extends BlockItem {
    public MahjongStoolItem(Block block, Properties properties) { super(block, properties); }

    @Override public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId() + "." + MahjongSupplies.color(stack).getName());
    }
}
