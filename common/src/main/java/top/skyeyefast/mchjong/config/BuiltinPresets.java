package top.skyeyefast.mchjong.config;

import java.util.List;
import net.minecraft.resources.Identifier;

/** IDs shipped with the mod and available to every client and server. */
public final class BuiltinPresets {
    public static final List<Identifier> STICKS = List.of(id("bamboo"), id("lightning_rod"), id("end_rod"));
    public static final List<Identifier> BACKS = List.of(id("creeper"), id("mojang"));

    private BuiltinPresets() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mchjong", path);
    }
}
