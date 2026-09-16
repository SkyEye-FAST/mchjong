package top.skyeyefast.mchjong.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MahjongStoolBlock extends Block {
    private static final VoxelShape SHAPE = box(2, 0, 2, 14, 10, 14);
    public MahjongStoolBlock(Properties properties) { super(properties); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            for (int seat = 0; seat < 4; seat++) {
                BlockPos center = pos.relative(TableGeometry.SIDES[seat].getOpposite(), 2);
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
