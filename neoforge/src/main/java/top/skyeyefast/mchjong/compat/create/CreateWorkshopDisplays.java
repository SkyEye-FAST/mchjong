package top.skyeyefast.mchjong.compat.create;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Both viewers use examples produced by the exact same adapters as the machines. */
public final class CreateWorkshopDisplays {
    public record Display(ResourceLocation id, String operation, List<ItemStack> inputs, List<ItemStack> outputs, ItemStack machine) {
        public Component title() { return Component.translatable("browser.mchjong.create." + operation); }
    }
    private CreateWorkshopDisplays() {}

    public static List<Display> dynamic() {
        var displays = new ArrayList<Display>();
        for (var material : TileMaterial.values()) for (var preset : List.of(TileFacePreset.KANSAI, TileFacePreset.KANTO)) {
            var blank = MahjongSupplies.tile(new TileData(-1, material, false), 64);
            add(displays, "print/" + material.getSerializedName() + "/" + preset.id().getPath(), CreateProcessing.pressing(List.of(
                blank, blank.copy(), blank.copyWithCount(16), new ItemStack(MahjongContent.BOX_ITEM),
                new ItemStack(MahjongContent.MAHJONG_DYE), CreateCompat.plate(preset))));
        }
        var box = MahjongSupplies.completeBox(TileMaterial.BONE);
        for (var color : DyeColor.values()) {
            var reagent = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(color.getName() + "_dye")));
            add(displays, "backs/boxes/" + color.getName(), CreateProcessing.mixing(List.of(box, box.copy(), reagent)));
            add(displays, "backs/tiles/" + color.getName(), CreateProcessing.mixing(List.of(
                MahjongSupplies.tile(new TileData(-1, TileMaterial.BONE, false), 64),
                MahjongSupplies.tile(new TileData(-1, TileMaterial.GLASS, false), 64), reagent)));
        }
        var colored = MahjongSupplies.dye(box, DyeColor.BLUE);
        add(displays, "backs/undo", CreateProcessing.mixing(List.of(colored, colored.copy(), new ItemStack(MahjongContent.UNDO_DYE))));
        MahjongSupplies.markings().forEach((reagent, points) -> add(displays, "sticks/" + points,
            CreateProcessing.mixing(List.of(new ItemStack(MahjongContent.POINT_STICK, 16), new ItemStack(reagent)))));
        for (int face : new int[] {4, 13, 22}) for (boolean red : new boolean[] {false, true}) {
            var tile = MahjongSupplies.tile(new TileData(face, TileMaterial.BONE, red), 1);
            var dye = new ItemStack(red ? MahjongContent.UNDO_DYE : MahjongContent.RED_DORA_DYE);
            var recipe = CreateProcessing.deploying(tile, dye);
            displays.add(new Display(MahjongContent.id("/create/display/five/" + face + "/" + red), "deploying",
                List.of(tile, dye), recipe.getRollableResultsAsItemStacks(), new ItemStack(AllBlocks.DEPLOYER.get())));
        }
        var marked = MahjongSupplies.markSticks(List.of(new ItemStack(MahjongContent.POINT_STICK, 16)), new ItemStack(Items.BLUE_DYE), 16);
        add(displays, "pack", CreateProcessing.pressing(List.of(box, marked, new ItemStack(MahjongContent.DICE, 2))));
        return List.copyOf(displays);
    }

    private static void add(List<Display> displays, String id, CreateProcessing.Batch batch) {
        if (batch == null) throw new IllegalStateException("Invalid workshop example: " + id);
        boolean mixing = batch.operation().equals("dye_backs") || batch.operation().equals("mark_sticks");
        displays.add(new Display(MahjongContent.id("/create/display/" + id), batch.operation(), batch.inputs(), batch.outputs(),
            new ItemStack(mixing ? AllBlocks.MECHANICAL_MIXER.get() : AllBlocks.MECHANICAL_PRESS.get())));
    }

    /** JEI already receives these data recipes from Create; EMI also exposes them without another addon. */
    public static List<Display> staticRecipes(Level level) {
        var displays = new ArrayList<Display>();
        for (var holder : level.getRecipeManager().getRecipes()) {
            if (!holder.id().getNamespace().equals("mchjong") || !holder.id().getPath().startsWith("create/")) continue;
            if (holder.value() instanceof com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe assembly) {
                var ingredients = new ArrayList<net.minecraft.world.item.crafting.Ingredient>();
                ingredients.add(assembly.getIngredient());
                for (var step : assembly.getSequence())
                    ingredients.addAll(step.getRecipe().getIngredients().stream().skip(1).toList());
                if (ingredients.stream().anyMatch(ingredient -> ingredient.getItems().length == 0)) continue;
                var inputs = new ArrayList<ItemStack>();
                for (int i = 0; i < ingredients.size(); i++)
                    inputs.add(ingredients.get(i).getItems()[0].copyWithCount(i == 0 ? 1 : assembly.getLoops()));
                displays.add(new Display(holder.id(), "sequenced_assembly", List.copyOf(inputs),
                    assembly.resultPool.stream().map(result -> result.getStack().copy()).toList(), new ItemStack(AllBlocks.DEPLOYER.get())));
                continue;
            }
            if (!(holder.value() instanceof ProcessingRecipe<?, ?> recipe)) continue;
            if (recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.getItems().length == 0)) continue;
            var operation = recipe.getTypeInfo().getId().getPath();
            var machine = operation.equals("cutting") ? AllBlocks.MECHANICAL_SAW.get() : AllBlocks.MECHANICAL_MIXER.get();
            var inputs = recipe.getIngredients().stream().map(ingredient -> ingredient.getItems()[0].copy()).toList();
            displays.add(new Display(holder.id(), operation, inputs, recipe.getRollableResultsAsItemStacks(), new ItemStack(machine)));
        }
        return List.copyOf(displays);
    }
}
