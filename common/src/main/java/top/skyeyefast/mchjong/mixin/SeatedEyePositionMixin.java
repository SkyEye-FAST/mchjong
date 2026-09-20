package top.skyeyefast.mchjong.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Vanilla first-person picking must start at the same eye as the seated camera. */
@Mixin(Entity.class)
public abstract class SeatedEyePositionMixin {
    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"), cancellable = true)
    private void mchjong$seatedEye(CallbackInfoReturnable<Vec3> callback) {
        Minecraft client = Minecraft.getInstance();
        if ((Object) this != client.player || !client.options.getCameraType().isFirstPerson()) return;
        if (client.player.getVehicle() instanceof SeatEntity seat)
            callback.setReturnValue(TableSettings.get().cameraPosition(seat));
    }

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void mchjong$interpolatedEye(float partialTick, CallbackInfoReturnable<Vec3> callback) {
        Minecraft client = Minecraft.getInstance();
        if ((Object) this != client.player || !client.options.getCameraType().isFirstPerson()) return;
        if (client.player.getVehicle() instanceof SeatEntity seat) {
            var pose = top.skyeyefast.mchjong.client.SeatedCamera.state(seat);
            callback.setReturnValue(top.skyeyefast.mchjong.world.TableGeometry.world(seat.tablePos(), pose.eye(seat.seat(), partialTick)));
        }
    }
}
