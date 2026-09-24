package top.skyeyefast.mchjong.compat.maid;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.api.entity.data.TaskDataKey;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Maintains the physical seat across task changes and world saves, independently of work schedules. */
public final class MaidTables extends Behavior<EntityMaid> {
    static TaskDataKey<CompoundTag> bindingKey;
    private long nextCheck;

    MaidTables() { super(Map.of()); }

    static boolean hasBinding(EntityMaid maid) {
        var binding = maid.getData(bindingKey);
        return binding != null && binding.hasUUID("table");
    }

    static void bind(EntityMaid maid, MahjongTableBlockEntity table) {
        var binding = new CompoundTag();
        binding.putLong("pos", table.getBlockPos().asLong());
        binding.putString("dimension", maid.level().dimension().location().toString());
        binding.putUUID("table", table.companionTableId(maid.getUUID()));
        binding.putBoolean("follow_owner_vehicle", maid.isRideable());
        maid.setData(bindingKey, binding);
        // The maid's vehicle-follow behavior otherwise dismounts her when the owner changes stools.
        maid.setRideable(false);
    }

    static boolean sit(EntityMaid maid, MahjongTableBlockEntity table) {
        boolean followVehicles = maid.isRideable();
        // Forge also checks this setting in its mount event, including restored seats.
        maid.setRideable(true);
        try { return table.sitCompanion(maid); }
        finally { maid.setRideable(followVehicles); }
    }

    @Override protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        return level.getGameTime() >= nextCheck && hasBinding(maid);
    }

    @Override protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        nextCheck = gameTime + 20;
        var binding = maid.getData(bindingKey);
        var dimension = ResourceLocation.tryParse(binding.getString("dimension"));
        ServerLevel tableLevel = dimension == null ? null : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        var pos = BlockPos.of(binding.getLong("pos"));
        // Never load a chunk to keep a companion at a table.
        MahjongTableBlockEntity table = tableLevel != null && tableLevel.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
            && tableLevel.getBlockEntity(pos) instanceof MahjongTableBlockEntity found ? found : null;
        boolean playing = maid.isAlive() && !maid.isMaidInSittingPose() && MaidMahjongTask.ID.equals(maid.getTask().getUid())
            && tableLevel == level && binding.hasUUID("table");
        if (playing && table == null && !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return;
        if (playing && table != null && binding.getUUID("table").equals(table.companionTableId(maid.getUUID()))) {
            if (maid.getVehicle() instanceof SeatEntity seat && seat.tablePos().equals(pos)) return;
            // Seat vehicles are transient; the companion's saved binding restores the mount.
            if (!maid.isPassenger() && sit(maid, table)) return;
        }
        if (table != null && binding.hasUUID("table")) table.leaveCompanion(binding.getUUID("table"), maid.getUUID());
        if (maid.getVehicle() instanceof SeatEntity seat && seat.tablePos().equals(pos)) maid.stopRiding();
        maid.setRideable(binding.getBoolean("follow_owner_vehicle"));
        maid.setData(bindingKey, new CompoundTag());
    }
}
