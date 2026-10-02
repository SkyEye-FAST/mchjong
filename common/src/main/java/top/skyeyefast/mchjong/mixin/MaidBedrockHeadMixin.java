package top.skyeyefast.mchjong.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.EntityMaidModel;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.state.EntityMaidRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.compat.maid.client.MaidHeadItems;

@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.layer.LayerMaidBipedHead", remap = false)
abstract class MaidBedrockHeadMixin extends RenderLayer<EntityMaidRenderState, EntityMaidModel> {
    protected MaidBedrockHeadMixin(RenderLayerParent<EntityMaidRenderState, EntityMaidModel> parent) { super(parent); }
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILcom/github/tartaricacid/touhoulittlemaid/client/renderer/entity/state/EntityMaidRenderState;FF)V", at = @At("TAIL"))
    private void mchjong$furniture(PoseStack pose, SubmitNodeCollector collector, int light, EntityMaidRenderState state,
            float yaw, float pitch, CallbackInfo callback) {
        var model = getParentModel();
        if (state.modelInfo == null || !state.modelInfo.isShowCustomHead() || !model.hasHead()) return;
        pose.pushPose(); model.root().translateAndRotate(pose); model.getHead().translateAndRotate(pose);
        MaidHeadItems.submit((MaidHeadItems.State) state, pose, collector, light, state.outlineColor);
        pose.popPose();
    }
}
