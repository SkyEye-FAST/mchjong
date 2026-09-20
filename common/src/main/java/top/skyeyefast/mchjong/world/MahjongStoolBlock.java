package top.skyeyefast.mchjong.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MahjongStoolBlock extends BaseEntityBlock {
    private static final MapCodec<MahjongStoolBlock> CODEC = simpleCodec(MahjongStoolBlock::new);
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, TableGeometry.STOOL_HEIGHT * 16, 14);
    public MahjongStoolBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FurnitureBlockEntity(pos, state); }

    // This is the vanilla/Fabric hook. NeoForge's extended hook delegates to it in this profile.
    @SuppressWarnings("deprecation")
    @Override public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof FurnitureBlockEntity furniture) {
            stack.set(top.skyeyefast.mchjong.item.MahjongComponents.WOOD, furniture.wood());
            stack.set(net.minecraft.core.component.DataComponents.BASE_COLOR, furniture.color());
        }
        return stack;
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof FurnitureBlockEntity furniture) {
            furniture.applyComponentsFromItemStack(stack);
            furniture.appearanceChanged();
        }
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Sneak is also vanilla's dismount input; do not start riding while it is held.
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            for (int seat = 0; seat < 4; seat++) {
                BlockPos center = pos.relative(TableGeometry.SIDES[seat].getOpposite(), TableGeometry.STOOL_DISTANCE);
                if (level.getBlockEntity(center) instanceof MahjongTableBlockEntity table) {
                    table.sit(serverPlayer, seat);
                    return InteractionResult.CONSUME;
                }
            }
            serverPlayer.displayClientMessage(Component.translatable("message.mchjong.no_table"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
