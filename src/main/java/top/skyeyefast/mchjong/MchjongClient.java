package top.skyeyefast.mchjong;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.ClientTableNetworking;
import top.skyeyefast.mchjong.client.MahjongTableRenderer;
import top.skyeyefast.mchjong.client.SeatRenderer;
import top.skyeyefast.mchjong.network.TableViewPayload;
import top.skyeyefast.mchjong.world.MahjongContent;

public final class MchjongClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client ->
            top.skyeyefast.mchjong.client.TableAudio.tick());
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
            top.skyeyefast.mchjong.client.TableAudio.close());
        if (!ResourceManagerHelper.registerBuiltinResourcePack(MahjongContent.id("patterned_backs"),
                FabricLoader.getInstance().getModContainer(MahjongContent.MOD_ID).orElseThrow(),
                Component.translatable("resourcePack.mchjong.patterned_backs.name"), ResourcePackActivationType.NORMAL)) {
            throw new IllegalStateException("MCjhong patterned back resource pack is missing");
        }
        BlockEntityRenderers.register(MahjongContent.TABLE_ENTITY, MahjongTableRenderer::new);
        EntityRendererRegistry.register(MahjongContent.SEAT_ENTITY, SeatRenderer::new);
        ClientPlayNetworking.registerGlobalReceiver(TableViewPayload.TYPE,
            (payload, context) -> context.client().execute(() -> ClientTableNetworking.receive(payload)));
    }
}
