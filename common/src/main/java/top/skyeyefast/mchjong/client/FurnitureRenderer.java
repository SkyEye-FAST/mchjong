package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.world.FurnitureBlockEntity;

public final class FurnitureRenderer implements BlockEntityRenderer<FurnitureBlockEntity, FurnitureRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        FurnitureWood wood;
        net.minecraft.world.item.DyeColor color;
    }
    public FurnitureRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(FurnitureBlockEntity stool, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(stool, state, partialTick, cameraPos, crumbling);
        state.wood = stool.wood();
        state.color = stool.color();
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        var buffers = new DeferredBuffers(collector);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        FurnitureMesh.stool(pose, buffers, state.lightCoords, state.wood, state.color);
        buffers.submit(pose);
        pose.popPose();
    }
}
