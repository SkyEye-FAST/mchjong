package top.skyeyefast.mchjong.compat.create.mixin;

import java.util.List;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A full stack of bone blocks yields 32 output stacks, one more than the native saw's output slots. */
@Mixin(value = SawBlockEntity.class, remap = false)
abstract class SawOutputMixin {
    @Shadow public ProcessingInventory inventory;
    @Shadow private List<RecipeHolder<? extends Recipe<?>>> getRecipes() { throw new AssertionError(); }

    @Inject(method = "applyRecipe", at = @At("HEAD"))
    private void mchjong$retainBulkOutputs(CallbackInfo callback) {
        int count = inventory.getStackInSlot(0).getCount();
        if (count == 0) return;
        int required = inventory.getSlots();
        for (var holder : getRecipes()) {
            if (!holder.id().getNamespace().equals("mchjong") || !holder.id().getPath().startsWith("create/")
                || !(holder.value() instanceof CuttingRecipe cutting)) continue;
            int slots = 1;
            for (var result : cutting.getRollableResultsAsItemStacks())
                slots += Math.ceilDiv(count * result.getCount(), result.getMaxStackSize());
            required = Math.max(required, slots);
        }
        if (required == inventory.getSlots()) return;
        var saved = new java.util.ArrayList<ItemStack>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) saved.add(inventory.getStackInSlot(slot).copy());
        inventory.setSize(required);
        for (int slot = 0; slot < saved.size(); slot++) inventory.setStackInSlot(slot, saved.get(slot));
    }
}
