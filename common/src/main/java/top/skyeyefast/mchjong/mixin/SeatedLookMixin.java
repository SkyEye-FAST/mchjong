package top.skyeyefast.mchjong.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.client.SeatedCamera;

@Mixin(Entity.class)
public abstract class SeatedLookMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void mchjong$look(double dx, double dy, CallbackInfo callback) {
        if ((Object) this == Minecraft.getInstance().player && SeatedCamera.turn(dx, dy)) callback.cancel();
    }
}
