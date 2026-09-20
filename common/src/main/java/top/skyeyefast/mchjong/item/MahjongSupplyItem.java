package top.skyeyefast.mchjong.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class MahjongSupplyItem extends Item {
    public MahjongSupplyItem(Properties properties) { super(properties); }

    /** Dedicated servers use names; the client-only presentation mixin applies the local preference. */
    private static Component tileLabel(TileData tile, TileFacePreset preset) { return tile.label(false, preset); }

    @Override public Component getName(ItemStack stack) {
        Integer points = stack.get(MahjongComponents.POINTS);
        if (points != null && points == -10000) return Component.translatable("item.mchjong.bust_stick");
        if (points != null && points > 0) return Component.translatable("item.mchjong.point_stick_value", points);
        return super.getName(stack);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        TileData tile = stack.get(MahjongComponents.TILE);
        if (tile != null) {
            lines.add(Component.translatable(tile.material() == TileMaterial.WOOD ? "material.mchjong.wood" : "block.minecraft." + tile.material().source()));
            lines.add(tileLabel(tile, MahjongSupplies.facePreset(stack)));
        }
        Integer points = stack.get(MahjongComponents.POINTS);
        if (points != null) {
            lines.add(points == 0 ? Component.translatable("item.mchjong.unmarked") : Component.translatable("item.mchjong.denomination", points));
        }
        if (stack.has(net.minecraft.core.component.DataComponents.BASE_COLOR))
            lines.add(Component.translatable("color.minecraft." + MahjongSupplies.color(stack).getName()));
    }
}
