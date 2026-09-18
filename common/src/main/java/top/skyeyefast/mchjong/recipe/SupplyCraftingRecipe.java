package top.skyeyefast.mchjong.recipe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Only component-dependent operations use code. Static recipes remain vanilla JSON. */
public final class SupplyCraftingRecipe extends CustomRecipe {
    public enum Operation { DYE, RED_FIVE, MARK_STICK, UPGRADE_TABLE }
    private final Operation operation;

    public Operation operation() { return operation; }

    public static java.util.Map<net.minecraft.world.item.Item, Integer> markings() {
        return java.util.Map.of(Items.BLACK_DYE, 100, Items.REDSTONE, 1000,
            Items.LAPIS_LAZULI, 5000, Items.GOLD_NUGGET, 10000);
    }

    public static List<net.minecraft.world.item.Item> upgradePattern() {
        return List.of(Items.IRON_INGOT, Items.REDSTONE, Items.IRON_INGOT,
            Items.REDSTONE, MahjongContent.TABLE_ITEM, Items.REDSTONE,
            Items.COPPER_INGOT, Items.HOPPER, Items.COPPER_INGOT);
    }

    public SupplyCraftingRecipe(CraftingBookCategory category, Operation operation) {
        super(category);
        this.operation = operation;
    }

    @Override public boolean matches(CraftingInput input, Level level) { return !result(input).isEmpty(); }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return result(input); }
    @Override public boolean canCraftInDimensions(int width, int height) {
        return operation == Operation.UPGRADE_TABLE ? width >= 3 && height >= 3 : width * height >= 2;
    }
    @Override public RecipeSerializer<?> getSerializer() { return MahjongRecipes.CRAFTING.get(operation); }

    private ItemStack result(CraftingInput input) {
        if (operation == Operation.UPGRADE_TABLE) return upgrade(input);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) if (!input.getItem(i).isEmpty()) items.add(input.getItem(i));
        if (operation == Operation.MARK_STICK) return markSticks(items);
        if (items.size() != 2) return ItemStack.EMPTY;
        ItemStack first = items.getFirst(), second = items.getLast();
        for (int order = 0; order < 2; order++) {
            ItemStack target = order == 0 ? first : second;
            ItemStack reagent = order == 0 ? second : first;
            switch (operation) {
                case RED_FIVE -> {
                    var data = MahjongSupplies.tile(target);
                    if (target.is(MahjongContent.TILE_ITEM) && MahjongSupplies.storable(target)
                        && data.valid() && !data.red() && (data.face() == 4 || data.face() == 13 || data.face() == 22)
                        && reagent.is(MahjongContent.RED_DORA_DYE)) {
                        var result = target.copyWithCount(1);
                        result.set(MahjongComponents.TILE, data.engraved(data.face(), true));
                        return result;
                    }
                }
                case DYE -> {
                    if ((target.is(MahjongContent.TILE_ITEM) || target.is(MahjongContent.CLOTH_ITEM)
                        || target.is(MahjongContent.BOX_ITEM) || target.is(MahjongContent.STOOL_ITEM))
                        && reagent.getItem() instanceof DyeItem dye)
                        return MahjongSupplies.dye(target, dye.getDyeColor());
                }
                default -> throw new IllegalStateException("Unexpected operation");
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack markSticks(List<ItemStack> items) {
        int blanks = 0, points = 0;
        for (ItemStack stack : items) {
            if (stack.is(MahjongContent.POINT_STICK)) {
                if (stack.getOrDefault(MahjongComponents.POINTS, 0) != 0 || !MahjongSupplies.storable(stack)) return ItemStack.EMPTY;
                blanks++;
            } else {
                if (points != 0) return ItemStack.EMPTY;
                points = markings().getOrDefault(stack.getItem(), 0);
                if (points == 0) return ItemStack.EMPTY;
            }
        }
        if (blanks < 1 || blanks > 8 || points == 0) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(MahjongContent.POINT_STICK, blanks);
        result.set(MahjongComponents.POINTS, points);
        return result;
    }

    private ItemStack upgrade(CraftingInput input) {
        if (input.width() != 3 || input.height() != 3) return ItemStack.EMPTY;
        var required = upgradePattern();
        for (int i = 0; i < 9; i++) if (!input.getItem(i).is(required.get(i))) return ItemStack.EMPTY;
        ItemStack table = input.getItem(4);
        // Furniture drops its removable contents separately; loaded creative stacks are not consumed.
        if (table.has(net.minecraft.core.component.DataComponents.CONTAINER)
            || table.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(MahjongContent.AUTO_TABLE_ITEM);
        result.set(MahjongComponents.WOOD, table.getOrDefault(MahjongComponents.WOOD, FurnitureWood.OAK));
        var name = table.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (name != null) result.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        return result;
    }
}
