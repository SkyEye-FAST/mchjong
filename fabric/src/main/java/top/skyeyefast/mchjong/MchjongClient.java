package top.skyeyefast.mchjong;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import top.skyeyefast.mchjong.client.ClientTableNetworking;
import top.skyeyefast.mchjong.client.MahjongTableRenderer;
import top.skyeyefast.mchjong.client.SeatRenderer;
import top.skyeyefast.mchjong.network.TableViewPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

public final class MchjongClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        top.skyeyefast.mchjong.fabric.mixin.SpecialModelRenderersAccessor.mchjong$idMapper().put(
            top.skyeyefast.mchjong.client.MahjongItemRenderer.TYPE,
            top.skyeyefast.mchjong.client.MahjongItemRenderer.MAP_CODEC);
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                @Override public net.minecraft.resources.Identifier getFabricId() { return MahjongContent.id("tile_face_presets"); }
                @Override public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager resources) {
                    top.skyeyefast.mchjong.client.TileFacePresets.reload(resources);
                }
            });
        top.skyeyefast.mchjong.client.TableKeys.ALL.forEach(net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper::registerKeyMapping);
        net.minecraft.client.gui.screens.MenuScreens.register(MahjongContent.BOX_MENU, top.skyeyefast.mchjong.client.MahjongBoxScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(MahjongContent.TABLE_MENU, top.skyeyefast.mchjong.client.MahjongTableScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(MahjongContent.STICK_MENU, top.skyeyefast.mchjong.client.PointStickScreen::new);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            top.skyeyefast.mchjong.client.TableAudio.tick();
            top.skyeyefast.mchjong.client.SeatedCamera.tick();
            top.skyeyefast.mchjong.client.ClientReplays.tick();
        });
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("touhou_little_maid"))
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
                top.skyeyefast.mchjong.compat.maid.client.MaidClientSeats.tick());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
            top.skyeyefast.mchjong.client.TableAudio.close());
        BlockEntityRenderers.register(MahjongContent.TABLE_ENTITY, MahjongTableRenderer::new);
        BlockEntityRenderers.register(MahjongContent.STOOL_ENTITY, top.skyeyefast.mchjong.client.FurnitureRenderer::new);
        EntityRendererRegistry.register(MahjongContent.SEAT_ENTITY, SeatRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(TableViewPayload.TYPE,
            (payload, context) -> context.client().execute(() -> ClientTableNetworking.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.ReplayPayload.TYPE,
            (payload, context) -> context.client().execute(() -> top.skyeyefast.mchjong.client.ClientReplays.receive(payload)));
    }
}
