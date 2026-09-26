package top.skyeyefast.mchjong.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.skyeyefast.mchjong.client.*;
import top.skyeyefast.mchjong.forge.mixin.ItemRenderProperties;
import top.skyeyefast.mchjong.world.MahjongContent;

@Mod.EventBusSubscriber(modid = MahjongContent.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MchjongForgeClient {
    private static final boolean PATCHOULI = net.minecraftforge.fml.ModList.get().isLoaded("patchouli");
    private MchjongForgeClient() {}

    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        event.register(RiichiStickModel.ID);
    }

    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) TileFacePresets::reload);
    }

    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        TableKeys.ALL.forEach(event::register);
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (net.minecraftforge.fml.ModList.get().isLoaded("touhou_little_maid"))
                top.skyeyefast.mchjong.compat.maid.client.MaidSeatMounts.register();
            if (net.minecraftforge.fml.ModList.get().isLoaded("create"))
                top.skyeyefast.mchjong.compat.create.CreatePonder.register();
            else if (net.minecraftforge.fml.ModList.get().isLoaded("ponder"))
                top.skyeyefast.mchjong.compat.ponder.MchjongPonder.register();
            RiichiStickModel.initialize(() -> Minecraft.getInstance().getModelManager()
                .getModel(RiichiStickModel.ID));
            MenuScreens.register(MahjongContent.BOX_MENU, MahjongBoxScreen::new);
            MenuScreens.register(MahjongContent.TABLE_MENU, MahjongTableScreen::new);
            MenuScreens.register(MahjongContent.STICK_MENU, PointStickScreen::new);
            HeldSupplyArm.initialize((model, context, pose, leftHand) -> model.applyTransform(context, pose, leftHand));
            var renderer = new MahjongItemRenderer();
            var extensions = new IClientItemExtensions() {
                @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() { return renderer; }
            };
            for (var item : MahjongItemRenderer.items()) ((ItemRenderProperties) item).mchjong$renderProperties(extensions);
        });
    }

    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MahjongContent.TABLE_ENTITY, MahjongTableRenderer::new);
        event.registerBlockEntityRenderer(MahjongContent.STOOL_ENTITY, FurnitureRenderer::new);
        event.registerEntityRenderer(MahjongContent.SEAT_ENTITY, SeatRenderer::new);
    }

    @Mod.EventBusSubscriber(modid = MahjongContent.MOD_ID, value = Dist.CLIENT)
    public static final class Lifecycle {
        private Lifecycle() {}

        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            top.skyeyefast.mchjong.compat.patchouli.ManualClient.tick(PATCHOULI);
            TableAudio.tick();
            SeatedCamera.tick();
            ClientReplays.tick();
            top.skyeyefast.mchjong.client.TileFacePresets.tick();
        }

        @SubscribeEvent public static void close(GameShuttingDownEvent event) { TableAudio.close(); }
    }
}
