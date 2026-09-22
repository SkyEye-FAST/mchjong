package top.skyeyefast.mchjong.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.engine.RedFives;
import top.skyeyefast.mchjong.world.MahjongContent;

/** One ordered catalogue for both loaders and recipe viewers. Each call owns its stacks. */
public final class MahjongCatalog {
    private MahjongCatalog() {}

    public static List<ItemStack> entries() {
        var entries = new ArrayList<ItemStack>();
        for (var wood : FurnitureWood.values()) {
            var table = new ItemStack(MahjongContent.TABLE_ITEM);
            MahjongComponents.wood(table, wood);
            entries.add(table);
        }
        for (var wood : FurnitureWood.values()) {
            var autoTable = new ItemStack(MahjongContent.AUTO_TABLE_ITEM);
            MahjongComponents.wood(autoTable, wood);
            entries.add(autoTable);
        }
        entries.add(new ItemStack(MahjongContent.STOOL_ITEM));
        for (var color : DyeColor.values()) {
            var cloth = new ItemStack(MahjongContent.CLOTH_ITEM);
            MahjongComponents.color(cloth, color);
            entries.add(cloth);
        }
        entries.add(new ItemStack(MahjongContent.BOX_ITEM));
        for (var reds : RedFives.values()) entries.add(MahjongSupplies.stockedBox(reds));
        for (var material : TileMaterial.values()) {
            entries.add(MahjongSupplies.tile(new TileData(-1, material, false), 1));
        }
        entries.add(new ItemStack(MahjongContent.DICE));
        entries.add(new ItemStack(MahjongContent.MAHJONG_DYE));
        entries.add(new ItemStack(MahjongContent.CREATIVE_MAHJONG_DYE));
        entries.add(new ItemStack(MahjongContent.RED_DORA_DYE));
        entries.add(new ItemStack(MahjongContent.UNDO_DYE));
        MahjongComponents.DENOMINATIONS.stream().sorted().forEach(points -> {
            var stick = new ItemStack(MahjongContent.POINT_STICK);
            MahjongComponents.points(stick, points);
            entries.add(stick);
        });
        return List.copyOf(entries);
    }
}
