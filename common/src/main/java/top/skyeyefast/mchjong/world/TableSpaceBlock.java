package top.skyeyefast.mchjong.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Reserves the complete footprint; each collider is clipped to the visible tabletop. */
public final class TableSpaceBlock extends Block {
    private static final int RADIUS = TableGeometry.FOOTPRINT_RADIUS;
    public static final IntegerProperty X = IntegerProperty.create("x", 0, RADIUS * 2);
    public static final IntegerProperty Z = IntegerProperty.create("z", 0, RADIUS * 2);
    private static final VoxelShape[][] SHAPES = shapes();

    private static VoxelShape[][] shapes() {
        var shapes = new VoxelShape[RADIUS * 2 + 1][RADIUS * 2 + 1];
        double half = TableGeometry.OUTER_HALF_WIDTH;
        for (int x = 0; x <= RADIUS * 2; x++) for (int z = 0; z <= RADIUS * 2; z++) {
            double originX = x - RADIUS - .5;
            double originZ = z - RADIUS - .5;
            shapes[x][z] = box(Math.max(0, -half - originX) * 16, 12, Math.max(0, -half - originZ) * 16,
                Math.min(1, half - originX) * 16, 16, Math.min(1, half - originZ) * 16);
        }
        return shapes;
    }

    public TableSpaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(X, RADIUS).setValue(Z, RADIUS));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(X, Z); }
    public static BlockPos center(BlockPos pos, BlockState state) { return pos.offset(RADIUS - state.getValue(X), 0, RADIUS - state.getValue(Z)); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(X)][state.getValue(Z)];
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(center(pos, state)) instanceof MahjongTableBlockEntity table) {
            if (!table.removeEquipment(serverPlayer, hit.getDirection())) {
                if (player.isShiftKeyDown()) table.open(serverPlayer);
                else table.openStorage(serverPlayer);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (stack.is(MahjongContent.BOX_ITEM) || stack.is(MahjongContent.CLOTH_ITEM) || stack.is(MahjongContent.POINT_STICK)) {
            if (player instanceof ServerPlayer server && level.getBlockEntity(center(pos, state)) instanceof MahjongTableBlockEntity table) {
                if (stack.is(MahjongContent.BOX_ITEM)) table.openStorage(server);
                else table.useEquipment(server, stack);
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos center = center(pos, state);
            if (level.getBlockState(center).getBlock() instanceof MahjongTableBlock) level.destroyBlock(center, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!level.isClientSide && !state.is(replacement.getBlock())) {
            BlockPos center = center(pos, state);
            if (level.getBlockState(center).getBlock() instanceof MahjongTableBlock) level.destroyBlock(center, true);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
