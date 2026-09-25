package top.skyeyefast.mchjong.compat.create;

import com.simibubi.create.AllBlocks;
import java.util.ArrayList;
import java.util.List;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.skyeyefast.mchjong.client.MahjongUi;

@EmiEntrypoint
public final class CreateWorkshopEmi implements EmiPlugin {
    @Override public void register(EmiRegistry registry) {
        if (!ModList.get().isLoaded("create") || Minecraft.getInstance().level == null) return;
        var category = new EmiRecipeCategory(ResourceLocation.fromNamespaceAndPath("mchjong", "create_workshop"),
            EmiStack.of(CreateCompat.PRINTING_PLATE.get())) {
                @Override public Component getName() { return Component.translatable("browser.mchjong.create"); }
            };
        registry.addCategory(category);
        var displays = new ArrayList<>(CreateWorkshopDisplays.dynamic());
        var staticRecipes = CreateWorkshopDisplays.staticRecipes(Minecraft.getInstance().level);
        var ids = staticRecipes.stream().map(CreateWorkshopDisplays.Display::id).collect(java.util.stream.Collectors.toSet());
        registry.removeRecipes(recipe -> ids.contains(recipe.getId()));
        displays.addAll(staticRecipes);
        for (var display : displays) registry.addRecipe(new WorkshopRecipe(category, display));
    }

    private record WorkshopRecipe(EmiRecipeCategory category, CreateWorkshopDisplays.Display display) implements EmiRecipe {
        @Override public EmiRecipeCategory getCategory() { return category; }
        @Override public ResourceLocation getId() { return display.id(); }
        @Override public List<EmiIngredient> getInputs() { return display.inputs().stream().<EmiIngredient>map(EmiStack::of).toList(); }
        @Override public List<EmiStack> getOutputs() { return display.outputs().stream().map(EmiStack::of).toList(); }
        @Override public List<EmiIngredient> getCatalysts() { return display.requiresBasin()
            ? List.of(EmiStack.of(display.machine()), EmiStack.of(AllBlocks.BASIN.get())) : List.of(EmiStack.of(display.machine())); }
        @Override public int getDisplayWidth() { return 178; }
        @Override public int getDisplayHeight() { return 80; }
        @Override public boolean supportsRecipeTree() { return false; }
        @Override public void addWidgets(WidgetHolder widgets) {
            widgets.addDrawable(0, 0, 178, 80, (graphics, mouseX, mouseY, delta) -> {
                MahjongUi.panel(graphics, 0, 0, 178, 80);
                MahjongUi.text(graphics, Minecraft.getInstance().font, display.title(), 4, 5, 170, MahjongUi.TEXT, false);
                graphics.drawString(Minecraft.getInstance().font, "→", 105, 49, MahjongUi.ACCENT, false);
            });
            for (int i = 0; i < display.inputs().size(); i++) widgets.addSlot(EmiStack.of(display.inputs().get(i)), 4 + i % 3 * 18, 23 + i / 3 * 18);
            for (int i = 0; i < display.outputs().size(); i++) widgets.addSlot(EmiStack.of(display.outputs().get(i)), 125 + i % 2 * 18, 32 + i / 2 * 18).recipeContext(this);
            widgets.addSlot(EmiStack.of(display.machine()), 81, 24).drawBack(false);
            if (display.requiresBasin()) widgets.addSlot(EmiStack.of(AllBlocks.BASIN.get()), 81, 47).drawBack(false);
        }
    }
}
