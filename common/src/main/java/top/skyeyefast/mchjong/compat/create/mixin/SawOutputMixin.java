package top.skyeyefast.mchjong.compat.create.mixin;

import java.util.List;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.compat.create.CreatePlatform;

/** A 64-bone-block batch needs room for all 32 output stacks plus the input slot. */
@Mixin(value = SawBlockEntity.class, remap = false)
abstract class SawOutputMixin {
    @Shadow public ProcessingInventory inventory;
    @Shadow private List<Recipe<?>> getRecipes() { throw new AssertionError(); }

    @Inject(method = "applyRecipe", at = @At("HEAD"))
    private void mchjong$retainBulkOutputs(CallbackInfo callback) {
        int count = inventory.getStackInSlot(0).getCount();
        if (count == 0) return;
        for (var recipe : getRecipes()) {
            if (!recipe.getId().getNamespace().equals("mchjong") || !recipe.getId().getPath().startsWith("create/")
                || !(recipe instanceof CuttingRecipe cutting)) continue;
            int slots = 1;
            for (var result : cutting.getRollableResultsAsItemStacks())
                slots += (count * result.getCount() + result.getMaxStackSize() - 1) / result.getMaxStackSize();
            CreatePlatform.ensureCapacity(inventory, slots);
        }
    }
}
