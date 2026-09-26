package top.skyeyefast.mchjong.compat.maid.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMountEvent;
import top.skyeyefast.mchjong.world.SeatEntity;

/** The follow-owner preference must not reject a server-synchronized Mahjong mount. */
public final class MaidSeatMounts {
    private MaidSeatMounts() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, EntityMountEvent.class, event -> {
            if (event.isMounting() && event.getEntityMounting() instanceof EntityMaid maid
                && maid.level().isClientSide && !maid.isRideable()
                && event.getEntityBeingMounted() instanceof SeatEntity) event.setCanceled(false);
        });
    }
}
