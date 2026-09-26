package top.skyeyefast.mchjong.smoke;

import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import top.skyeyefast.mchjong.compat.create.CreateCompat;
import top.skyeyefast.mchjong.compat.create.CreatePlatform;
import top.skyeyefast.mchjong.compat.create.CreateProcessing;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;

/** One shared, real-world transaction case; only native inventory access differs by loader. */
final class CreateSmoke {
    private CreateSmoke() {}

    static void verify(ServerPlayer player) throws ReflectiveOperationException {
        var level = player.serverLevel();
        var pos = player.blockPosition().offset(3, 4, 0);
        level.setBlockAndUpdate(pos, AllBlocks.BASIN.getDefaultState());
        level.setBlockAndUpdate(pos.above(2), AllBlocks.MECHANICAL_PRESS.getDefaultState());
        var basin = (BasinBlockEntity) level.getBlockEntity(pos);
        check(basin != null, "Missing basin");
        basin.initialize();
        try {
            var blank = MahjongSupplies.tile(new TileData(-1, TileMaterial.GLASS, false), DyeColor.CYAN, 64);
            blank.setHoverName(Component.literal("Workshop tiles"));
            var plate = new ItemStack(CreateCompat.PRINTING_PLATE);
            plate.setHoverName(Component.literal("Reusable plate"));
            var box = new ItemStack(MahjongContent.BOX_ITEM);
            box.setHoverName(Component.literal("Workshop box"));
            var inputs = List.of(blank, blank.copy(), blank.copyWithCount(16), box, new ItemStack(MahjongContent.MAHJONG_DYE), plate);
            for (var input : inputs) check(CreateSmokeInventory.insert(basin, input.copy()), "Native insertion rejected full-deck ingredients");
            check(total(CreatePlatform.inventory(basin)) == 147, "Full-deck inventory count");
            var matcher = BasinOperatingBlockEntity.class.getDeclaredMethod("getMatchingRecipes");
            matcher.setAccessible(true);
            @SuppressWarnings("unchecked") var recipes = (List<Recipe<?>>) matcher.invoke(level.getBlockEntity(pos.above(2)));
            check(!recipes.isEmpty() && recipes.getFirst() instanceof CreateProcessing.WorkshopRecipe, "Press did not discover printing operation");
            check(BasinRecipe.match(basin, recipes.getFirst()), "Printing match failed");
            check(BasinRecipe.apply(basin, recipes.getFirst()), "Printing transaction failed");
            var produced = CreateSmokeInventory.outputs(basin);
            var printed = produced.stream().filter(stack -> stack.is(MahjongContent.BOX_ITEM)).findFirst().orElseThrow();
            check(MahjongSupplies.tileCount(MahjongSupplies.contents(printed)) == 144, "Printing lost tiles");
            check(MahjongSupplies.deck(printed).preset().equals(TileFacePreset.KANSAI), "Printing lost preset");
            check(printed.getHoverName().equals(box.getHoverName()), "Printing lost box name");
            check(produced.stream().anyMatch(stack -> ItemStack.matches(stack, plate)), "Plate not returned intact");
            check(!BasinRecipe.apply(basin, recipes.getFirst()), "Stale recipe consumed a second time");

            CreateSmokeInventory.clear(basin);
            level.setBlockAndUpdate(pos.above(2), AllBlocks.MECHANICAL_MIXER.getDefaultState());
            for (var input : List.of(printed, printed.copy(), new ItemStack(Items.BLUE_DYE)))
                check(CreateSmokeInventory.insert(basin, input.copy()), "Second identical box rejected");
            var dye = CreateProcessing.mixing(CreatePlatform.inventory(basin));
            check(dye != null, "Two-box dye operation missing");
            CreateSmokeInventory.blockOutputs(basin);
            var beforeBlocked = CreatePlatform.inventory(basin);
            check(!BasinRecipe.match(basin, dye.recipe()) && !BasinRecipe.apply(basin, dye.recipe()), "Blocked output consumed a batch");
            var afterBlocked = CreatePlatform.inventory(basin);
            check(beforeBlocked.size() == afterBlocked.size() && java.util.stream.IntStream.range(0, beforeBlocked.size())
                .allMatch(i -> ItemStack.matches(beforeBlocked.get(i), afterBlocked.get(i))), "Blocked transaction changed inputs or outputs");
            CreateSmokeInventory.clearOutputs(basin);
            check(BasinRecipe.apply(basin, dye.recipe()), "Two-box dye transaction failed");
            var dyed = CreateSmokeInventory.outputs(basin);
            check(dyed.size() == 2 && dyed.stream().allMatch(stack -> MahjongSupplies.deck(stack).back() == DyeColor.BLUE), "Two-box dye output");
            check(CreateProcessing.mixing(List.of(dyed.get(0), dyed.get(1), new ItemStack(Items.BLUE_DYE))) == null, "No-op dye starts");
            CreateSmokeInventory.clear(basin);
            for (var input : List.of(dyed.get(0), dyed.get(1), new ItemStack(MahjongContent.UNDO_DYE)))
                check(CreateSmokeInventory.insert(basin, input.copy()), "Undo inputs rejected");
            var undo = CreateProcessing.mixing(CreatePlatform.inventory(basin));
            check(undo != null && BasinRecipe.apply(basin, undo.recipe()), "Undo transaction failed");
            check(CreateSmokeInventory.outputs(basin).stream().allMatch(stack -> MahjongSupplies.deck(stack).back() == null), "Undo lost material backs");

            CreateSmokeInventory.clear(basin);
            var sticks = new ItemStack(MahjongContent.POINT_STICK, 32);
            sticks.setHoverName(Component.literal("Named sticks"));
            check(CreateSmokeInventory.insert(basin, sticks) && CreateSmokeInventory.insert(basin, new ItemStack(Items.BLUE_DYE)), "Stick insertion");
            var marking = CreateProcessing.mixing(CreatePlatform.inventory(basin));
            check(marking != null && BasinRecipe.apply(basin, marking.recipe()), "Marking failed");
            var marked = CreateSmokeInventory.outputs(basin).getFirst();
            check(marked.getCount() == 16 && MahjongComponents.points(marked) == 1000 && marked.getHoverName().equals(sticks.getHoverName()), "Marked stack data");
            CreateSmokeInventory.clear(basin);
            level.setBlockAndUpdate(pos.above(2), AllBlocks.MECHANICAL_PRESS.getDefaultState());
            for (var input : List.of(printed, marked, new ItemStack(MahjongContent.DICE, 2)))
                check(CreateSmokeInventory.insert(basin, input.copy()), "Packing insertion");
            var packing = CreateProcessing.pressing(CreatePlatform.inventory(basin));
            check(packing != null && BasinRecipe.apply(basin, packing.recipe()), "Packing failed");
            var packed = CreateSmokeInventory.outputs(basin).getFirst();
            check(MahjongSupplies.validBox(packed) && MahjongSupplies.contents(packed).get(MahjongSupplies.DICE_SLOT).getCount() == 2, "Packed inventory invalid");
            check(MahjongSupplies.pack(packed, List.of(new ItemStack(MahjongContent.DICE, 64))).isEmpty(), "Packing bypassed dice capacity");

            var five = MahjongSupplies.tile(new TileData(13, TileMaterial.GLASS, false), DyeColor.CYAN, 64);
            five.setHoverName(Component.literal("Named five"));
            var red = CreateProcessing.deploying(five, new ItemStack(MahjongContent.RED_DORA_DYE));
            check(red != null, "Deployer recipe missing");
            var result = red.rollResults().getFirst();
            check(result.getCount() == 1 && five.getCount() == 64 && MahjongSupplies.tile(result).red(), "Deployer converted a whole stack");
            check(result.getHoverName().equals(five.getHoverName()) && MahjongSupplies.back(result) == DyeColor.CYAN, "Deployer lost stack data");
            check(CreateProcessing.deploying(result, new ItemStack(MahjongContent.RED_DORA_DYE)) == null, "Already-red input matched");
            check(ItemStack.matches(CreateProcessing.deploying(result, new ItemStack(MahjongContent.UNDO_DYE)).rollResults().getFirst(), five.copyWithCount(1)), "Deployer undo changed other data");

            var assembled = new ItemStack(Items.CHEST);
            for (var reagent : List.of(Items.LEATHER, Items.IRON_NUGGET)) {
                var recipe = SequencedAssemblyRecipe.getRecipe(level, assembled, AllRecipeTypes.DEPLOYING.getType(), DeployerApplicationRecipe.class).orElseThrow();
                check(recipe.getIngredients().get(1).test(new ItemStack(reagent)), "Wrong assembly reagent");
                assembled = recipe.rollResults().getFirst();
            }
            var finishing = SequencedAssemblyRecipe.getRecipe(level, assembled, AllRecipeTypes.PRESSING.getType(), PressingRecipe.class).orElseThrow();
            check(MahjongSupplies.validBox(finishing.rollResults().getFirst()), "Assembly did not finish a box");
            for (String id : List.of("blanks_bone", "blank_point_sticks", "mahjong_dye", "red_dora_dye", "undo_dye")) {
                check(AllRecipeTypes.shouldIgnoreInAutomation(level.getRecipeManager().byKey(MahjongContent.id(id)).orElseThrow()), "Inefficient native recipe remains in automation");
                check(level.getRecipeManager().byKey(MahjongContent.id("create/" + id)).isPresent(), "Industrial recipe missing: " + id);
            }
            for (var material : TileMaterial.values()) {
                var output = level.getRecipeManager().byKey(MahjongContent.id("create/blanks_" + material.getSerializedName())).orElseThrow().getResultItem(level.registryAccess());
                check(output.getCount() == 24 && MahjongSupplies.tile(output).blank() && MahjongSupplies.tile(output).material() == material, "Sawing result lost material or yield");
            }
            var sawPos = pos.east(2);
            level.setBlockAndUpdate(sawPos, AllBlocks.MECHANICAL_SAW.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.saw.SawBlock.FACING, net.minecraft.core.Direction.UP));
            try {
                var saw = (com.simibubi.create.content.kinetics.saw.SawBlockEntity) level.getBlockEntity(sawPos);
                saw.initialize();
                var filter = com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(saw,
                    com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour.TYPE);
                filter.setFilter(new ItemStack(MahjongContent.POINT_STICK));
                saw.inventory.setStackInSlot(0, new ItemStack(Items.BONE_BLOCK, 64));
                var apply = com.simibubi.create.content.kinetics.saw.SawBlockEntity.class.getDeclaredMethod("applyRecipe");
                apply.setAccessible(true);
                apply.invoke(saw);
                check(CreateSmokeInventory.sawCount(saw.inventory) == 64 * 32, "Bulk sawing discarded output stacks");
            } finally { level.removeBlock(sawPos, false); }
        } finally {
            level.removeBlock(pos.above(2), false);
            level.removeBlock(pos, false);
        }
    }

    private static int total(List<ItemStack> stacks) { return stacks.stream().mapToInt(ItemStack::getCount).sum(); }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
