package top.skyeyefast.mchjong.compat.create;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.skyeyefast.mchjong.client.MahjongUi;
import top.skyeyefast.mchjong.item.MahjongSupplies;

@JeiPlugin
public final class CreateWorkshopJei implements IModPlugin {
    private static final RecipeType<CreateWorkshopDisplays.Display> TYPE = RecipeType.create("mchjong", "create_workshop", CreateWorkshopDisplays.Display.class);
    @Override public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("mchjong", "create_workshop"); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!ModList.get().isLoaded("create")) return;
        var icon = registration.getJeiHelpers().getGuiHelper().createDrawableIngredient(VanillaTypes.ITEM_STACK,
            new ItemStack(CreateCompat.PRINTING_PLATE.get()));
        registration.addRecipeCategories(new Category(icon));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        if (ModList.get().isLoaded("create")) registration.addRecipes(TYPE, CreateWorkshopDisplays.dynamic());
    }
    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        if (!ModList.get().isLoaded("create")) return;
        registration.registerSubtypeInterpreter(CreateCompat.PRINTING_PLATE.get(), new ISubtypeInterpreter<ItemStack>() {
            @Override public Object getSubtypeData(ItemStack stack, UidContext context) { return MahjongSupplies.facePreset(stack); }
            @SuppressWarnings("deprecation")
            @Override public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) { return ""; }
        });
    }

    private record Category(IDrawable icon) implements IRecipeCategory<CreateWorkshopDisplays.Display> {
        @Override public RecipeType<CreateWorkshopDisplays.Display> getRecipeType() { return TYPE; }
        @Override public Component getTitle() { return Component.translatable("browser.mchjong.create"); }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return 178; }
        @Override public int getHeight() { return 98; }
        @Override public ResourceLocation getRegistryName(CreateWorkshopDisplays.Display recipe) { return recipe.id(); }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, CreateWorkshopDisplays.Display recipe, IFocusGroup focuses) {
            for (int i = 0; i < recipe.inputs().size(); i++) builder.addSlot(RecipeIngredientRole.INPUT,
                5 + i % 3 * 18, 24 + i / 3 * 18).addItemStack(recipe.inputs().get(i));
            for (int i = 0; i < recipe.outputs().size(); i++) builder.addSlot(RecipeIngredientRole.OUTPUT,
                126 + i % 2 * 18, 33 + i / 2 * 18).addItemStack(recipe.outputs().get(i));
            builder.addSlot(RecipeIngredientRole.CATALYST, 82, 25).addItemStack(recipe.machine());
        }
        @Override public void draw(CreateWorkshopDisplays.Display recipe, IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
            var font = Minecraft.getInstance().font;
            MahjongUi.panel(graphics, 0, 0, getWidth(), getHeight());
            MahjongUi.text(graphics, font, recipe.title(), 4, 5, 170, MahjongUi.TEXT, false);
            for (int i = 0; i < recipe.inputs().size(); i++) MahjongUi.slot(graphics, 5 + i % 3 * 18, 24 + i / 3 * 18, false);
            for (int i = 0; i < recipe.outputs().size(); i++) MahjongUi.slot(graphics, 126 + i % 2 * 18, 33 + i / 2 * 18, false);
            graphics.drawString(font, "→", 84, 50, MahjongUi.ACCENT, false);
            MahjongUi.text(graphics, font, Component.translatable("browser.mchjong.components"), 4, 84, 170, MahjongUi.MUTED, false);
        }
    }
}
