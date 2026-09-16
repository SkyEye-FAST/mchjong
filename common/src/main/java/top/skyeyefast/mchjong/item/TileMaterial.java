package top.skyeyefast.mchjong.item;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

public enum TileMaterial implements StringRepresentable {
    WOOD(0xffb48a53, "oak_planks"), BONE(0xffe7dfca, "bone_block"),
    QUARTZ(0xffeeeae5, "quartz_block"), CALCITE(0xffcfcfca, "calcite"),
    GLASS(0x509cd4db, "glass"), AMETHYST(0xffaa80cf, "amethyst_block");

    public static final Codec<TileMaterial> CODEC = StringRepresentable.fromEnum(TileMaterial::values);
    private final int color;
    private final String source;
    TileMaterial(int color, String source) { this.color = color; this.source = source; }
    public int color() { return color; }
    public String source() { return source; }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}
