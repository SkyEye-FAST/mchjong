package top.skyeyefast.mchjong.smoke;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
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
        return List.of(MahjongJeiPlugin.CRAFTING).stream()
            .flatMap(type -> runtime.getRecipeManager().createRecipeLookup(type).limitFocus(List.of(focus)).get())
            .map(top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExample::id).collect(Collectors.toSet());
    }

    @Override public void showRecipe(ResourceLocation id) {
        var runtime = MahjongJeiPlugin.runtime();
        for (var type : List.of(MahjongJeiPlugin.CRAFTING)) {
            var result = runtime.getRecipeManager().createRecipeLookup(type).get().filter(recipe -> recipe.id().equals(id)).toList();
            if (!result.isEmpty()) {
                runtime.getRecipesGui().showRecipes(runtime.getRecipeManager().getRecipeCategory(type), result, List.of());
                return;
            }
        }
        throw new IllegalStateException("JEI did not register " + id);
    }

}
