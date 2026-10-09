package top.skyeyefast.mchjong.item;

import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.engine.Tile;

/** A uniform 108-tile suited subset from one physical case. */
public record SichuanDeck(TileMaterial material, DyeColor back, TileFacePreset preset, Identifier backPreset) {
    public SichuanDeck {
        Objects.requireNonNull(material); Objects.requireNonNull(preset); Objects.requireNonNull(backPreset);
    }
    public List<Integer> tiles() { return Tile.sichuanSet(); }
    public TileData tile(int id) { return new TileData(Tile.kind(id), material, false); }
    public static SichuanDeck select(ItemStack box) {
        var appearance = DeckAdmission.inspect(box, DeckAdmission.requirements(false, top.skyeyefast.mchjong.engine.RedFives.NONE, true, false)).appearance();
        return appearance == null ? null : new SichuanDeck(appearance.material(), appearance.back(), appearance.face(), appearance.backPreset());
    }
}
