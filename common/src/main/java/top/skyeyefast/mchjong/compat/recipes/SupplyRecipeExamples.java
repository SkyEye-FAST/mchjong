package top.skyeyefast.mchjong.compat.recipes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.recipe.SupplyCraftingRecipe;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Finite, executable examples, derived from recipes present in the current datapack.
 * Alternatives are admitted only when the actual recipe produces the same complete output. */
public final class SupplyRecipeExamples {
    private SupplyRecipeExamples() {}

    public static List<SupplyRecipeExample> create(Level level) {
        var examples = new ArrayList<SupplyRecipeExample>();
        var recipes = level.getRecipeManager().getRecipes().stream()
            .sorted(Comparator.comparing(holder -> holder.getId().toString())).toList();
        for (var holder : recipes) {
            if (holder instanceof SupplyCraftingRecipe recipe) {
                switch (recipe.operation()) {
                    case RED_FIVE, UNDO_RED_FIVE -> {
                        boolean red = recipe.operation() == SupplyCraftingRecipe.Operation.RED_FIVE;
                        for (var material : TileMaterial.values()) for (int face : new int[]{4, 13, 22})
                            add(examples, level, holder, material.getSerializedName() + "/" + face,
                                List.of(MahjongSupplies.tile(new TileData(face, material, !red), DyeColor.BLUE, 1),
                                    new ItemStack(red ? MahjongContent.RED_DORA_DYE : MahjongContent.UNDO_DYE)));
                    }
                    case MARK_STICK -> SupplyCraftingRecipe.markings().entrySet().stream()
                        .sorted(java.util.Map.Entry.comparingByValue()).forEach(mark -> {
                            var input = new ArrayList<ItemStack>();
                            for (int i = 0; i < 8; i++) input.add(new ItemStack(MahjongContent.POINT_STICK));
                            input.add(new ItemStack(mark.getKey()));
                            add(examples, level, holder, mark.getValue() + "/8", input);
                        });
                    case UPGRADE_TABLE -> {
                        for (var wood : FurnitureWood.values()) {
                            var input = SupplyCraftingRecipe.upgradePattern().stream().map(ItemStack::new).toList();
                            MahjongComponents.wood(input.get(4), wood);
                            add(examples, level, holder, wood.getSerializedName(), input);
                        }
                    }
                    case DYE -> {
                        for (var color : DyeColor.values()) {
                            var targets = new ArrayList<ItemStack>();
                            targets.add(new ItemStack(MahjongContent.CLOTH_ITEM));
                            for (var wood : FurnitureWood.values()) {
                                var stool = new ItemStack(MahjongContent.STOOL_ITEM);
                                MahjongComponents.wood(stool, wood);
                                targets.add(stool);
                            }
                            for (var material : TileMaterial.values()) {
                                for (int face : new int[]{-1, 4, TileData.FIRST_FLOWER + TileData.FLOWER_COUNT - 1})
                                    targets.add(MahjongSupplies.tile(new TileData(face, material, face == 4), face == -1 ? null : DyeColor.BLUE, 1));
                                targets.add(MahjongSupplies.completeBox(material, DyeColor.BLUE));
                                targets.add(blanks(material, DyeColor.BLUE, true));
                            }
                            for (int i = 0; i < targets.size(); i++)
                                add(examples, level, holder, color.getName() + "/" + i,
                                    List.of(targets.get(i), new ItemStack(DyeItem.byColor(color))));
                        }
                    }
                }
            }
        }
        return List.copyOf(examples);
    }

    public static ItemStack blanks(TileMaterial material, DyeColor color, boolean spares) {
        var items = net.minecraft.core.NonNullList.withSize(MahjongSupplies.BOX_SLOTS, ItemStack.EMPTY);
        var data = new TileData(-1, material, false);
        items.set(0, MahjongSupplies.tile(data, color, 64));
        items.set(1, MahjongSupplies.tile(data, color, 64));
        items.set(2, MahjongSupplies.tile(data, color, spares ? 16 : 8));
        if (spares) {
            var stick = new ItemStack(MahjongContent.POINT_STICK, 4);
            MahjongComponents.points(stick, 1000);
            items.set(MahjongSupplies.TILE_SLOTS, stick);
        }
        var box = new ItemStack(MahjongContent.BOX_ITEM);
        MahjongSupplies.setContents(box, items);
        return box;
    }

    private static void add(List<SupplyRecipeExample> examples, Level level, Recipe<?> source,
                            String variant, List<ItemStack> ingredients) {
        var input = new ArrayList<>(ingredients);
        input.addAll(Collections.nCopies(9 - input.size(), ItemStack.EMPTY));
        var id = MahjongContent.id("/supplies/" + source.getId().getNamespace() + "/" + source.getId().getPath() + "/" + variant);
        var candidate = new SupplyRecipeExample(id, source, List.copyOf(input), ItemStack.EMPTY, List.of(input.get(0)));
        var output = candidate.assemble(level);
        // A datapack may restrict a vanilla ingredient. Only publish actual matches.
        if (output.isEmpty()) return;
        var alternatives = new ArrayList<ItemStack>();
        if (source instanceof SupplyCraftingRecipe recipe && recipe.operation() == SupplyCraftingRecipe.Operation.DYE) {
            for (var color : DyeColor.values()) {
                var variantInput = new ArrayList<>(input);
                var variantStack = MahjongSupplies.dye(input.get(0), color);
                variantInput.set(0, variantStack);
                var variantExample = new SupplyRecipeExample(id, source, variantInput, output, List.of(variantStack));
                var result = variantExample.assemble(level);
                if (ItemStack.matches(output, result)) alternatives.add(variantStack);
            }
        } else alternatives.add(input.get(0));
        examples.add(new SupplyRecipeExample(id, source, candidate.input(), output, List.copyOf(alternatives)));
    }
}
