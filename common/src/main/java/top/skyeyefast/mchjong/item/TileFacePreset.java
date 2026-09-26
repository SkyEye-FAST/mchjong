package top.skyeyefast.mchjong.item;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

/** Persistent cosmetic identity; images are resolved on each client. */
public record TileFacePreset(ResourceLocation id) {
    public static final TileFacePreset KANSAI = new TileFacePreset(new ResourceLocation("mchjong", "kansai"));
    public static final TileFacePreset KANTO = new TileFacePreset(new ResourceLocation("mchjong", "kanto"));
    public static final Codec<TileFacePreset> CODEC = ResourceLocation.CODEC.xmap(TileFacePreset::new, TileFacePreset::id);
    public TileFacePreset {
        java.util.Objects.requireNonNull(id);
        if (id.toString().length() > 128) throw new IllegalArgumentException("Preset ID exceeds 128 characters");
    }
    public String getSerializedName() { return id.toString(); }
    public String translationKey() { return "preset." + id.getNamespace() + "." + id.getPath().replace('/', '.'); }
}
