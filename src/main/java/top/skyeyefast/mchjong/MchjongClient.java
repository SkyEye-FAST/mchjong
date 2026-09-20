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
        top.skyeyefast.mchjong.client.TableKeys.ALL.forEach(net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper::registerKeyBinding);
        top.skyeyefast.mchjong.client.HeldSupplyArm.initialize((model, context, pose, leftHand) ->
            model.getTransforms().getTransform(context).apply(leftHand, pose));
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ponder"))
            top.skyeyefast.mchjong.compat.ponder.MchjongPonder.register();
        net.minecraft.client.gui.screens.MenuScreens.register(MahjongContent.BOX_MENU, top.skyeyefast.mchjong.client.MahjongBoxScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(MahjongContent.TABLE_MENU, top.skyeyefast.mchjong.client.MahjongTableScreen::new);
        net.minecraft.client.gui.screens.MenuScreens.register(MahjongContent.STICK_MENU, top.skyeyefast.mchjong.client.PointStickScreen::new);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            top.skyeyefast.mchjong.client.TableAudio.tick();
            top.skyeyefast.mchjong.client.SeatedCamera.tick();
            top.skyeyefast.mchjong.client.ClientReplays.tick();
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
            top.skyeyefast.mchjong.client.TableAudio.close());
        BlockEntityRenderers.register(MahjongContent.TABLE_ENTITY, MahjongTableRenderer::new);
        BlockEntityRenderers.register(MahjongContent.STOOL_ENTITY, top.skyeyefast.mchjong.client.FurnitureRenderer::new);
        var itemRenderer = new top.skyeyefast.mchjong.client.MahjongItemRenderer();
        for (var item : top.skyeyefast.mchjong.client.MahjongItemRenderer.items())
            net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry.INSTANCE.register(item, itemRenderer::renderByItem);
        EntityRendererRegistry.register(MahjongContent.SEAT_ENTITY, SeatRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(TableViewPayload.TYPE,
            (payload, context) -> context.client().execute(() -> ClientTableNetworking.receive(payload)));
        ClientPlayNetworking.registerGlobalReceiver(top.skyeyefast.mchjong.network.ReplayPayload.TYPE,
            (payload, context) -> context.client().execute(() -> top.skyeyefast.mchjong.client.ClientReplays.receive(payload)));
    }
}
