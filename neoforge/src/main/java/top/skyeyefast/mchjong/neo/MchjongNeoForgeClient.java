package top.skyeyefast.mchjong.neo;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import top.skyeyefast.mchjong.client.MahjongTableRenderer;
import top.skyeyefast.mchjong.client.SeatRenderer;
import top.skyeyefast.mchjong.world.MahjongContent;

@EventBusSubscriber(modid = MahjongContent.MOD_ID, value = Dist.CLIENT)
public final class MchjongNeoForgeClient {
    private MchjongNeoForgeClient() {}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        top.skyeyefast.mchjong.client.TableAudio.tick();
        top.skyeyefast.mchjong.client.ClientReplays.tick();
    }
    @SubscribeEvent public static void close(net.neoforged.neoforge.event.GameShuttingDownEvent event) {
        top.skyeyefast.mchjong.client.TableAudio.close();
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MahjongContent.TABLE_ENTITY, MahjongTableRenderer::new);
        event.registerEntityRenderer(MahjongContent.SEAT_ENTITY, SeatRenderer::new);
    }
}
