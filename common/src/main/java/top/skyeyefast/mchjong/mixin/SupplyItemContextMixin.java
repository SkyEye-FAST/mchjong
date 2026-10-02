package top.skyeyefast.mchjong.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import top.skyeyefast.mchjong.client.MahjongItemRenderer;

/** Preserve the native item's display context in the deferred supply argument. */
@Mixin(SpecialModelWrapper.class)
abstract class SupplyItemContextMixin {
    @ModifyExpressionValue(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/special/SpecialModelRenderer;extractArgument(Lnet/minecraft/world/item/ItemStack;)Ljava/lang/Object;"))
    private Object mchjong$context(Object value, ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver,
            ItemDisplayContext context, ClientLevel level, ItemOwner owner, int seed) {
        return value instanceof MahjongItemRenderer.Argument supply ? new MahjongItemRenderer.Argument(supply.stack(), context) : value;
    }
}
