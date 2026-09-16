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
import top.skyeyefast.mchjong.client.TableScreen;
import top.skyeyefast.mchjong.client.TableSettings;
import top.skyeyefast.mchjong.world.SeatEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

@Mixin(Camera.class)
public abstract class TableCameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"))
    private void mchjong$tableCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored,
            float partialTick, CallbackInfo callback) {
        TableScreen table = TableScreen.active(Minecraft.getInstance().screen);
        if (table == null || entity != Minecraft.getInstance().player || !(entity.getVehicle() instanceof SeatEntity seat)
                || !seat.tablePos().equals(table.tablePos())) return;
        TableSettings settings = TableSettings.get();
        Vec3 position = TableGeometry.world(seat.tablePos(), TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, seat.seat()));
        setPosition(position.x, position.y, position.z);
        setRotation(entity.getViewYRot(partialTick), entity.getViewXRot(partialTick));
    }
}
