package top.skyeyefast.mchjong.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.client.HeldSupplyArm;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldSupplyArmMixin {
    @Inject(method = "renderItem", at = @At("HEAD"))
    private void mchjong$heldSupplyArm(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                       PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo callback) {
        if (entity instanceof AbstractClientPlayer player)
            HeldSupplyArm.begin(player, stack, context, pose);
    }

    @Inject(method = "renderItem", at = @At("RETURN"))
    private void mchjong$clearHeldSupplyArm(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                            PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo callback) {
        HeldSupplyArm.end();
    }
}
