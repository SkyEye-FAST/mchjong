package top.skyeyefast.mchjong.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExample;
import top.skyeyefast.mchjong.client.MahjongUi;

final class SupplyJeiCategory implements IRecipeCategory<SupplyRecipeExample> {
    private final RecipeType<SupplyRecipeExample> type;
    private final IDrawable icon;
    private final boolean cutting;

    SupplyJeiCategory(RecipeType<SupplyRecipeExample> type, IGuiHelper gui, boolean cutting) {
        this.type = type;
        this.cutting = cutting;
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(cutting ? Items.STONECUTTER : Items.CRAFTING_TABLE));
    }

    @Override public RecipeType<SupplyRecipeExample> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.translatable(cutting ? "browser.mchjong.engraving" : "browser.mchjong.crafting"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 134; }
    @Override public int getHeight() { return 78; }
    @Override public ResourceLocation getRegistryName(SupplyRecipeExample example) { return example.id(); }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, SupplyRecipeExample example, IFocusGroup focuses) {
        for (int i = 0; i < example.input().size(); i++)
            if (!example.input().get(i).isEmpty()) builder.addSlot(RecipeIngredientRole.INPUT,
                inputX(i), inputY(i)).addItemStacks(example.ingredients(i));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 100, 23).addItemStack(example.output());
        if (example.shapeless()) builder.setShapeless();
    }

    @Override public void draw(SupplyRecipeExample example, IRecipeSlotsView slots, GuiGraphics graphics, double x, double y) {
        var font = Minecraft.getInstance().font;
        MahjongUi.panel(graphics, 0, 0, getWidth(), getHeight());
        for (int i = 0; i < example.input().size(); i++)
            if (!example.input().get(i).isEmpty()) MahjongUi.slot(graphics, inputX(i), inputY(i), false);
        MahjongUi.slot(graphics, 100, 23, false);
        graphics.drawString(font, "→", 71, 27, MahjongUi.ACCENT, false);
        MahjongUi.text(graphics, font, Component.translatable("browser.mchjong.components"), 4, 64, 126, MahjongUi.MUTED, false);
    }

    private int inputX(int index) { return cutting ? 23 : 5 + index % 3 * 18; }
    private int inputY(int index) { return cutting ? 23 : 5 + index / 3 * 18; }
}
