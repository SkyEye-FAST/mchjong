package top.skyeyefast.mchjong.recipe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Only component-dependent operations use code. Static recipes remain vanilla JSON. */
public final class SupplyCraftingRecipe extends CustomRecipe {
    public enum Operation { DYE, RED_FIVE, UNDO_RED_FIVE, MARK_STICK, UPGRADE_TABLE }
    private final Operation operation;

    public Operation operation() { return operation; }

    public static java.util.Map<net.minecraft.world.item.Item, Integer> markings() {
        return MahjongSupplies.markings();
    }

    public static List<net.minecraft.world.item.Item> upgradePattern() {
        return List.of(Items.IRON_INGOT, Items.REDSTONE, Items.IRON_INGOT,
            Items.REDSTONE, MahjongContent.TABLE_ITEM, Items.REDSTONE,
            Items.COPPER_INGOT, Items.HOPPER, Items.COPPER_INGOT);
    }

    public SupplyCraftingRecipe(net.minecraft.resources.ResourceLocation id, CraftingBookCategory category, Operation operation) {
        super(id, category);
        this.operation = operation;
    }

    @Override public boolean matches(CraftingContainer input, Level level) { return !result(input).isEmpty(); }
    @Override public ItemStack assemble(CraftingContainer input, RegistryAccess registries) { return result(input); }
    @Override public boolean canCraftInDimensions(int width, int height) {
        return operation == Operation.UPGRADE_TABLE ? width >= 3 && height >= 3 : width * height >= 2;
    }
    @Override public RecipeSerializer<?> getSerializer() { return MahjongRecipes.CRAFTING.get(operation); }

    private ItemStack result(CraftingContainer input) {
        if (operation == Operation.UPGRADE_TABLE) return upgrade(input);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < input.getContainerSize(); i++) if (!input.getItem(i).isEmpty()) items.add(input.getItem(i));
        if (operation == Operation.MARK_STICK) return markSticks(items);
        if (items.size() != 2) return ItemStack.EMPTY;
        ItemStack first = items.get(0), second = items.get(items.size() - 1);
        for (int order = 0; order < 2; order++) {
            ItemStack target = order == 0 ? first : second;
            ItemStack reagent = order == 0 ? second : first;
            switch (operation) {
                case RED_FIVE, UNDO_RED_FIVE -> {
                    boolean red = operation == Operation.RED_FIVE;
                    if (reagent.is(red ? MahjongContent.RED_DORA_DYE : MahjongContent.UNDO_DYE))
                        return MahjongSupplies.redFive(target, reagent);
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
        var blanks = new ArrayList<ItemStack>();
        ItemStack reagent = ItemStack.EMPTY;
        for (ItemStack stack : items) {
            if (stack.is(MahjongContent.POINT_STICK)) {
                blanks.add(stack.copyWithCount(1));
            } else {
                if (!reagent.isEmpty()) return ItemStack.EMPTY;
                reagent = stack;
            }
        }
        return MahjongSupplies.markSticks(blanks, reagent, 8);
    }

    private ItemStack upgrade(CraftingContainer input) {
        if (input.getWidth() != 3 || input.getHeight() != 3) return ItemStack.EMPTY;
        var required = upgradePattern();
        for (int i = 0; i < 9; i++) if (!input.getItem(i).is(required.get(i))) return ItemStack.EMPTY;
        ItemStack table = input.getItem(4);
        // Furniture drops its removable contents separately; loaded creative stacks are not consumed.
        if (MahjongSupplies.hasStorage(table)) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(MahjongContent.AUTO_TABLE_ITEM);
        MahjongComponents.wood(result, MahjongComponents.wood(table));
        if (table.hasCustomHoverName()) result.setHoverName(table.getHoverName());
        return result;
    }
}
