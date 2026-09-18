package top.skyeyefast.mchjong.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.skyeyefast.mchjong.client.TableCamera;
import top.skyeyefast.mchjong.world.SeatEntity;

/** World rendering and table picking consume the same seated field of view. */
@Mixin(GameRenderer.class)
public abstract class TableFovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void mchjong$tableFov(Camera camera, float partialTick, boolean changingFov,
            CallbackInfoReturnable<Double> callback) {
        var client = Minecraft.getInstance();
        if (!changingFov || camera.isDetached() || camera.getEntity() != client.player
                || client.player == null || !(client.player.getVehicle() instanceof SeatEntity)) return;
        var window = client.getWindow();
        if (window.getWidth() <= 0 || window.getHeight() <= 0) return;
        callback.setReturnValue(TableCamera.fov(callback.getReturnValue(),
            (double) window.getWidth() / window.getHeight()));
    }
}
