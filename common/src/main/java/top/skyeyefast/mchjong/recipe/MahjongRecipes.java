package top.skyeyefast.mchjong.recipe;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class MahjongRecipes {
    public static final Map<SupplyCraftingRecipe.Operation, RecipeSerializer<SupplyCraftingRecipe>> CRAFTING;
    public static final Map<String, RecipeSerializer<?>> SERIALIZERS;
    static {
        var crafting = new EnumMap<SupplyCraftingRecipe.Operation, RecipeSerializer<SupplyCraftingRecipe>>(SupplyCraftingRecipe.Operation.class);
        var serializers = new LinkedHashMap<String, RecipeSerializer<?>>();
        for (var operation : SupplyCraftingRecipe.Operation.values()) {
            var recipe = new SupplyCraftingRecipe(operation);
            RecipeSerializer<SupplyCraftingRecipe> serializer =
                new RecipeSerializer<>(MapCodec.unit(recipe), StreamCodec.unit(recipe));
            crafting.put(operation, serializer);
            serializers.put(operation.name().toLowerCase(Locale.ROOT), serializer);
        }
        CRAFTING = Map.copyOf(crafting);
        SERIALIZERS = Map.copyOf(serializers);
    }
    private MahjongRecipes() {}
}
