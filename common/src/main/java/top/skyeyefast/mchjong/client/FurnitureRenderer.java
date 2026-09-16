package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import top.skyeyefast.mchjong.world.FurnitureBlockEntity;

public final class FurnitureRenderer implements BlockEntityRenderer<FurnitureBlockEntity> {
    public FurnitureRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(FurnitureBlockEntity stool, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(.5, 0, .5);
        FurnitureMesh.stool(pose, buffers, light, stool.wood(), stool.color());
        pose.popPose();
    }
}
