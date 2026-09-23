package top.skyeyefast.mchjong.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.world.SeatEntity;

@Mixin(Camera.class)
public abstract class TableCameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void mchjong$tableCamera(DeltaTracker delta, CallbackInfo callback) {
        Camera camera = (Camera) (Object) this;
        var entity = camera.entity();
        if (camera.isDetached() || entity != Minecraft.getInstance().player
                || !(entity.getVehicle() instanceof SeatEntity seat)) return;
        var pose = top.skyeyefast.mchjong.client.SeatedCamera.state(seat);
        pose.sample(camera.getCameraEntityPartialTicks(delta));
        Vec3 position = TableSettings.get().cameraPosition(seat);
        setPosition(position.x, position.y, position.z);
        setRotation(pose.yaw(seat.seat()), pose.pitch());
    }
}
