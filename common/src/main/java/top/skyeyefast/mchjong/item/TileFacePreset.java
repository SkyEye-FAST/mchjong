package top.skyeyefast.mchjong.item;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

/** Persistent cosmetic identity; images are resolved on each client. */
public record TileFacePreset(Identifier id) {
    public static final TileFacePreset KANSAI = new TileFacePreset(Identifier.fromNamespaceAndPath("mchjong", "kansai"));
    public static final TileFacePreset KANTO = new TileFacePreset(Identifier.fromNamespaceAndPath("mchjong", "kanto"));
    public static final TileFacePreset HONG_KONG = new TileFacePreset(Identifier.fromNamespaceAndPath("mchjong", "hong_kong"));
    public static final TileFacePreset SICHUAN = new TileFacePreset(Identifier.fromNamespaceAndPath("mchjong", "sichuan"));
    public static final TileFacePreset TAIWAN = new TileFacePreset(Identifier.fromNamespaceAndPath("mchjong", "taiwan"));
    public static final TileFacePreset FUJIAN = new TileFacePreset(Identifier.fromNamespaceAndPath("mchjong", "fujian"));
    public static final java.util.List<TileFacePreset> BUILTINS = java.util.List.of(KANSAI, KANTO, SICHUAN, HONG_KONG, TAIWAN, FUJIAN);
    public static final Codec<TileFacePreset> CODEC = Identifier.CODEC.xmap(TileFacePreset::new, TileFacePreset::id);
    public TileFacePreset {
        java.util.Objects.requireNonNull(id);
        if (id.toString().length() > 128) throw new IllegalArgumentException("Preset ID exceeds 128 characters");
    }
    public String getSerializedName() { return id.toString(); }
    public String translationKey() { return "preset." + id.getNamespace() + "." + id.getPath().replace('/', '.'); }
}
