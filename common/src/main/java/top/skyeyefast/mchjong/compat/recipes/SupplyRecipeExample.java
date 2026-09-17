package top.skyeyefast.mchjong.compat.recipes;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import top.skyeyefast.mchjong.recipe.SupplyCraftingRecipe;
import top.skyeyefast.mchjong.recipe.TileCuttingRecipe;

/** Display data only. The loaded server recipe remains the sole crafting implementation. */
public record SupplyRecipeExample(ResourceLocation id, RecipeHolder<?> source, List<ItemStack> input,
                                  ItemStack output, boolean cutting, List<ItemStack> firstAlternatives) {
    public List<ItemStack> ingredients(int slot) {
        return slot == 0 ? firstAlternatives : List.of(input.get(slot));
    }
    public ItemStack assemble(Level level) {
        if (source.value() instanceof TileCuttingRecipe recipe) {
            var grid = new SingleRecipeInput(input.getFirst());
            return recipe.matches(grid, level) ? recipe.assemble(grid, level.registryAccess()) : ItemStack.EMPTY;
        }
        var recipe = (SupplyCraftingRecipe) source.value();
        var grid = CraftingInput.of(3, 3, input);
        return recipe.matches(grid, level) ? recipe.assemble(grid, level.registryAccess()) : ItemStack.EMPTY;
    }

    public boolean shapeless() {
        return source.value() instanceof SupplyCraftingRecipe recipe
            && recipe.operation() != SupplyCraftingRecipe.Operation.UPGRADE_TABLE;
    }
}
