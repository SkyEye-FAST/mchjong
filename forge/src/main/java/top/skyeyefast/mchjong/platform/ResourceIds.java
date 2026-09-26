package top.skyeyefast.mchjong.platform;

import net.minecraft.resources.ResourceLocation;

/** Identifier construction through the loader's supported 1.20.1 API. */
public final class ResourceIds {
    private ResourceIds() {}

    public static ResourceLocation of(String value) { return ResourceLocation.parse(value); }
    public static ResourceLocation of(String namespace, String path) { return ResourceLocation.fromNamespaceAndPath(namespace, path); }
}
