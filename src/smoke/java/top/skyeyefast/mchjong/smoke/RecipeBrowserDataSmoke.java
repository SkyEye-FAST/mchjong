package top.skyeyefast.mchjong.smoke;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExample;
import top.skyeyefast.mchjong.compat.recipes.SupplyRecipeExamples;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.recipe.SupplyCraftingRecipe;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Every display and cycling alternative is crafted in the actual integrated server world. */
final class RecipeBrowserDataSmoke {
    static void verify(Level level) {
        var examples = SupplyRecipeExamples.create(level);
        check(examples.size() == examples.stream().map(SupplyRecipeExample::id).distinct().count(), "Duplicate recipe display IDs");
        var cuttingFaces = new HashSet<TileData>();
        boolean keptSpares = false;
        int alternatives = 0;
        for (var example : examples) {
            check(ItemStack.matches(example.output(), example.assemble(level)), example.id().toString());
            check(!example.firstAlternatives().isEmpty(), "Empty displayed ingredient");
            for (var alternative : example.firstAlternatives()) {
                alternatives++;
                var input = new ArrayList<>(example.input());
                input.set(0, alternative);
                var candidate = new SupplyRecipeExample(example.id(), example.source(), input, example.output(),
                    example.cutting(), List.of(alternative));
                check(ItemStack.matches(example.output(), candidate.assemble(level)), example.id().toString());
            }
            if (example.cutting()) {
                cuttingFaces.add(MahjongSupplies.tile(example.output()));
                check(MahjongSupplies.color(example.input().getFirst()) == MahjongSupplies.color(example.output()), "Cutting lost back color");
                var engraved = example.output().copy();
                var invalid = new SupplyRecipeExample(example.id(), example.source(), List.of(engraved), example.output(), true, List.of(engraved));
                check(invalid.assemble(level).isEmpty(), "An engraved tile appeared as a valid blank");
            } else if (example.source().value() instanceof SupplyCraftingRecipe recipe
                && recipe.operation() == SupplyCraftingRecipe.Operation.ENGRAVE_SET && example.id().getPath().endsWith("/spares")) {
                var contents = MahjongSupplies.contents(example.output());
                check(144 == MahjongSupplies.tileCount(contents), "Engraving lost spare tiles");
                check(4 == contents.stream().filter(stack -> stack.is(MahjongContent.POINT_STICK)).mapToInt(ItemStack::getCount).sum(), "Engraving lost point sticks");
                check(contents.stream().filter(stack -> stack.is(MahjongContent.POINT_STICK)).allMatch(stack -> stack.get(MahjongComponents.POINTS) == 1000), "Engraving changed currency");
                keptSpares = true;
            }
        }
        check(keptSpares, "Missing spare-tile example");
        check(cuttingFaces.contains(new TileData(TileData.FIRST_FLOWER, TileMaterial.BONE, false)), "Missing flower engraving");
        check(cuttingFaces.contains(new TileData(4, TileMaterial.BONE, true)), "Missing red engraving");
        org.slf4j.LoggerFactory.getLogger("mchjong-smoke").info("Verified {} recipe examples and {} ingredient alternatives on the server", examples.size(), alternatives);
    }

    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
