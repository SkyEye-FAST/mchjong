package top.skyeyefast.mchjong.compat.create.mixin;

import com.simibubi.create.AllRecipeTypes;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the original stonecutter/crafting recipes, but use their efficient Create counterparts in machines. */
@Mixin(value = AllRecipeTypes.class, remap = false)
abstract class AutomationRecipeMixin {
    @Inject(method = "shouldIgnoreInAutomation", at = @At("HEAD"), cancellable = true)
    private static void mchjong$preferIndustrialRecipes(RecipeHolder<?> recipe, CallbackInfoReturnable<Boolean> callback) {
        if (!recipe.id().getNamespace().equals("mchjong")) return;
        String path = recipe.id().getPath();
        if (path.startsWith("blanks_") || path.equals("blank_point_sticks") || path.equals("mahjong_dye")
            || path.equals("red_dora_dye") || path.equals("undo_dye")) callback.setReturnValue(true);
    }
}
