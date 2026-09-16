package top.skyeyefast.mchjong.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Uses the vanilla stonecutter menu while checking and preserving input components. */
public final class TileCuttingRecipe extends StonecutterRecipe {
    public TileCuttingRecipe(String group, Ingredient ingredient, ItemStack result) {
        super(group, ingredient, result);
        TileData data = result.get(MahjongComponents.TILE);
        if (!result.is(MahjongContent.TILE_ITEM) || result.getCount() != 1 || data == null || data.blank() || !data.valid())
            throw new IllegalArgumentException("An engraving recipe must produce one valid engraved tile");
    }

    @Override public boolean matches(SingleRecipeInput input, Level level) {
        return engraveable(input.item()) && super.matches(input, level);
    }
    @Override public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        if (!engraveable(input.item())) return ItemStack.EMPTY;
        TileData design = MahjongSupplies.tile(result);
        ItemStack output = input.item().copyWithCount(1);
        output.set(MahjongComponents.TILE, MahjongSupplies.tile(input.item()).engraved(design.face(), design.red()));
        return output;
    }
    @Override public RecipeSerializer<?> getSerializer() { return MahjongRecipes.ENGRAVE_TILE; }

    private static boolean engraveable(ItemStack stack) {
        TileData data = MahjongSupplies.tile(stack);
        return stack.is(MahjongContent.TILE_ITEM) && MahjongSupplies.storable(stack) && data.valid() && data.blank();
    }

    public static final class Serializer extends SingleItemRecipe.Serializer<TileCuttingRecipe> {
        public Serializer() { super(TileCuttingRecipe::new); }
    }
}
