package top.skyeyefast.mchjong.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;

/** A blank is face -1, not a second item. Red is meaningful only on a five. */
public record TileData(int face, TileMaterial material, boolean red) {
    public static final int FIRST_FLOWER = 34;
    public static final int FLOWER_COUNT = 8;
    public static final java.util.List<String> FLOWERS = java.util.List.of(
        "plum", "orchid", "chrysanthemum", "bamboo", "spring", "summer", "autumn", "winter");
    public static final TileData BLANK = new TileData(-1, TileMaterial.BONE, false);
    public static final Codec<TileData> CODEC = RecordCodecBuilder.<TileData>create(instance -> instance.group(
        Codec.intRange(-1, FIRST_FLOWER + FLOWER_COUNT - 1).fieldOf("face").forGetter(TileData::face),
        TileMaterial.CODEC.fieldOf("material").forGetter(TileData::material),
        Codec.BOOL.optionalFieldOf("red", false).forGetter(TileData::red)
    ).apply(instance, TileData::new)).validate(data -> data.valid()
        ? DataResult.success(data) : DataResult.error(() -> "Only a numbered five may be red"));

    public TileData { Objects.requireNonNull(material); }
    public boolean blank() { return face == -1; }
    public boolean flower() { return face >= FIRST_FLOWER && face < FIRST_FLOWER + FLOWER_COUNT; }
    public String flowerKey() {
        if (!flower()) throw new IllegalStateException("Not a flower tile");
        return "flower.mchjong." + FLOWERS.get(face - FIRST_FLOWER);
    }
    public boolean valid() { return face >= -1 && face < FIRST_FLOWER + FLOWER_COUNT && (!red || face == 4 || face == 13 || face == 22); }
    public TileData engraved(int face, boolean red) { return new TileData(face, material, red); }
}
