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
    @SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent event) {
        top.skyeyefast.mchjong.client.TableKeys.ALL.forEach(event::register);
    }
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        if (net.neoforged.fml.ModList.get().isLoaded("ponder"))
            event.enqueueWork(top.skyeyefast.mchjong.compat.ponder.MchjongPonder::register);
    }
    @SubscribeEvent public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(MahjongContent.BOX_MENU, top.skyeyefast.mchjong.client.MahjongBoxScreen::new);
        event.register(MahjongContent.TABLE_MENU, top.skyeyefast.mchjong.client.MahjongTableScreen::new);
        event.register(MahjongContent.STICK_MENU, top.skyeyefast.mchjong.client.PointStickScreen::new);
    }
    @SubscribeEvent public static void items(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        top.skyeyefast.mchjong.client.HeldSupplyArm.initialize((model, context, pose, leftHand) ->
            model.applyTransform(context, pose, leftHand));
        var renderer = new top.skyeyefast.mchjong.client.MahjongItemRenderer();
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            @Override public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
        }, top.skyeyefast.mchjong.client.MahjongItemRenderer.items());
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        top.skyeyefast.mchjong.client.TableAudio.tick();
        top.skyeyefast.mchjong.client.SeatedCamera.tick();
        top.skyeyefast.mchjong.client.ClientReplays.tick();
    }
    @SubscribeEvent public static void close(net.neoforged.neoforge.event.GameShuttingDownEvent event) {
        top.skyeyefast.mchjong.client.TableAudio.close();
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MahjongContent.TABLE_ENTITY, MahjongTableRenderer::new);
        event.registerBlockEntityRenderer(MahjongContent.STOOL_ENTITY, top.skyeyefast.mchjong.client.FurnitureRenderer::new);
        event.registerEntityRenderer(MahjongContent.SEAT_ENTITY, SeatRenderer::new);
    }
}
