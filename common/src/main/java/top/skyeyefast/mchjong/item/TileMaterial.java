package top.skyeyefast.mchjong.item;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum TileMaterial implements StringRepresentable {
    OAK(0xffb38b59, "oak_planks"), SPRUCE(0xff735037, "spruce_planks"),
    BIRCH(0xffd7c59a, "birch_planks"), JUNGLE(0xffae7d60, "jungle_planks"),
    ACACIA(0xffb66543, "acacia_planks"), DARK_OAK(0xff50392d, "dark_oak_planks"),
    MANGROVE(0xff884744, "mangrove_planks"), CHERRY(0xffdca7a5, "cherry_planks"),
    BAMBOO(0xffc2ac63, "bamboo_planks"), CRIMSON(0xff85435f, "crimson_planks"),
    WARPED(0xff43857f, "warped_planks"), BONE(0xffe7dfca, "bone_block"),
    QUARTZ(0xffeeeae5, "quartz_block"), CALCITE(0xffcfcfca, "calcite"),
    GLASS(0x509cd4db, "glass"), AMETHYST(0xffaa80cf, "amethyst_block");

    public static final Codec<TileMaterial> CODEC = StringRepresentable.fromEnum(TileMaterial::values);
    private final int color;
    private final String source;
    TileMaterial(int color, String source) { this.color = color; this.source = source; }
    public int color() { return color; }
    public String source() { return source; }
    public String texture() { return source.endsWith("_planks") ? "wood" : getSerializedName(); }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}
