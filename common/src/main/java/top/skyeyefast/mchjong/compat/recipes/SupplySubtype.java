package top.skyeyefast.mchjong.compat.recipes;

import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A private, immutable stack copy gives both viewers the game's NBT equality.
 * Names are cosmetic; material, color, faces, red flags, points and complete contents are not. */
public final class SupplySubtype {
    private final ItemStack stack;
    private final int hash;

    private SupplySubtype(ItemStack input) {
        stack = input.copyWithCount(1);
        stack.resetHoverName();
        hash = java.util.Objects.hash(stack.getItem(), stack.getTag());
    }

    public static SupplySubtype of(ItemStack stack) { return new SupplySubtype(stack); }

    public static List<Item> items() {
        return List.of(MahjongContent.TABLE_ITEM, MahjongContent.AUTO_TABLE_ITEM, MahjongContent.STOOL_ITEM,
            MahjongContent.CLOTH_ITEM, MahjongContent.TILE_ITEM, MahjongContent.POINT_STICK, MahjongContent.BOX_ITEM,
            MahjongContent.MAHJONG_DYE, MahjongContent.CREATIVE_MAHJONG_DYE, MahjongContent.RED_DORA_DYE,
            MahjongContent.UNDO_DYE, MahjongContent.DICE);
    }

    @Override public boolean equals(Object other) {
        return other instanceof SupplySubtype key && ItemStack.isSameItemSameTags(stack, key.stack);
    }

    @Override public int hashCode() { return hash; }
}
