package top.skyeyefast.mchjong.compat.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.client.MahjongUi;

/** NeoForge's REI view of the shared Create workshop displays. */
public final class CreateWorkshopRei {
    public static final CategoryIdentifier<Display> CATEGORY = CategoryIdentifier.of("mchjong", "create_workshop");

    private CreateWorkshopRei() {}

    public static DisplayCategory<Display> category() { return new Category(); }

    public static List<Display> displays() {
        var examples = new ArrayList<>(CreateWorkshopDisplays.dynamic());
        var level = Minecraft.getInstance().level;
        if (level != null) examples.addAll(CreateWorkshopDisplays.staticRecipes(level));
        return examples.stream().map(Display::new).toList();
    }

    public static final class Display extends BasicDisplay {
        private final CreateWorkshopDisplays.Display recipe;

        private Display(CreateWorkshopDisplays.Display recipe) {
            super(recipe.inputs().stream().map(EntryIngredients::of).toList(),
                recipe.outputs().stream().map(EntryIngredients::of).toList(), Optional.of(recipe.id()));
            this.recipe = recipe;
        }

        @Override public CategoryIdentifier<?> getCategoryIdentifier() { return CATEGORY; }
    }

    private static final class Category implements DisplayCategory<Display> {
        @Override public CategoryIdentifier<? extends Display> getCategoryIdentifier() { return CATEGORY; }
        @Override public Component getTitle() { return Component.translatable("browser.mchjong.create"); }
        @Override public Renderer getIcon() { return EntryStacks.of(CreateCompat.PRINTING_PLATE.get()); }
        @Override public int getDisplayWidth(Display display) { return 178; }
        @Override public int getDisplayHeight() { return 80; }

        @Override public List<Widget> setupDisplay(Display display, Rectangle bounds) {
            var widgets = new ArrayList<Widget>();
            widgets.add(Widgets.createRecipeBase(bounds));
            widgets.add(Widgets.createDrawableWidget((graphics, mouseX, mouseY, delta) -> {
                MahjongUi.panel(graphics, bounds.x, bounds.y, bounds.width, bounds.height);
                MahjongUi.text(graphics, Minecraft.getInstance().font, display.recipe.title(),
                    bounds.x + 4, bounds.y + 5, 170, MahjongUi.TEXT, false);
                CreateWorkshopAnimation.render(graphics, display.recipe, bounds.x, bounds.y);
                graphics.drawString(Minecraft.getInstance().font, "→", bounds.x + 105, bounds.y + 49, MahjongUi.ACCENT, false);
            }));
            for (int i = 0; i < display.getInputEntries().size(); i++)
                widgets.add(Widgets.createSlot(new Point(bounds.x + 5 + i % 3 * 18, bounds.y + 24 + i / 3 * 18))
                    .entries(display.getInputEntries().get(i)).markInput());
            for (int i = 0; i < display.getOutputEntries().size(); i++)
                widgets.add(Widgets.createSlot(new Point(bounds.x + 126 + i % 2 * 18, bounds.y + 33 + i / 2 * 18))
                    .entries(display.getOutputEntries().get(i)).markOutput());
            return widgets;
        }
    }
}
