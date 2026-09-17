package top.skyeyefast.mchjong.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import top.skyeyefast.mchjong.engine.Tile;

public class MahjongSupplyItem extends Item {
    public MahjongSupplyItem(Properties properties) { super(properties); }

    @Override public Component getName(ItemStack stack) {
        Integer points = stack.get(MahjongComponents.POINTS);
        if (points != null && points > 0) return Component.translatable("item.mchjong.point_stick_value", points);
        TileData tile = stack.get(MahjongComponents.TILE);
        return tile != null && tile.flower() ? Component.translatable(tile.flowerKey()) : super.getName(stack);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        TileData tile = stack.get(MahjongComponents.TILE);
        if (tile != null) {
            lines.add(Component.translatable(tile.material() == TileMaterial.WOOD ? "material.mchjong.wood" : "block.minecraft." + tile.material().source()));
            lines.add(tile.blank() ? Component.translatable("item.mchjong.blank")
                : tile.flower() ? Component.translatable("item.mchjong.flower")
                : Component.literal((tile.red() ? "0" + "mps".charAt(tile.face() / 9) : Tile.notation(tile.face()))));
        }
        Integer points = stack.get(MahjongComponents.POINTS);
        if (points != null) {
            lines.add(points == 0 ? Component.translatable("item.mchjong.unmarked") : Component.translatable("item.mchjong.denomination", points));
        }
        if (stack.has(net.minecraft.core.component.DataComponents.BASE_COLOR))
            lines.add(Component.translatable("color.minecraft." + MahjongSupplies.color(stack).getName()));
    }
}
