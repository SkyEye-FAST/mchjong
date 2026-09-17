package top.skyeyefast.mchjong.compat.jei;

import java.util.List;
import java.util.function.Function;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.skyeyefast.mchjong.client.MahjongBoxScreen;
import top.skyeyefast.mchjong.client.MahjongTableScreen;
import top.skyeyefast.mchjong.client.PointStickScreen;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExample;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExamples;
import top.skyeyefast.mchjong.compat.recipes.SupplySubtype;
import top.skyeyefast.mchjong.recipe.SupplyCraftingRecipe;
import top.skyeyefast.mchjong.recipe.TileCuttingRecipe;

@JeiPlugin
public final class MahjongJeiPlugin implements IModPlugin {
    public static final RecipeType<SupplyRecipeExample> CRAFTING = RecipeType.create("mchjong", "supplies", SupplyRecipeExample.class);
    public static final RecipeType<SupplyRecipeExample> CUTTING = RecipeType.create("mchjong", "engraving", SupplyRecipeExample.class);
    private static IJeiRuntime runtime;

    public static IJeiRuntime runtime() { return runtime; }
    // NeoForge discovers plugin IDs before block registration opens.
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("mchjong", "supplies"); }

    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        var interpreter = new ISubtypeInterpreter<ItemStack>() {
            @Override public Object getSubtypeData(ItemStack stack, UidContext context) { return SupplySubtype.of(stack); }
            // Required by this JEI API; current component identity uses getSubtypeData exclusively.
            @SuppressWarnings("deprecation")
            @Override public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) { return ""; }
        };
        SupplySubtype.items().forEach(item -> registration.registerSubtypeInterpreter(item, interpreter));
    }

    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        var gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new SupplyJeiCategory(CRAFTING, gui, false), new SupplyJeiCategory(CUTTING, gui, true));
    }

    @Override public void registerRecipes(IRecipeRegistration registration) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var examples = SupplyRecipeExamples.create(level);
        registration.addRecipes(CRAFTING, examples.stream().filter(example -> !example.cutting()).toList());
        registration.addRecipes(CUTTING, examples.stream().filter(SupplyRecipeExample::cutting).toList());
    }

    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(Items.CRAFTING_TABLE), CRAFTING);
        registration.addRecipeCatalyst(new ItemStack(Items.STONECUTTER), CUTTING);
    }

    @Override public void onRuntimeAvailable(IJeiRuntime available) {
        runtime = available;
        var recipes = runtime.getRecipeManager();
        recipes.hideRecipes(RecipeTypes.CRAFTING, recipes.createRecipeLookup(RecipeTypes.CRAFTING).get()
            .filter(holder -> holder.value() instanceof SupplyCraftingRecipe).toList());
        recipes.hideRecipes(RecipeTypes.STONECUTTING, recipes.createRecipeLookup(RecipeTypes.STONECUTTING).get()
            .filter(holder -> holder.value() instanceof TileCuttingRecipe).toList());
    }

    @Override public void onRuntimeUnavailable() { runtime = null; }

    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        bounds(registration, MahjongBoxScreen.class, MahjongBoxScreen::browserBounds);
        bounds(registration, MahjongTableScreen.class, MahjongTableScreen::browserBounds);
        bounds(registration, PointStickScreen.class, PointStickScreen::browserBounds);
    }

    private static <T extends AbstractContainerScreen<?>> void bounds(IGuiHandlerRegistration registration,
            Class<T> type, Function<T, ScreenRectangle> bounds) {
        registration.addGuiContainerHandler(type, new IGuiContainerHandler<T>() {
            @Override public List<Rect2i> getGuiExtraAreas(T screen) {
                var area = bounds.apply(screen);
                return List.of(new Rect2i(area.left(), area.top(), area.width(), area.height()));
            }
        });
    }
}
