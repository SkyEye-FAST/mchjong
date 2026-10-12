package top.skyeyefast.mchjong.smoke;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Exercises generated NBT recipes and dye previews in the native 1.20.1 server. */
final class ColorRecipesSmoke {
    private ColorRecipesSmoke() {}

    static void verify(ServerPlayer player) {
        for (var color : DyeColor.values()) {
            var carpet = vanilla(color.getName() + "_carpet");
            for (var wood : FurnitureWood.values()) {
                String name = wood.getSerializedName();
                var slab = vanilla(name + "_slab"); var fence = vanilla(name + "_fence");
                var stools = craft(player, "mahjong_stool" + (wood == FurnitureWood.OAK ? "" : "_" + name) + "_" + color.getName(),
                    2, 3, List.of(carpet, carpet.copy(), slab, slab.copy(), fence, fence.copy()));
                check(stools.is(MahjongContent.STOOL_ITEM) && stools.getCount() == 2
                    && MahjongComponents.wood(stools) == wood && MahjongComponents.color(stools) == color,
                    "Colored stool recipe lost its appearance");
            }
            var cloth = craft(player, "table_cloth_" + color.getName(), 3, 1, List.of(carpet, carpet.copy(), carpet.copy()));
            check(cloth.is(MahjongContent.CLOTH_ITEM) && MahjongComponents.color(cloth) == color, "Colored cloth recipe failed");
            for (var item : List.of(MahjongContent.STOOL_ITEM, MahjongContent.CLOTH_ITEM)) {
                var target = new ItemStack(item, 2);
                target.setHoverName(net.minecraft.network.chat.Component.literal("Named fabric"));
                if (item == MahjongContent.STOOL_ITEM) MahjongComponents.wood(target, FurnitureWood.SPRUCE);
                MahjongComponents.color(target, color == DyeColor.WHITE ? DyeColor.BLACK : DyeColor.WHITE);
                var before = target.copy(); var dye = vanilla(color.getName() + "_dye");
                for (var ingredients : List.of(List.of(target, dye), List.of(dye, target))) {
                    var result = craft(player, "dye", 2, 1, ingredients);
                    var expected = target.copyWithCount(1); MahjongComponents.color(expected, color);
                    check(ItemStack.matches(expected, result) && ItemStack.matches(before, target), "Dye recipe changed other components or its input");
                }
            }
        }
    }

    private static ItemStack vanilla(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("minecraft", name)));
    }
    private static ItemStack craft(ServerPlayer player, String id, int width, int height, List<ItemStack> items) {
        var grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return false; }
        }, width, height);
        for (int i = 0; i < items.size(); i++) grid.setItem(i, items.get(i));
        var recipe = (CraftingRecipe) player.server.getRecipeManager().byKey(MahjongContent.id(id)).orElseThrow();
        check(recipe.matches(grid, player.serverLevel()), "Color recipe does not match " + id);
        return recipe.assemble(grid, player.serverLevel().registryAccess());
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
