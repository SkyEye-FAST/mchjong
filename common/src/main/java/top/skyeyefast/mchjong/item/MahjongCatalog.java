package top.skyeyefast.mchjong.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.world.MahjongContent;

/** One ordered catalogue for both loaders and recipe viewers. Each call owns its stacks. */
public final class MahjongCatalog {
    private MahjongCatalog() {}

    public static List<ItemStack> entries() {
        var entries = new ArrayList<ItemStack>();
        entries.add(new ItemStack(MahjongContent.TABLE_ITEM));
        entries.add(new ItemStack(MahjongContent.AUTO_TABLE_ITEM));
        entries.add(new ItemStack(MahjongContent.STOOL_ITEM));
        entries.add(new ItemStack(MahjongContent.CLOTH_ITEM));
        entries.add(new ItemStack(MahjongContent.BOX_ITEM));
        entries.add(MahjongSupplies.completeBox(TileMaterial.BONE, DyeColor.BLUE));
        entries.add(new ItemStack(MahjongContent.TILE_ITEM));
        entries.add(new ItemStack(MahjongContent.MAHJONG_DYE));
        entries.add(new ItemStack(MahjongContent.CREATIVE_MAHJONG_DYE));
        entries.add(new ItemStack(MahjongContent.RED_DORA_DYE));
        MahjongComponents.DENOMINATIONS.stream().sorted().forEach(points -> {
            var stick = new ItemStack(MahjongContent.POINT_STICK);
            stick.set(MahjongComponents.POINTS, points);
            entries.add(stick);
        });
        return List.copyOf(entries);
    }
}
