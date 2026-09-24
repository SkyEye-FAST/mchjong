package top.skyeyefast.mchjong.smoke;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.compat.jei.MahjongJeiPlugin;

final class JeiBrowserSmoke implements BrowserDriver {
    @Override public boolean ready() { return MahjongJeiPlugin.runtime() != null; }
    @Override public List<ItemStack> catalogue() { return List.copyOf(MahjongJeiPlugin.runtime().getIngredientManager().getAllItemStacks()); }

    @Override public Set<ResourceLocation> query(ItemStack stack, boolean output) {
        var runtime = MahjongJeiPlugin.runtime();
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(
            output ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);
        return runtime.getRecipeManager().createRecipeCategoryLookup().get()
            .filter(category -> category.getRecipeType().getUid().getNamespace().equals("mchjong"))
            .flatMap(category -> recipeIds(category, focus))
            .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    }

    private static <T> java.util.stream.Stream<ResourceLocation> recipeIds(IRecipeCategory<T> category,
            mezz.jei.api.recipe.IFocus<ItemStack> focus) {
        return MahjongJeiPlugin.runtime().getRecipeManager().createRecipeLookup(category.getRecipeType())
            .limitFocus(List.of(focus)).get().map(category::getRegistryName);
    }

    @Override public void showRecipe(ResourceLocation id) {
        var runtime = MahjongJeiPlugin.runtime();
        for (var category : runtime.getRecipeManager().createRecipeCategoryLookup().get().toList())
            if (showRecipe(category, id)) return;
        throw new IllegalStateException("JEI did not register " + id);
    }

    private static <T> boolean showRecipe(IRecipeCategory<T> category, ResourceLocation id) {
        var runtime = MahjongJeiPlugin.runtime();
        var result = runtime.getRecipeManager().createRecipeLookup(category.getRecipeType()).get()
            .filter(recipe -> id.equals(category.getRegistryName(recipe))).toList();
        if (result.isEmpty()) return false;
        runtime.getRecipesGui().showRecipes(category, result, List.of());
        return true;
    }

}
