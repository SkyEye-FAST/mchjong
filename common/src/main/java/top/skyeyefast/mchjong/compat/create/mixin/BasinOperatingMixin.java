package top.skyeyefast.mchjong.compat.create.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.skyeyefast.mchjong.compat.create.CreatePlatform;
import top.skyeyefast.mchjong.compat.create.CreateProcessing;

/** Add data-preserving recipes to the normal Create machine lifecycle. */
@Mixin(value = BasinOperatingBlockEntity.class, remap = false)
abstract class BasinOperatingMixin {
    @Shadow protected abstract Optional<BasinBlockEntity> getBasin();

    @Inject(method = "getMatchingRecipes", at = @At("RETURN"), cancellable = true)
    private void mchjong$workshopRecipes(CallbackInfoReturnable<List<Recipe<?>>> callback) {
        boolean mixing = (Object) this instanceof MechanicalMixerBlockEntity;
        if (!mixing && !((Object) this instanceof MechanicalPressBlockEntity)) return;
        var basin = getBasin().orElse(null);
        if (basin == null || basin.getLevel() == null) return;
        var inventory = CreatePlatform.inventory(basin);
        var batch = mixing ? CreateProcessing.mixing(inventory) : CreateProcessing.pressing(inventory);
        if (batch == null || !BasinRecipe.match(basin, batch.recipe())) return;
        var recipes = new ArrayList<Recipe<?>>();
        recipes.add(batch.recipe());
        recipes.addAll(callback.getReturnValue());
        callback.setReturnValue(recipes);
    }
}
