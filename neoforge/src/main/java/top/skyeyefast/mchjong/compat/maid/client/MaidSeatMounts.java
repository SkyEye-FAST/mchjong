package top.skyeyefast.mchjong.compat.maid.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import top.skyeyefast.mchjong.world.SeatEntity;

/** The follow-owner preference must not reject a server-synchronized Mahjong mount. */
public final class MaidSeatMounts {
    private MaidSeatMounts() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityMountEvent.class, event -> {
            if (event.isMounting() && event.getEntityMounting() instanceof EntityMaid maid
                && maid.level().isClientSide && !maid.isRideable()
                && event.getEntityBeingMounted() instanceof SeatEntity) event.setCanceled(false);
        });
    }
}
