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
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.is(MahjongContent.AUTO_TABLE) ? SHAPE : box(0, 12, 0, 16, 16, 16);
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MahjongTableBlockEntity(pos, state); }

    // This is the vanilla/Fabric hook. NeoForge's extended hook delegates to it in this profile.
    @SuppressWarnings("deprecation")
    @Override public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state,
            boolean includeData) {
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof FurnitureBlockEntity furniture)
            stack.set(top.skyeyefast.mchjong.item.MahjongComponents.WOOD, furniture.wood());
        return stack;
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, MahjongContent.TABLE_ENTITY, MahjongTableBlockEntity::serverTick);
    }

    @Override public void setPlacedBy(Level level, BlockPos center, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(center) instanceof MahjongTableBlockEntity table) {
            table.applyComponentsFromItemStack(stack);
            table.appearanceChanged();
        }
        int radius = TableGeometry.FOOTPRINT_RADIUS;
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) if (x != 0 || z != 0) {
            level.setBlock(center.offset(x, 0, z), MahjongContent.SPACE.defaultBlockState()
                .setValue(TableSpaceBlock.X, x + radius).setValue(TableSpaceBlock.Z, z + radius), 3);
        }
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table) {
            table.use(serverPlayer, hit);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table && table.drawerAt(hit) >= 0) {
            if (player instanceof ServerPlayer server) table.openSticks(server, table.drawerAt(hit));
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        if (stack.is(MahjongContent.BOX_ITEM) || stack.is(MahjongContent.CLOTH_ITEM)) {
            if (player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table) {
                if (stack.is(MahjongContent.BOX_ITEM)) table.openStorage(server);
                else table.useEquipment(server, stack);
            }
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, boolean moving) {
        if (!level.getBlockState(pos).is(state.getBlock())) {
            if (level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table) table.dropEquipment();
            int radius = TableGeometry.FOOTPRINT_RADIUS;
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                BlockPos other = pos.offset(x, 0, z);
                BlockState part = level.getBlockState(other);
                if (part.is(MahjongContent.SPACE) && TableSpaceBlock.center(other, part).equals(pos))
                    level.removeBlock(other, false);
            }
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moving);
    }
}
