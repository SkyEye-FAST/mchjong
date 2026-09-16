package top.skyeyefast.mchjong.neo;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
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
    @SubscribeEvent public static void resourcePacks(AddPackFindersEvent event) {
        event.addPackFinders(MahjongContent.id("resourcepacks/patterned_backs"), PackType.CLIENT_RESOURCES,
            Component.translatable("resourcePack.mchjong.patterned_backs.name"), PackSource.BUILT_IN, false, Pack.Position.TOP);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MahjongContent.TABLE_ENTITY, MahjongTableRenderer::new);
        event.registerEntityRenderer(MahjongContent.SEAT_ENTITY, SeatRenderer::new);
    }
}
