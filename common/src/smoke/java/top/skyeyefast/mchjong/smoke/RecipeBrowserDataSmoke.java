package top.skyeyefast.mchjong.smoke;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExample;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExamples;

/** Every display and cycling alternative is crafted in the actual integrated server world. */
final class RecipeBrowserDataSmoke {
    static void verify(Level level) {
        var examples = SupplyRecipeExamples.create(level);
        check(examples.size() == examples.stream().map(SupplyRecipeExample::id).distinct().count(), "Duplicate recipe display IDs");
        int alternatives = 0;
        for (var example : examples) {
            check(ItemStack.matches(example.output(), example.assemble(level)), example.id().toString());
            check(!example.firstAlternatives().isEmpty(), "Empty displayed ingredient");
            for (var alternative : example.firstAlternatives()) {
                alternatives++;
                var input = new ArrayList<>(example.input());
                input.set(0, alternative);
                var candidate = new SupplyRecipeExample(example.id(), example.source(), input, example.output(), List.of(alternative));
                check(ItemStack.matches(example.output(), candidate.assemble(level)), example.id().toString());
            }
        }
        org.slf4j.LoggerFactory.getLogger("mchjong-smoke").info("Verified {} recipe examples and {} ingredient alternatives on the server", examples.size(), alternatives);
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
