package top.skyeyefast.mchjong.compat.rei;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExample;

final class SupplyReiDisplay extends BasicDisplay {
    static final CategoryIdentifier<SupplyReiDisplay> CATEGORY = CategoryIdentifier.of("mchjong", "supplies");
    private final boolean shapeless;

    SupplyReiDisplay(SupplyRecipeExample example) {
        super(IntStream.range(0, example.input().size())
                .mapToObj(index -> EntryIngredients.ofItemStacks(example.ingredients(index))).toList(),
            List.of(EntryIngredients.of(example.output())), Optional.of(example.id()));
        shapeless = example.shapeless();
    }

    @Override public CategoryIdentifier<?> getCategoryIdentifier() { return CATEGORY; }
    boolean shapeless() { return shapeless; }
}
