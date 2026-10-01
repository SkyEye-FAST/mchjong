package top.skyeyefast.mchjong.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.skyeyefast.mchjong.world.MahjongContent;

/** The optional maid layer must not draw the furniture's shadow-only block model. */
@Pseudo
@Mixin(targets = {
    "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.layer.LayerMaidBipedHead",
    "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.geckolayer.GeckoLayerMaidBipedHead"
}, remap = false)
abstract class MaidDisplayItemMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", ordinal = 1,
        target = "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;", remap = true))
    private Item mchjong$itemRenderedFurniture(ItemStack stack) {
        return stack.is(MahjongContent.TABLE_ITEM) || stack.is(MahjongContent.AUTO_TABLE_ITEM)
            || stack.is(MahjongContent.STOOL_ITEM) ? Items.AIR : stack.getItem();
    }
}
