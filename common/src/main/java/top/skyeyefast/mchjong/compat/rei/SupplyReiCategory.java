package top.skyeyefast.mchjong.compat.rei;

import java.util.ArrayList;
import java.util.List;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

final class SupplyReiCategory implements DisplayCategory<SupplyReiDisplay> {
    @Override public CategoryIdentifier<? extends SupplyReiDisplay> getCategoryIdentifier() {
        return SupplyReiDisplay.CATEGORY;
    }

    @Override public Component getTitle() { return Component.translatable("browser.mchjong.crafting"); }
    @Override public Renderer getIcon() { return EntryStacks.of(Items.CRAFTING_TABLE); }
    @Override public int getDisplayWidth(SupplyReiDisplay display) { return 134; }
    @Override public int getDisplayHeight() { return 78; }

    @Override public List<Widget> setupDisplay(SupplyReiDisplay display, Rectangle bounds) {
        var widgets = new ArrayList<Widget>();
        widgets.add(Widgets.createRecipeBase(bounds));
        for (int i = 0; i < display.getInputEntries().size(); i++) {
            if (display.getInputEntries().get(i).isEmpty()) continue;
            widgets.add(Widgets.createSlot(new Point(bounds.x + inputX(i), bounds.y + inputY(i)))
                .entries(display.getInputEntries().get(i)).markInput());
        }
        widgets.add(Widgets.createArrow(new Point(bounds.x + 68, bounds.y + 24)));
        widgets.add(Widgets.createSlot(new Point(bounds.x + 100, bounds.y + 23))
            .entries(display.getOutputEntries().getFirst()).markOutput());
        if (display.shapeless()) widgets.add(Widgets.createShapelessIcon(new Point(bounds.x + 58, bounds.y + 57)));
        return widgets;
    }

    private static int inputX(int index) { return 5 + index % 3 * 18; }
    private static int inputY(int index) { return 5 + index / 3 * 18; }
}
