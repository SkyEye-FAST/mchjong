package top.skyeyefast.mchjong.compat.create;

import top.skyeyefast.mchjong.platform.ItemRegistry;
import top.skyeyefast.mchjong.platform.ResourceIds;
import com.simibubi.create.AllBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import top.skyeyefast.mchjong.client.MahjongUi;

@JeiPlugin
public final class CreateWorkshopJei implements IModPlugin {
    private static final RecipeType<CreateWorkshopDisplays.Display> TYPE = RecipeType.create("mchjong", "create_workshop", CreateWorkshopDisplays.Display.class);
    @Override public ResourceLocation getPluginUid() { return ResourceIds.of("mchjong", "create_workshop"); }
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {
        if (!ItemRegistry.containsKey(ResourceIds.of("mchjong", "mahjong_printing_plate"))) return;
        var icon = registration.getJeiHelpers().getGuiHelper().createDrawableIngredient(VanillaTypes.ITEM_STACK,
            new ItemStack(CreateCompat.PRINTING_PLATE));
        registration.addRecipeCategories(new Category(icon));
    }
    @Override public void registerRecipes(IRecipeRegistration registration) {
        if (ItemRegistry.containsKey(ResourceIds.of("mchjong", "mahjong_printing_plate"))) registration.addRecipes(TYPE, CreateWorkshopDisplays.dynamic());
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        if (!ItemRegistry.containsKey(ResourceIds.of("mchjong", "mahjong_printing_plate"))) return;
        registration.addRecipeCatalyst(new ItemStack(AllBlocks.MECHANICAL_PRESS.get()), TYPE);
        registration.addRecipeCatalyst(new ItemStack(AllBlocks.MECHANICAL_MIXER.get()), TYPE);
        registration.addRecipeCatalyst(new ItemStack(AllBlocks.DEPLOYER.get()), TYPE);
    }
    private record Category(IDrawable icon) implements IRecipeCategory<CreateWorkshopDisplays.Display> {
        @Override public RecipeType<CreateWorkshopDisplays.Display> getRecipeType() { return TYPE; }
        @Override public Component getTitle() { return Component.translatable("browser.mchjong.create"); }
        @Override public IDrawable getIcon() { return icon; }
        @Override public int getWidth() { return 178; }
        @Override public int getHeight() { return 80; }
        @Override public ResourceLocation getRegistryName(CreateWorkshopDisplays.Display recipe) { return recipe.id(); }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, CreateWorkshopDisplays.Display recipe, IFocusGroup focuses) {
            for (int i = 0; i < recipe.inputs().size(); i++) builder.addSlot(RecipeIngredientRole.INPUT,
                5 + i % 3 * 18, 24 + i / 3 * 18).addItemStack(recipe.inputs().get(i));
            for (int i = 0; i < recipe.outputs().size(); i++) builder.addSlot(RecipeIngredientRole.OUTPUT,
                126 + i % 2 * 18, 33 + i / 2 * 18).addItemStack(recipe.outputs().get(i));
        }
        @Override public void draw(CreateWorkshopDisplays.Display recipe, IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
            var font = Minecraft.getInstance().font;
            MahjongUi.panel(graphics, 0, 0, getWidth(), getHeight());
            MahjongUi.text(graphics, font, recipe.title(), 4, 5, 170, MahjongUi.TEXT, false);
            for (int i = 0; i < recipe.inputs().size(); i++) MahjongUi.slot(graphics, 5 + i % 3 * 18, 24 + i / 3 * 18, false);
            for (int i = 0; i < recipe.outputs().size(); i++) MahjongUi.slot(graphics, 126 + i % 2 * 18, 33 + i / 2 * 18, false);
            CreateWorkshopAnimation.render(graphics, recipe, 0, 0);
            graphics.drawString(font, "→", 105, 49, MahjongUi.ACCENT, false);
        }
    }
}
