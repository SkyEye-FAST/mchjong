package top.skyeyefast.mchjong.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class MahjongTableItem extends BlockItem {
    public MahjongTableItem(Block block, Properties properties) { super(block, properties); }

    @Override public Component getName(ItemStack stack) {
        var wood = top.skyeyefast.mchjong.item.MahjongComponents.wood(stack);
        return Component.translatable(getDescriptionId(stack) + "." + wood.getSerializedName());
    }

    @Override public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        boolean clear = level.getBlockState(center.below()).isFaceSturdy(level, center.below(), net.minecraft.core.Direction.UP);
        int radius = TableGeometry.FOOTPRINT_RADIUS;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
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
