package top.skyeyefast.mchjong.compat.maid.client;

import cn.sh1rocu.touhoulittlemaid.api.event.EntityMountEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.fabric.api.event.Event;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.SeatEntity;

/** The follow-owner preference must not reject a server-synchronized Mahjong mount. */
public final class MaidSeatMounts {
    private MaidSeatMounts() {}

    public static void register() {
        var phase = MahjongContent.id("maid_seat_mount");
        EntityMountEvent.CALLBACK.addPhaseOrdering(Event.DEFAULT_PHASE, phase);
        EntityMountEvent.CALLBACK.register(phase, event -> {
            if (event.isMounting() && event.getEntityMounting() instanceof EntityMaid maid
                && maid.level().isClientSide() && !maid.isRideable()
                && event.getEntityBeingMounted() instanceof SeatEntity) event.setCanceled(false);
        });
    }
}
