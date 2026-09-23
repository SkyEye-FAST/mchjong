package top.skyeyefast.mchjong.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.world.SeatEntity;

/** World rendering and table picking consume the same seated field of view. */
@Mixin(Camera.class)
public abstract class TableFovMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void mchjong$tableFov(float partialTick, CallbackInfoReturnable<Float> callback) {
        Camera camera = (Camera) (Object) this;
        var client = Minecraft.getInstance();
        if (client.player == null || camera.isDetached() || camera.entity() != client.player
                || !(client.player.getVehicle() instanceof SeatEntity)) return;
        var window = client.getWindow();
        if (window.getWidth() <= 0 || window.getHeight() <= 0) return;
        double fov = TableSettings.get().cameraFov(callback.getReturnValue(),
            (double) window.getWidth() / window.getHeight());
        callback.setReturnValue((float) TableSettings.get().camera().fov(fov));
    }
}
