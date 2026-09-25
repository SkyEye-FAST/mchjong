package top.skyeyefast.mchjong.config;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** IDs shipped with the mod and available to every client and server. */
public final class BuiltinPresets {
    public static final List<ResourceLocation> STICKS = List.of(id("bamboo"), id("lightning_rod"), id("end_rod"));
    public static final List<ResourceLocation> BACKS = List.of(id("creeper"), id("mojang"));

    private BuiltinPresets() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mchjong", path);
    }
}
