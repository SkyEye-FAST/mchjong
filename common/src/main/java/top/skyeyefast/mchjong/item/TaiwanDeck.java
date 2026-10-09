package top.skyeyefast.mchjong.item;

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
        var appearance = DeckAdmission.inspect(box, DeckAdmission.requirements(false, top.skyeyefast.mchjong.engine.RedFives.NONE, false, flowers)).appearance();
        return appearance == null ? null : new TaiwanDeck(flowers, appearance.material(), appearance.back(), appearance.face(), appearance.backPreset());
    }
}
