package top.skyeyefast.mchjong.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
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
                                       boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light,
                                       CallbackInfo callback) {
        if (entity instanceof AbstractClientPlayer player)
            HeldSupplyArm.render(player, stack, context, leftHand, pose, buffers, light);
    }
}
