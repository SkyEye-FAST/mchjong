package top.skyeyefast.mchjong.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.engine.FlowerTile;
import top.skyeyefast.mchjong.engine.Tile;

/** One uniform 136/144-tile Taiwan subset from one physical case; selection never modifies inventory. */
public record TaiwanDeck(boolean flowers, TileMaterial material, DyeColor back, TileFacePreset preset, Identifier backPreset) {
    public TaiwanDeck {
        Objects.requireNonNull(material);
        Objects.requireNonNull(preset);
        Objects.requireNonNull(backPreset);
    }

    public List<Integer> tiles() { return flowers ? Tile.standard144Set() : Tile.set(false, top.skyeyefast.mchjong.engine.RedFives.NONE); }

    /** Physical flowers are independent identities, not ordinary kinds 34 through 41. */
    public TileData tile(int physicalId) {
        if (Tile.red(physicalId)) throw new IllegalArgumentException("Taiwan stock has no red fives");
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
    public static TaiwanDeck select(ItemStack box, boolean flowers) {
        if (!MahjongSupplies.validBox(box)) return null;
        var stocks = new LinkedHashMap<TaiwanDeck, int[]>();
        var items = MahjongSupplies.contents(box);
        for (int slot = 0; slot < MahjongSupplies.TILE_SLOTS; slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) continue;
            TileData data = MahjongSupplies.tile(stack);
            if (data.blank() || data.red()) continue;
            var appearance = new TaiwanDeck(flowers, data.material(), MahjongSupplies.back(stack),
                MahjongSupplies.facePreset(stack), MahjongSupplies.backPreset(stack));
            int[] stock = stocks.computeIfAbsent(appearance, ignored -> new int[TileData.FIRST_FLOWER + TileData.FLOWER_COUNT]);
            stock[data.face()] += stack.getCount();
        }
        for (var stock : stocks.entrySet()) {
            boolean complete = true;
            for (int face = 0; face < (flowers ? stock.getValue().length : TileData.FIRST_FLOWER) && complete; face++)
                complete = stock.getValue()[face] >= (face < TileData.FIRST_FLOWER ? 4 : 1);
            if (complete) return stock.getKey();
        }
        return null;
    }
}
