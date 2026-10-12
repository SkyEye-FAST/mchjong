package top.skyeyefast.mchjong.smoke.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the isolated test client from capturing the desktop cursor. */
@Mixin(MouseHandler.class)
abstract class SmokeMouseMixin {
    @Inject(method = {"grabMouse", "releaseMouse"}, at = @At("HEAD"), cancellable = true)
    private void keepDesktopCursorFree(CallbackInfo callback) {
        callback.cancel();
    }
}
