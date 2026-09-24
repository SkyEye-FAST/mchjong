package top.skyeyefast.mchjong.compat.create;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Snapshot adapters only. Create owns matching, consumption, output capacity and machine timing. */
public final class CreateProcessing {
    private CreateProcessing() {}

    /** Identifies only our immutable, exact-component recipes at the native basin boundary. */
    public static final class WorkshopRecipe extends BasinRecipe {
        private WorkshopRecipe(boolean mixing, ProcessingRecipeParams params) {
            super(mixing ? AllRecipeTypes.MIXING : AllRecipeTypes.COMPACTING, params);
        }
        @Override protected int getMaxInputCount() { return 18 * 64; }
    }

    public record Batch(String operation, List<ItemStack> inputs, List<ItemStack> outputs, BasinRecipe recipe) {}

    public static Batch mixing(List<ItemStack> inventory) {
        var targets = inventory.stream().filter(stack -> stack.is(MahjongContent.TILE_ITEM) || stack.is(MahjongContent.BOX_ITEM)).toList();
        for (var reagent : inventory) {
            boolean undo = reagent.is(MahjongContent.UNDO_DYE);
            if (!undo && !(reagent.getItem() instanceof DyeItem)) continue;
            var color = undo ? null : ((DyeItem) reagent.getItem()).getDyeColor();
            for (int first = 0; first < targets.size(); first++) for (int second = first + 1; second < targets.size(); second++) {
                var pair = List.of(targets.get(first), targets.get(second));
                var dyed = MahjongSupplies.dyeBatch(pair, color);
                if (!dyed.isEmpty()) return basin("dye_backs", true,
                    List.of(pair.getFirst(), pair.getLast(), reagent.copyWithCount(1)), dyed);
            }
            if (undo) continue;
            for (var template : inventory) {
                if (!template.is(MahjongContent.POINT_STICK)) continue;
                var blanks = take(inventory, template, 16);
                var marked = MahjongSupplies.markSticks(blanks, reagent, 16);
                if (marked.isEmpty()) continue;
                var inputs = new ArrayList<>(blanks);
                inputs.add(reagent.copyWithCount(1));
                return basin("mark_sticks", true, inputs, List.of(marked));
            }
        }
        return null;
    }

    public static Batch pressing(List<ItemStack> inventory) {
        for (var box : inventory) {
            if (!MahjongSupplies.validBox(box)) continue;
            var plate = inventory.stream().filter(stack -> stack.is(CreateCompat.PRINTING_PLATE.get())).findFirst().orElse(ItemStack.EMPTY);
            var dye = inventory.stream().filter(stack -> stack.is(MahjongContent.MAHJONG_DYE)).findFirst().orElse(ItemStack.EMPTY);
            if (!plate.isEmpty()) {
                if (dye.isEmpty()) continue;
                int needed = 144 - MahjongSupplies.tileCount(MahjongSupplies.contents(box));
                if (needed < 0) continue;
                var templates = new ArrayList<>(inventory.stream().filter(stack -> stack.is(MahjongContent.TILE_ITEM)).toList());
                if (needed == 0) templates.add(ItemStack.EMPTY);
                for (var template : templates) {
                    var blanks = take(inventory, template, needed);
                    var printed = MahjongSupplies.printBox(box, blanks, MahjongSupplies.facePreset(plate));
                    if (printed.isEmpty()) continue;
                    var inputs = new ArrayList<>(blanks);
                    inputs.add(box);
                    inputs.add(dye.copyWithCount(1));
                    inputs.add(plate.copyWithCount(1));
                    return basin("print_tiles", false, inputs, List.of(printed, plate.copyWithCount(1)));
                }
                continue;
            }
            // A returned plate can be routed back to this basin; partially packed blanks can also be printed later.
            var incoming = inventory.stream().filter(MahjongSupplies::storable).toList();
            var packed = MahjongSupplies.pack(box, incoming);
            if (packed.isEmpty()) continue;
            var inputs = new ArrayList<>(incoming);
            inputs.add(box);
            return basin("pack_box", false, inputs, List.of(packed));
        }
        return null;
    }

    private static List<ItemStack> take(List<ItemStack> inventory, ItemStack template, int count) {
        var result = new ArrayList<ItemStack>();
        for (var stack : inventory) {
            if (count == 0) break;
            if (!ItemStack.isSameItemSameComponents(template, stack) || stack.getCount() > stack.getMaxStackSize()) continue;
            int amount = Math.min(stack.getCount(), count);
            result.add(stack.copyWithCount(amount));
            count -= amount;
        }
        return count == 0 ? List.copyOf(result) : List.of();
    }

    private static Batch basin(String operation, boolean mixing, List<ItemStack> inputs, List<ItemStack> outputs) {
        // The normal basin ingredient limit is 64 individual items, not 64 stacks. Printing consumes 144 tiles.
        StandardProcessingRecipe.Factory<BasinRecipe> factory = params -> new WorkshopRecipe(mixing, params);
        var builder = new StandardProcessingRecipe.Builder<>(factory, MahjongContent.id("create/" + operation)).duration(100);
        for (var stack : inputs) {
            var ingredient = DataComponentIngredient.of(true, stack);
            for (int count = 0; count < stack.getCount(); count++) builder.require(ingredient);
        }
        outputs.forEach(builder::output);
        return new Batch(operation, inputs.stream().map(ItemStack::copy).toList(), outputs.stream().map(ItemStack::copy).toList(), builder.build());
    }

    public static DeployerApplicationRecipe deploying(ItemStack target, ItemStack reagent) {
        var output = MahjongSupplies.redFive(target, reagent);
        if (output.isEmpty()) return null;
        return new ItemApplicationRecipe.Builder<>(DeployerApplicationRecipe::new, MahjongContent.id("create/red_five"))
            .require(DataComponentIngredient.of(true, target)).require(DataComponentIngredient.of(true, reagent))
            .output(output).build();
    }

    static void deployerRecipe(DeployerRecipeSearchEvent event) {
        var inventory = event.getInventory();
        var recipe = deploying(inventory.getItem(0), inventory.getItem(1));
        if (recipe != null) event.addRecipe(() -> Optional.of(new RecipeHolder<>(MahjongContent.id("create/red_five"), recipe)), 100);
    }
}
