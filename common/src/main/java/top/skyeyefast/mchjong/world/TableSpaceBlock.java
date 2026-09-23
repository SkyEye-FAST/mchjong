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
    // Vanilla/Fabric hook; NeoForge's player-aware hook delegates to this method.
    @SuppressWarnings("deprecation")
    @Override public net.minecraft.world.item.ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state,
            boolean includeData) {
        BlockPos center = center(pos, state);
        BlockState table = level.getBlockState(center);
        return table.getBlock() instanceof MahjongTableBlock
            ? table.getCloneItemStack(level, center, includeData) : net.minecraft.world.item.ItemStack.EMPTY;
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = SHAPES[state.getValue(X)][state.getValue(Z)];
        BlockPos center = center(pos, state);
        if (level.getBlockState(center).is(MahjongContent.TABLE)) {
            var cell = new net.minecraft.world.phys.AABB(pos);
            for (int side = 0; side < 4; side++) {
                var drawer = TableGeometry.drawerBounds(side).move(center.getX() + .5, center.getY(), center.getZ() + .5);
                if (drawer.intersects(cell)) shape = net.minecraft.world.phys.shapes.Shapes.or(shape,
                    net.minecraft.world.phys.shapes.Shapes.create(drawer.intersect(cell).move(-pos.getX(), -pos.getY(), -pos.getZ())));
            }
        }
        return shape;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(center(pos, state)) instanceof MahjongTableBlockEntity table) {
            table.use(serverPlayer, hit);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(center(pos, state)) instanceof MahjongTableBlockEntity table && table.drawerAt(hit) >= 0) {
            if (player instanceof ServerPlayer server) table.openSticks(server, table.drawerAt(hit));
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        if (stack.is(MahjongContent.BOX_ITEM) || stack.is(MahjongContent.CLOTH_ITEM)) {
            if (player instanceof ServerPlayer server && level.getBlockEntity(center(pos, state)) instanceof MahjongTableBlockEntity table) {
                if (stack.is(MahjongContent.BOX_ITEM)) table.openStorage(server);
                else table.useEquipment(server, stack);
            }
            return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative()) {
            BlockPos center = center(pos, state);
            if (level.getBlockState(center).getBlock() instanceof MahjongTableBlock) level.destroyBlock(center, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level,
            BlockPos pos, boolean moving) {
        if (!level.getBlockState(pos).is(state.getBlock())) {
            BlockPos center = center(pos, state);
            if (level.getBlockState(center).getBlock() instanceof MahjongTableBlock) level.destroyBlock(center, true);
        }
        super.affectNeighborsAfterRemoval(state, level, pos, moving);
    }
}
