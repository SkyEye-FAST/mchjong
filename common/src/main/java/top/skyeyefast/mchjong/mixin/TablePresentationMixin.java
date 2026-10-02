package top.skyeyefast.mchjong.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.skyeyefast.mchjong.client.RiichiTableScreen;

@Mixin(GameRenderer.class)
public abstract class TablePresentationMixin {
    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void mchjong$tableHands(com.mojang.blaze3d.vertex.PoseStack pose, Camera camera, float delta, CallbackInfo callback) {
        var screen = Minecraft.getInstance().screen;
        if (RiichiTableScreen.active(screen) != null || top.skyeyefast.mchjong.client.McrTableScreen.isOpen(screen)
            || top.skyeyefast.mchjong.client.SichuanTableScreen.isOpen(screen)) callback.cancel();
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void mchjong$tableOutline(CallbackInfoReturnable<Boolean> callback) {
        var screen = Minecraft.getInstance().screen;
        if (RiichiTableScreen.active(screen) != null || top.skyeyefast.mchjong.client.McrTableScreen.isOpen(screen)
            || top.skyeyefast.mchjong.client.SichuanTableScreen.isOpen(screen)) callback.setReturnValue(false);
    }
}
