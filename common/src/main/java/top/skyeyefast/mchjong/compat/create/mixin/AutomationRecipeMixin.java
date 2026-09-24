package top.skyeyefast.mchjong.compat.create.mixin;

import com.simibubi.create.AllRecipeTypes;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Machines use the efficient industrial recipes, leaving hand recipes unchanged. */
@Mixin(value = AllRecipeTypes.class, remap = false)
abstract class AutomationRecipeMixin {
    @Inject(method = "shouldIgnoreInAutomation", at = @At("HEAD"), cancellable = true)
    private static void mchjong$preferIndustrialRecipes(Recipe<?> recipe, CallbackInfoReturnable<Boolean> callback) {
        if (!recipe.getId().getNamespace().equals("mchjong")) return;
        String path = recipe.getId().getPath();
        if (path.startsWith("blanks_") || path.equals("blank_point_sticks") || path.equals("mahjong_dye")
            || path.equals("red_dora_dye") || path.equals("undo_dye")) callback.setReturnValue(true);
    }
}
