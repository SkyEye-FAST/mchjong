package top.skyeyefast.mchjong.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Vanilla 1.20 recipes with an NBT result; vanilla networking already preserves that stack. */
final class NbtRecipeSerializer<T extends Recipe<?>> implements RecipeSerializer<T> {
    private final RecipeSerializer<T> vanilla;

    NbtRecipeSerializer(RecipeSerializer<T> vanilla) { this.vanilla = vanilla; }

    @Override public T fromJson(ResourceLocation id, JsonObject json) {
        T recipe = vanilla.fromJson(id, json);
        if (json.has("result_nbt")) {
            try {
                recipe.getResultItem(RegistryAccess.EMPTY).setTag(TagParser.parseTag(json.get("result_nbt").getAsString()));
            } catch (CommandSyntaxException failure) {
                throw new JsonSyntaxException("Invalid result NBT for " + id, failure);
            }
        }
        return recipe;
    }

    @Override public T fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) { return vanilla.fromNetwork(id, buffer); }
    @Override public void toNetwork(FriendlyByteBuf buffer, T recipe) { vanilla.toNetwork(buffer, recipe); }
}
