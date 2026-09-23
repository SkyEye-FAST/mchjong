package top.skyeyefast.mchjong.compat.recipes;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import top.skyeyefast.mchjong.recipe.SupplyCraftingRecipe;

/** Display data only. The loaded server recipe remains the sole crafting implementation. */
public record SupplyRecipeExample(Identifier id, RecipeHolder<?> source, List<ItemStack> input,
                                  ItemStack output, List<ItemStack> firstAlternatives) {
    public List<ItemStack> ingredients(int slot) {
        return slot == 0 ? firstAlternatives : List.of(input.get(slot));
    }
    public ItemStack assemble(Level level) {
        var recipe = (SupplyCraftingRecipe) source.value();
        var grid = CraftingInput.of(3, 3, input);
        return recipe.matches(grid, level) ? recipe.assemble(grid) : ItemStack.EMPTY;
    }

    public boolean shapeless() {
        return source.value() instanceof SupplyCraftingRecipe recipe
            && recipe.operation() != SupplyCraftingRecipe.Operation.UPGRADE_TABLE;
    }
}
