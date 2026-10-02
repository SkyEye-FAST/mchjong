package top.skyeyefast.mchjong;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import top.skyeyefast.mchjong.client.ClientRiichiNetworking;
import top.skyeyefast.mchjong.client.MahjongTableRenderer;
import top.skyeyefast.mchjong.client.SeatRenderer;
import top.skyeyefast.mchjong.network.RiichiViewPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

public final class MchjongClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        boolean patchouli = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("patchouli");
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("touhou_little_maid"))
            top.skyeyefast.mchjong.compat.maid.client.MaidSeatMounts.register();
        top.skyeyefast.mchjong.fabric.mixin.SpecialModelRenderersAccessor.mchjong$idMapper().put(
            top.skyeyefast.mchjong.client.MahjongItemRenderer.TYPE,
            top.skyeyefast.mchjong.client.MahjongItemRenderer.MAP_CODEC);
        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(MahjongContent.id("tile_face_presets"),
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                    top.skyeyefast.mchjong.client.TileFacePresets::reload);
        net.minecraft.client.KeyMapping.Category.register(top.skyeyefast.mchjong.client.TableKeys.CATEGORY.id());
        top.skyeyefast.mchjong.client.TableKeys.ALL.forEach(net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper::registerKeyMapping);
        net.minecraft.client.gui.screens.MenuScreens.register(MahjongContent.BOX_MENU, top.skyeyefast.mchjong.client.MahjongBoxScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(MahjongContent.TABLE_MENU, top.skyeyefast.mchjong.client.MahjongTableScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(MahjongContent.STICK_MENU, top.skyeyefast.mchjong.client.PointStickScreen::new);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            top.skyeyefast.mchjong.compat.patchouli.ManualClient.tick(patchouli);
            top.skyeyefast.mchjong.client.RiichiAudio.tick();
            top.skyeyefast.mchjong.client.SeatedCamera.tick();
            top.skyeyefast.mchjong.client.ClientReplays.tick();
            top.skyeyefast.mchjong.client.TileFacePresets.tick();
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
            top.skyeyefast.mchjong.client.RiichiAudio.close());
        BlockEntityRenderers.register(MahjongContent.TABLE_ENTITY, MahjongTableRenderer::new);
        BlockEntityRenderers.register(MahjongContent.STOOL_ENTITY, top.skyeyefast.mchjong.client.FurnitureRenderer::new);
        EntityRenderers.register(MahjongContent.SEAT_ENTITY, SeatRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(RiichiViewPayload.TYPE,
            (payload, context) -> context.client().execute(() -> ClientRiichiNetworking.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.McrViewPayload.TYPE,
            (payload, context) -> context.client().execute(() -> top.skyeyefast.mchjong.client.ClientMcrNetworking.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.SichuanViewPayload.TYPE,
            (payload, context) -> context.client().execute(() -> top.skyeyefast.mchjong.client.ClientSichuanNetworking.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.VoiceAppearancePayload.TYPE,
            (payload, context) -> context.client().execute(() -> top.skyeyefast.mchjong.client.VoicePresets.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.ReplayPayload.TYPE,
            (payload, context) -> context.client().execute(() -> top.skyeyefast.mchjong.client.ClientReplays.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.PresetBundlePayload.TYPE,
            (payload, context) -> context.client().execute(() -> top.skyeyefast.mchjong.client.TileFacePresets.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.StickAppearancePayload.TYPE,
            (payload, context) -> context.client().execute(() -> top.skyeyefast.mchjong.client.RiichiStickPresets.receive(payload)));
    }
}
