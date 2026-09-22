package top.skyeyefast.mchjong.compat.recipes;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import top.skyeyefast.mchjong.recipe.SupplyCraftingRecipe;

/** Display data only. The loaded server recipe remains the sole crafting implementation. */
public record SupplyRecipeExample(ResourceLocation id, Recipe<?> source, List<ItemStack> input,
                                  ItemStack output, List<ItemStack> firstAlternatives) {
    public List<ItemStack> ingredients(int slot) {
        return slot == 0 ? firstAlternatives : List.of(input.get(slot));
    }
    public ItemStack assemble(Level level) {
        var recipe = (SupplyCraftingRecipe) source;
        var grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return false; }
        }, 3, 3);
        for (int slot = 0; slot < input.size(); slot++) grid.setItem(slot, input.get(slot));
        return recipe.matches(grid, level) ? recipe.assemble(grid, level.registryAccess()) : ItemStack.EMPTY;
    }

    public boolean shapeless() {
        return source instanceof SupplyCraftingRecipe recipe
            && recipe.operation() != SupplyCraftingRecipe.Operation.UPGRADE_TABLE;
    }
}
