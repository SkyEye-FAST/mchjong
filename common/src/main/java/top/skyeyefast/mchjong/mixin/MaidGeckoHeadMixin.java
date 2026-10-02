package top.skyeyefast.mchjong.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.gecko.GeckoMaidRenderData;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.state.EntityMaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoLocatorType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.compat.maid.client.MaidHeadItems;

@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.gecko.layer.GeckoLayerMaidBipedHead", remap = false)
abstract class MaidGeckoHeadMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/SubmitNodeCollector;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/github/tartaricacid/touhoulittlemaid/client/renderer/entity/state/EntityMaidRenderState;Lcom/github/tartaricacid/touhoulittlemaid/client/renderer/entity/gecko/GeckoMaidRenderData;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
    private void mchjong$furniture(SubmitNodeCollector collector, PoseStack pose, EntityMaidRenderState state,
            GeckoMaidRenderData data, CameraRenderState camera, CallbackInfo callback) {
        if (state.modelInfo == null || !state.modelInfo.isShowCustomHead()) return;
        data.modelState.visitLocatorGroup(GeoLocatorType.HEAD, pose, head -> {
            head.pushPose(); head.mulPose(Axis.ZP.rotationDegrees(180));
            MaidHeadItems.submit((MaidHeadItems.State) state, head, collector, state.lightCoords, state.outlineColor);
            head.popPose();
        });
    }
}
