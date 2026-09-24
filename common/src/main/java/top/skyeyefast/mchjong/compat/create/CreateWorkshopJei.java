package top.skyeyefast.mchjong.compat.create;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.client.MahjongUi;
import top.skyeyefast.mchjong.item.MahjongSupplies;

@JeiPlugin
public final class CreateWorkshopJei implements IModPlugin {
    public static final RecipeType<CreateWorkshopDisplays.Display> TYPE = RecipeType.create("mchjong", "create_workshop", CreateWorkshopDisplays.Display.class);
    @Override public ResourceLocation getPluginUid() { return new ResourceLocation("mchjong", "create_workshop"); }
    private static boolean available() { return BuiltInRegistries.ITEM.containsKey(new ResourceLocation("mchjong", "mahjong_printing_plate")); }
    @Override public void registerItemSubtypes(ISubtypeRegistration registration) {
        if (!available()) return;
        registration.registerSubtypeInterpreter(CreateCompat.PRINTING_PLATE, new ISubtypeInterpreter<ItemStack>() {
            @Override public Object getSubtypeData(ItemStack stack, UidContext context) { return MahjongSupplies.facePreset(stack); }
            @Override public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) { return MahjongSupplies.facePreset(stack).getSerializedName(); }
        });
    }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        if (available()) registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        if (available()) registration.addRecipes(TYPE, CreateWorkshopDisplays.dynamic());
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (available()) registration.addRecipeCatalyst(new ItemStack(CreateCompat.PRINTING_PLATE), TYPE);
    }

    private static final class Category implements IRecipeCategory<CreateWorkshopDisplays.Display> {
        private final IDrawable icon;
        Category(IGuiHelper gui) { icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(CreateCompat.PRINTING_PLATE)); }
        @Override public RecipeType<CreateWorkshopDisplays.Display> getRecipeType() { return TYPE; }
        @Override public Component getTitle() { return Component.translatable("browser.mchjong.create"); }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return 178; }
        @Override public int getHeight() { return 98; }
        @Override public ResourceLocation getRegistryName(CreateWorkshopDisplays.Display display) { return display.id(); }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, CreateWorkshopDisplays.Display display, IFocusGroup focuses) {
            for (int i = 0; i < display.inputs().size(); i++) builder.addSlot(RecipeIngredientRole.INPUT, 5 + i % 3 * 18, 24 + i / 3 * 18).addItemStack(display.inputs().get(i));
            for (int i = 0; i < display.outputs().size(); i++) builder.addSlot(RecipeIngredientRole.OUTPUT, 126 + i % 2 * 18, 33 + i / 2 * 18).addItemStack(display.outputs().get(i));
            builder.addSlot(RecipeIngredientRole.CATALYST, 82, 25).addItemStack(display.machine());
        }
        @Override public void draw(CreateWorkshopDisplays.Display display, IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
            var font = Minecraft.getInstance().font;
            MahjongUi.panel(graphics, 0, 0, 178, 98);
            MahjongUi.text(graphics, font, display.title(), 4, 5, 170, MahjongUi.TEXT, false);
            graphics.drawString(font, "→", 84, 50, MahjongUi.ACCENT, false);
            MahjongUi.text(graphics, font, Component.translatable("browser.mchjong.components"), 4, 84, 170, MahjongUi.MUTED, false);
        }
    }
}
