package top.skyeyefast.mchjong.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Shared by the Forge maid mod and its Fabric Orihime port. */
public final class MaidMahjongTask implements IMaidTask {
    public static final ResourceLocation ID = MahjongContent.id("mahjong");

    @Override public ResourceLocation getUid() { return ID; }
    @Override public ItemStack getIcon() { return new ItemStack(MahjongContent.AUTO_TABLE_ITEM); }
    @Override public SoundEvent getAmbientSound(EntityMaid maid) { return null; }
    @Override public boolean enableLookAndRandomWalk(EntityMaid maid) { return false; }

    @Override public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        // The maid API appends its own behaviors to this list.
        return new ArrayList<>(List.of(Pair.of(5, new JoinTable())));
    }

    private static final class JoinTable extends Behavior<EntityMaid> {
        private long nextSearch;

        JoinTable() { super(Map.of()); }

        @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
            return level.getGameTime() >= nextSearch && maid.canBrainMoving() && !MaidTables.hasBinding(maid);
        }

        @Override protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
            nextSearch = gameTime + 20;
            if (!(maid.getOwner() instanceof ServerPlayer owner) || owner.level() != level) return;
            MahjongTableBlockEntity table = null;
            BlockPos stool = null;
            double closest = 16 * 16;
            BlockPos origin = owner.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -8, -8), origin.offset(8, 8, 8))) {
                if (owner.distanceToSqr(pos.getCenter()) > 64
                    || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    || !(level.getBlockEntity(pos) instanceof MahjongTableBlockEntity candidate)) continue;
                int seat = candidate.companionSeat(maid);
                if (seat < 0) continue;
                BlockPos candidateStool = TableGeometry.stool(pos, seat);
                double distance = maid.distanceToSqr(candidateStool.getCenter());
                if (!maid.isWithinRestriction(candidateStool) || distance >= closest) continue;
                table = candidate;
                stool = candidateStool;
                closest = distance;
            }
            if (table == null) return;
            if (MaidTables.sit(maid, table)) {
                maid.getNavigation().stop();
                maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
                MaidTables.bind(maid, table);
            } else {
                BehaviorUtils.setWalkAndLookTargetMemories(maid, stool, 0.6f, 1);
            }
        }
    }
}
