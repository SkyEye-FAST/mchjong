package top.skyeyefast.mchjong.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class MahjongTableItem extends BlockItem {
    public MahjongTableItem(Block block, Properties properties) { super(block, properties); }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, java.util.List<Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        var wood = stack.getOrDefault(top.skyeyefast.mchjong.item.MahjongComponents.WOOD, top.skyeyefast.mchjong.item.FurnitureWood.OAK);
        lines.add(Component.translatable("block.minecraft." + wood.getSerializedName() + "_planks"));
        lines.add(Component.translatable(getBlock() == MahjongContent.AUTO_TABLE ? "item.mchjong.automatic_table_help" : "item.mchjong.manual_table_help"));
        lines.add(Component.translatable("item.mchjong.equipment_help"));
    }

    @Override public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        boolean clear = level.getBlockState(center.below()).isFaceSturdy(level, center.below(), net.minecraft.core.Direction.UP);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            BlockPos pos = center.offset(x, 0, z);
            clear &= level.getWorldBorder().isWithinBounds(pos) && !level.isOutsideBuildHeight(pos.above())
                && level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.above()).canBeReplaced() && level.getFluidState(pos.above()).isEmpty();
        }
        if (!clear) {
            if (!level.isClientSide && context.getPlayer() != null)
                context.getPlayer().displayClientMessage(Component.translatable("message.mchjong.space"), true);
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}
