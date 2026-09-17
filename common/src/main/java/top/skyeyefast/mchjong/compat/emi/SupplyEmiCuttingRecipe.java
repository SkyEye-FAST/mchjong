package top.skyeyefast.mchjong.compat.emi;

import java.util.List;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExample;

final class SupplyEmiCuttingRecipe implements EmiRecipe {
    private final SupplyRecipeExample example;
    private final EmiStack input;
    private final EmiStack output;

    SupplyEmiCuttingRecipe(SupplyRecipeExample example) {
        this.example = example;
        input = EmiStack.of(example.input().getFirst());
        output = EmiStack.of(example.output());
    }

    @Override public EmiRecipeCategory getCategory() { return VanillaEmiRecipeCategories.STONECUTTING; }
    @Override public ResourceLocation getId() { return example.id(); }
    @Override public RecipeHolder<?> getBackingRecipe() { return example.source(); }
    @Override public List<EmiIngredient> getInputs() { return List.of(input); }
    @Override public List<EmiStack> getOutputs() { return List.of(output); }
    @Override public int getDisplayWidth() { return 76; }
    @Override public int getDisplayHeight() { return 26; }
    @Override public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(input, 0, 4);
        widgets.addTexture(EmiTexture.EMPTY_ARROW, 24, 5);
        widgets.addSlot(output, 50, 0).large(true).recipeContext(this);
    }
}
