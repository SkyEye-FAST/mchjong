package top.skyeyefast.mchjong.smoke;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.compat.jei.MahjongJeiPlugin;

final class JeiBrowserSmoke implements BrowserDriver {
    @Override public boolean ready() { return MahjongJeiPlugin.runtime() != null; }
    @Override public List<ItemStack> catalogue() { return List.copyOf(MahjongJeiPlugin.runtime().getIngredientManager().getAllItemStacks()); }

    @Override public Set<Identifier> query(ItemStack stack, boolean output) {
        var runtime = MahjongJeiPlugin.runtime();
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(
            output ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, stack);
        return runtime.getRecipeManager().createRecipeLookup(MahjongJeiPlugin.CRAFTING)
            .limitFocus(List.of(focus)).get()
            .map(top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExample::id).collect(Collectors.toSet());
    }

    @Override public void showRecipe(Identifier id) {
        var runtime = MahjongJeiPlugin.runtime();
        var result = runtime.getRecipeManager().createRecipeLookup(MahjongJeiPlugin.CRAFTING).get()
            .filter(recipe -> id.equals(recipe.id())).toList();
        if (result.isEmpty()) throw new IllegalStateException("JEI did not register " + id);
        runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(MahjongJeiPlugin.CRAFTING), result, List.of());
    }

}
