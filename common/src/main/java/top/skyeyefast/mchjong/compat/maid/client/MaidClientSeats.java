package top.skyeyefast.mchjong.compat.maid.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Restores client mounts for table maids while preserving their vehicle-follow setting. */
public final class MaidClientSeats {
    private MaidClientSeats() {}

    public static void tick() {
        var client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof SeatEntity seat)) continue;
            String rider = seat.riderId();
            if (seat.isVehicle() || rider.isEmpty()) continue;
            for (var maid : client.level.getEntitiesOfClass(EntityMaid.class, seat.getBoundingBox().inflate(3))) {
                if (!maid.getUUID().toString().equals(rider)) continue;
                boolean rideable = maid.isRideable();
                maid.setRideable(true);
                try { maid.startRiding(seat, true, false); }
                finally { maid.setRideable(rideable); }
                break;
            }
        }
    }
}
