package top.skyeyefast.mchjong.item;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Built-in face designs, independent of tile material and back color. */
public enum TileFacePreset implements StringRepresentable {
    KANSAI, KANTO;

    public static final Codec<TileFacePreset> CODEC = StringRepresentable.fromEnum(TileFacePreset::values);
    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String translationKey() { return "preset.mchjong." + getSerializedName(); }
}
