package top.skyeyefast.mchjong.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.skyeyefast.mchjong.client.TableScreen;

@Mixin(GameRenderer.class)
public abstract class TablePresentationMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void mchjong$tableHands(CameraRenderState camera, float delta, Matrix4fc projection, CallbackInfo callback) {
        if (TableScreen.active(Minecraft.getInstance().screen) != null) callback.cancel();
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void mchjong$tableOutline(CallbackInfoReturnable<Boolean> callback) {
        if (TableScreen.active(Minecraft.getInstance().screen) != null) callback.setReturnValue(false);
    }
}
