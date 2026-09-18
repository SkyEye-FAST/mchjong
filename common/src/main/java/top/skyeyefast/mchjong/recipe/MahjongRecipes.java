package top.skyeyefast.mchjong.recipe;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;

public final class MahjongRecipes {
    public static final Map<SupplyCraftingRecipe.Operation, RecipeSerializer<SupplyCraftingRecipe>> CRAFTING;
    public static final Map<String, RecipeSerializer<?>> SERIALIZERS;
    static {
        var crafting = new EnumMap<SupplyCraftingRecipe.Operation, RecipeSerializer<SupplyCraftingRecipe>>(SupplyCraftingRecipe.Operation.class);
        var serializers = new LinkedHashMap<String, RecipeSerializer<?>>();
        for (var operation : SupplyCraftingRecipe.Operation.values()) {
            RecipeSerializer<SupplyCraftingRecipe> serializer = new SimpleCraftingRecipeSerializer<>(category -> new SupplyCraftingRecipe(category, operation));
            crafting.put(operation, serializer);
            serializers.put(operation.name().toLowerCase(Locale.ROOT), serializer);
        }
        CRAFTING = Map.copyOf(crafting);
        SERIALIZERS = Map.copyOf(serializers);
    }
    private MahjongRecipes() {}
}
