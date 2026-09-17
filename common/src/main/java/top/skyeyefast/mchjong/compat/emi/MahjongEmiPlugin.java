package top.skyeyefast.mchjong.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import top.skyeyefast.mchjong.client.MahjongBoxScreen;
import top.skyeyefast.mchjong.client.MahjongTableScreen;
import top.skyeyefast.mchjong.client.PointStickScreen;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExamples;
import top.skyeyefast.mchjong.compat.recipes.SupplySubtype;
import top.skyeyefast.mchjong.recipe.SupplyCraftingRecipe;
import top.skyeyefast.mchjong.recipe.TileCuttingRecipe;

@EmiEntrypoint
public final class MahjongEmiPlugin implements EmiPlugin {
    @Override public void register(EmiRegistry registry) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var comparison = Comparison.compareData(stack -> SupplySubtype.of(stack.getItemStack()));
        SupplySubtype.items().forEach(item -> registry.setDefaultComparison(item, comparison));
        var examples = SupplyRecipeExamples.create(level);
        var originals = registry.getRecipeManager().getRecipes().stream()
            .filter(holder -> holder.value() instanceof TileCuttingRecipe || holder.value() instanceof SupplyCraftingRecipe)
            .map(net.minecraft.world.item.crafting.RecipeHolder::id).collect(java.util.stream.Collectors.toSet());
        registry.removeRecipes(recipe -> originals.contains(recipe.getId()));
        for (var example : examples) {
            if (example.cutting()) registry.addRecipe(new SupplyEmiCuttingRecipe(example));
            else {
                var ingredients = java.util.stream.IntStream.range(0, example.input().size()).mapToObj(index ->
                    EmiIngredient.of(example.ingredients(index).stream().map(EmiStack::of).toList())).toList();
                registry.addRecipe(new EmiCraftingRecipe(ingredients, EmiStack.of(example.output()), example.id(), example.shapeless()) {
                    @Override public net.minecraft.world.item.crafting.RecipeHolder<?> getBackingRecipe() { return example.source(); }
                });
            }
        }
        registry.addScreenBoundsProvider(MahjongBoxScreen.class, screen -> bounds(screen.browserBounds()));
        registry.addScreenBoundsProvider(MahjongTableScreen.class, screen -> bounds(screen.browserBounds()));
        registry.addScreenBoundsProvider(PointStickScreen.class, screen -> bounds(screen.browserBounds()));
    }

    private static Bounds bounds(ScreenRectangle rectangle) {
        return new Bounds(rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height());
    }
}
