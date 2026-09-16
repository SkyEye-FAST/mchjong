package top.skyeyefast.mchjong.mixin;

import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Vanilla caches stonecutting by item identity; tile recipes also depend on components. */
@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin {
    @Shadow private ItemStack input;

    @Redirect(method = "slotsChanged", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean mchjong$unchangedInput(ItemStack current, Item item) {
        return current.is(item) && (!current.is(MahjongContent.TILE_ITEM)
            || ItemStack.isSameItemSameComponents(current, input));
    }
}
