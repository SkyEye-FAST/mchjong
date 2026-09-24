package top.skyeyefast.mchjong.compat.create.mixin;

import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import net.minecraft.core.Direction;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.skyeyefast.mchjong.compat.create.CreateProcessing;

/** Reserve all dynamic outputs together; two boxes must not claim the same last free slot. */
@Mixin(value = BasinRecipe.class, remap = false)
abstract class BasinOutputMixin {
    @Inject(method = "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;Lnet/minecraft/world/item/crafting/Recipe;Z)Z",
        at = @At("HEAD"), cancellable = true)
    private static void mchjong$reserveOutputs(BasinBlockEntity basin, Recipe<?> recipe, boolean simulate,
                                               CallbackInfoReturnable<Boolean> callback) {
        if (!(recipe instanceof CreateProcessing.WorkshopRecipe workshop)
            || basin.getBlockState().getValue(BasinBlock.FACING) != Direction.DOWN) return;
        var output = basin.getOutputInventory();
        var reservation = new ItemStackHandler(output.getSlots());
        for (int slot = 0; slot < output.getSlots(); slot++)
            reservation.setStackInSlot(slot, output.getStackInSlot(slot).copy());
        for (var result : workshop.getRollableResultsAsItemStacks()) {
            if (ItemHandlerHelper.insertItemStacked(reservation, result.copy(), false).isEmpty()) continue;
            callback.setReturnValue(false);
            return;
        }
    }
}
