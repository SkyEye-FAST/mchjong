package top.skyeyefast.mchjong.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
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
    private static final ModelResourceLocation RIICHI = new ModelResourceLocation(RiichiStickModel.ID, "standalone");
    private MchjongForgeClient() {}

    public static void registerConfigScreen(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(PersonalSettingsScreen::new));
    }

    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) {
        event.register(RIICHI);
    }

    @SubscribeEvent public static void resources(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) TileFacePresets::reload);
        event.registerReloadListener((ResourceManagerReloadListener) RuleHelp::reload);
    }


    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            RiichiStickModel.initialize(() -> Minecraft.getInstance().getModelManager()
                .getModel(RIICHI));
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

        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post event) {
            RiichiAudio.tick();
            SeatedCamera.tick();
            ClientReplays.tick();
            top.skyeyefast.mchjong.client.TileFacePresets.tick();
        }

        @SubscribeEvent public static void close(GameShuttingDownEvent event) { RiichiAudio.close(); }
    }
}
