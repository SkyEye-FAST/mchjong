package top.skyeyefast.mchjong.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.engine.Tile;

/** A uniform 108-tile suited subset from one physical case. */
public record SichuanDeck(TileMaterial material, DyeColor back, TileFacePreset preset, ResourceLocation backPreset) {
    public SichuanDeck {
        Objects.requireNonNull(material); Objects.requireNonNull(preset); Objects.requireNonNull(backPreset);
    }
    public List<Integer> tiles() { return Tile.sichuanSet(); }
    public static SichuanDeck select(ItemStack box) {
        if (!MahjongSupplies.validBox(box)) return null;
        var stocks = new LinkedHashMap<SichuanDeck, int[]>();
        var contents = MahjongSupplies.contents(box);
        for (int slot = 0; slot < MahjongSupplies.TILE_SLOTS; slot++) {
            var stack = contents.get(slot);
            if (stack.isEmpty()) continue;
            var data = MahjongSupplies.tile(stack);
            if (data.blank() || data.red() || data.face() >= 27) continue;
            var appearance = new SichuanDeck(data.material(), MahjongSupplies.back(stack),
                MahjongSupplies.facePreset(stack), MahjongSupplies.backPreset(stack));
            stocks.computeIfAbsent(appearance, ignored -> new int[27])[data.face()] += stack.getCount();
        }
        for (var stock : stocks.entrySet()) {
            boolean complete = true;
            for (int count : stock.getValue()) if (count < 4) complete = false;
            if (complete) return stock.getKey();
        }
        return null;
    }
}
