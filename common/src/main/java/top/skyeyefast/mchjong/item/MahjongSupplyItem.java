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
        int points = MahjongComponents.points(stack);
        if (stack.is(top.skyeyefast.mchjong.world.MahjongContent.POINT_STICK) && points == -10000) return Component.translatable("item.mchjong.bust_stick");
        if (stack.is(top.skyeyefast.mchjong.world.MahjongContent.POINT_STICK) && points > 0) return Component.translatable("item.mchjong.point_stick_value", points);
        if (stack.is(top.skyeyefast.mchjong.world.MahjongContent.CLOTH_ITEM)) {
            return Component.translatable("item.mchjong.table_cloth." + MahjongSupplies.color(stack).getName());
        }
        return super.getName(stack);
    }

    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level, List<Component> lines, TooltipFlag flag) {
        TileData tile = MahjongSupplies.tile(stack);
        if (stack.is(top.skyeyefast.mchjong.world.MahjongContent.TILE_ITEM)) {
            lines.add(Component.translatable(tile.material() == TileMaterial.WOOD ? "material.mchjong.wood" : "block.minecraft." + tile.material().source()));
            lines.add(tileLabel(tile, MahjongSupplies.facePreset(stack)));
        }
        int points = MahjongComponents.points(stack);
        if (stack.is(top.skyeyefast.mchjong.world.MahjongContent.POINT_STICK)) {
            lines.add(points == 0 ? Component.translatable("item.mchjong.unmarked") : Component.translatable("item.mchjong.denomination", points));
        }
        if (MahjongSupplies.back(stack) != null && !stack.is(top.skyeyefast.mchjong.world.MahjongContent.CLOTH_ITEM))
            lines.add(Component.translatable("color.minecraft." + MahjongSupplies.color(stack).getName()));
    }
}
