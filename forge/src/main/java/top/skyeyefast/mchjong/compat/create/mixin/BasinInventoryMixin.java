package top.skyeyefast.mchjong.compat.create.mixin;

import com.simibubi.create.content.processing.basin.BasinInventory;
import com.simibubi.create.foundation.blockEntity.SyncedBlockEntity;
import com.simibubi.create.foundation.item.SmartInventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Permit repeated supply stacks without changing the basin's capacity. */
@Mixin(value = BasinInventory.class, remap = false)
abstract class BasinInventoryMixin extends SmartInventory {
    private BasinInventoryMixin(int slots, SyncedBlockEntity owner) { super(slots, owner); }

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void mchjong$allowRepeatedSupplies(int slot, ItemStack stack, boolean simulate,
                                              CallbackInfoReturnable<ItemStack> callback) {
        if (MahjongSupplies.storable(stack) || stack.is(MahjongContent.BOX_ITEM))
            callback.setReturnValue(super.insertItem(slot, stack, simulate));
    }
}
