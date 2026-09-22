package top.skyeyefast.mchjong.smoke;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class EmiBrowserSmoke implements BrowserDriver {
    @Override public boolean ready() { return !EmiApi.getRecipeManager().getRecipes().isEmpty() && !EmiApi.getIndexStacks().isEmpty(); }
    @Override public List<ItemStack> catalogue() { return EmiApi.getIndexStacks().stream().map(EmiStack::getItemStack).toList(); }

    @Override public Set<ResourceLocation> query(ItemStack stack, boolean output) {
        var recipes = output ? EmiApi.getRecipeManager().getRecipesByOutput(EmiStack.of(stack))
            : EmiApi.getRecipeManager().getRecipesByInput(EmiStack.of(stack));
        return recipes.stream().map(dev.emi.emi.api.recipe.EmiRecipe::getId)
            .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    }

    @Override public void showRecipe(ResourceLocation id) {
        var recipe = EmiApi.getRecipeManager().getRecipe(id);
        if (recipe == null) throw new IllegalStateException("EMI did not register " + id);
        EmiApi.displayRecipe(recipe);
    }
}
