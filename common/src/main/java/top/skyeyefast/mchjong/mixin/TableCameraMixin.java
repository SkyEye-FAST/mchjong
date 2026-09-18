package top.skyeyefast.mchjong.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.client.TableCamera;
import top.skyeyefast.mchjong.world.SeatEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

@Mixin(Camera.class)
public abstract class TableCameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void mchjong$tableCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
            float partialTick, CallbackInfo callback) {
        if (detached || entity != Minecraft.getInstance().player || !(entity.getVehicle() instanceof SeatEntity seat)) return;
        Vec3 position = TableCamera.position(seat);
        setPosition(position.x, position.y, position.z);
        if (TableCamera.overhead()) setRotation(TableGeometry.yaw(seat.seat()), 90);
        else setRotation(entity.getViewYRot(partialTick), entity.getViewXRot(partialTick));
    }
}
