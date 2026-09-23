package top.skyeyefast.mchjong.item;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;

/** Persistent cosmetic identity; resource-pack definitions belong exclusively to the client. */
public record TileFacePreset(Identifier id) {
    public static final TileFacePreset KANSAI = new TileFacePreset(Identifier.fromNamespaceAndPath("mchjong", "kansai"));
    public static final TileFacePreset KANTO = new TileFacePreset(Identifier.fromNamespaceAndPath("mchjong", "kanto"));
    public static final Codec<TileFacePreset> CODEC = Identifier.CODEC.xmap(TileFacePreset::new, TileFacePreset::id);
    public TileFacePreset {
        java.util.Objects.requireNonNull(id);
        if (id.toString().length() > 128) throw new IllegalArgumentException("Preset ID exceeds 128 characters");
    }
    public String getSerializedName() { return id.toString(); }
    public String translationKey() { return "preset." + id.getNamespace() + "." + id.getPath().replace('/', '.'); }
}
