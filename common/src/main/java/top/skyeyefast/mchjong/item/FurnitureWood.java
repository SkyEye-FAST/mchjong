package top.skyeyefast.mchjong.item;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** One item/block ID per furniture type; wood is an immutable item component. */
public enum FurnitureWood implements StringRepresentable {
    OAK, SPRUCE, BIRCH, JUNGLE, ACACIA, DARK_OAK, MANGROVE, CHERRY, BAMBOO, CRIMSON, WARPED;

    public static final Codec<FurnitureWood> CODEC = StringRepresentable.fromEnum(FurnitureWood::values);
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}
