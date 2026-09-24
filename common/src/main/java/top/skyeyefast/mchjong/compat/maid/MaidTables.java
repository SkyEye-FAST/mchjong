package top.skyeyefast.mchjong.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Maintains saved companion seats through the loader's persistent attachment boundary. */
public final class MaidTables extends Behavior<EntityMaid> {
    private long nextCheck;

    MaidTables() { super(Map.of()); }
    static boolean hasBinding(EntityMaid maid) { return MaidData.get(maid) != null; }
    static void bind(EntityMaid maid, MahjongTableBlockEntity table) {
        MaidData.set(maid, new MaidBinding(table.getBlockPos().asLong(), maid.level().dimension().identifier(),
            table.companionTableId(maid.getUUID()), maid.isRideable()));
        maid.setRideable(false);
    }
    static boolean sit(EntityMaid maid, MahjongTableBlockEntity table) {
        boolean follow = maid.isRideable();
        maid.setRideable(true);
        try { return table.sitCompanion(maid); }
        finally { maid.setRideable(follow); }
    }
    @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return level.getGameTime() >= nextCheck && hasBinding(maid);
    }
    @Override protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        nextCheck = gameTime + 20;
        var binding = MaidData.get(maid);
        if (binding == null) return;
        ServerLevel tableLevel = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, binding.dimension()));
        var pos = BlockPos.of(binding.pos());
        MahjongTableBlockEntity table = tableLevel != null && tableLevel.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && tableLevel.getBlockEntity(pos) instanceof MahjongTableBlockEntity found ? found : null;
        boolean playing = maid.isAlive() && !maid.isMaidInSittingPose() && MaidMahjongTask.ID.equals(maid.getTask().getUid())
            && tableLevel == level;
        if (playing && table == null && !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
        if (playing && table != null && binding.table().equals(table.companionTableId(maid.getUUID()))) {
            if (maid.getVehicle() instanceof SeatEntity seat && seat.tablePos().equals(pos)) return;
            if (!maid.isPassenger() && sit(maid, table)) return;
        }
        if (table != null) table.leaveCompanion(binding.table(), maid.getUUID());
        if (maid.getVehicle() instanceof SeatEntity seat && seat.tablePos().equals(pos)) maid.stopRiding();
        maid.setRideable(binding.followVehicles());
        MaidData.clear(maid);
    }
}
