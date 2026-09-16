package top.skyeyefast.mchjong.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MahjongTableBlock extends BaseEntityBlock {
    private static final MapCodec<MahjongTableBlock> CODEC = simpleCodec(MahjongTableBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(box(0, 12, 0, 16, 16, 16), box(2, 0, 2, 14, 12, 14));
    public MahjongTableBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.is(MahjongContent.AUTO_TABLE) ? SHAPE : box(0, 12, 0, 16, 16, 16);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MahjongTableBlockEntity(pos, state); }

    // This is the vanilla/Fabric hook. NeoForge's extended hook delegates to it in this profile.
    @SuppressWarnings("deprecation")
    @Override public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof FurnitureBlockEntity furniture)
            stack.set(top.skyeyefast.mchjong.item.MahjongComponents.WOOD, furniture.wood());
        return stack;
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, MahjongContent.TABLE_ENTITY, MahjongTableBlockEntity::serverTick);
    }

    @Override public void setPlacedBy(Level level, BlockPos center, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(center) instanceof MahjongTableBlockEntity table) {
            table.applyComponentsFromItemStack(stack);
            table.appearanceChanged();
        }
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) if (x != 1 || z != 1) {
            level.setBlock(center.offset(x - 1, 0, z - 1), MahjongContent.SPACE.defaultBlockState()
                .setValue(TableSpaceBlock.X, x).setValue(TableSpaceBlock.Z, z), 3);
        }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table) {
            if (!table.removeEquipment(serverPlayer, hit.getDirection())) table.interact(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (stack.is(MahjongContent.BOX_ITEM) || stack.is(MahjongContent.CLOTH_ITEM) || stack.is(MahjongContent.POINT_STICK)) {
            if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table)
                table.useEquipment(server, stack);
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) {
            if (level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table) table.dropEquipment();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
                BlockPos other = pos.offset(x, 0, z);
                if (level.getBlockState(other).is(MahjongContent.SPACE)) level.removeBlock(other, false);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
