package top.skyeyefast.mchjong.item;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Kanto is reserved explicitly and cannot be applied until its artwork is supplied. */
public enum TileFacePreset implements StringRepresentable {
    KANSAI, KANTO;

    public static final Codec<TileFacePreset> CODEC = StringRepresentable.fromEnum(TileFacePreset::values);
    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String translationKey() { return "preset.mchjong." + getSerializedName(); }
    public boolean available() { return this == KANSAI; }
}
