package top.skyeyefast.mchjong.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.engine.FlowerTile;
import top.skyeyefast.mchjong.engine.Tile;

/** One uniform standard MCR subset from one physical case; selection never modifies inventory. */
public record McrDeck(TileMaterial material, DyeColor back, TileFacePreset preset, Identifier backPreset) {
    public McrDeck {
        Objects.requireNonNull(material);
        Objects.requireNonNull(preset);
        Objects.requireNonNull(backPreset);
    }

    public List<Integer> tiles() { return Tile.mcrSet(); }

    /** Physical flowers are independent identities, not ordinary kinds 34 through 41. */
    public TileData tile(int physicalId) {
        if (Tile.red(physicalId)) throw new IllegalArgumentException("Standard MCR stock has no red fives");
        FlowerTile flower = FlowerTile.of(physicalId);
        int face = flower == null ? Tile.kind(physicalId) : switch (flower) {
            case SPRING -> 34;
            case SUMMER -> 35;
            case AUTUMN -> 36;
            case WINTER -> 37;
            case PLUM -> 38;
            case ORCHID -> 39;
            case BAMBOO -> 40;
            case CHRYSANTHEMUM -> 41;
        };
        return new TileData(face, material, false);
    }

    /** Extra tiles remain in the case. Red markings cannot replace missing ordinary fives. */
    public static McrDeck select(ItemStack box) {
        if (!MahjongSupplies.validBox(box)) return null;
        var stocks = new LinkedHashMap<McrDeck, int[]>();
        var items = MahjongSupplies.contents(box);
        for (int slot = 0; slot < MahjongSupplies.TILE_SLOTS; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            TileData data = MahjongSupplies.tile(stack);
            if (data.blank() || data.red()) continue;
            var appearance = new McrDeck(data.material(), MahjongSupplies.back(stack),
                MahjongSupplies.facePreset(stack), MahjongSupplies.backPreset(stack));
            int[] stock = stocks.computeIfAbsent(appearance, ignored -> new int[TileData.FIRST_FLOWER + TileData.FLOWER_COUNT]);
            stock[data.face()] += stack.getCount();
        }
        for (var stock : stocks.entrySet()) {
            boolean complete = true;
            for (int face = 0; face < stock.getValue().length && complete; face++)
                complete = stock.getValue()[face] >= (face < TileData.FIRST_FLOWER ? 4 : 1);
            if (complete) return stock.getKey();
        }
        return null;
    }
}
