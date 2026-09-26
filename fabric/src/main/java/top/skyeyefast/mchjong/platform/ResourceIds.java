package top.skyeyefast.mchjong.platform;

import net.minecraft.resources.ResourceLocation;

/** Identifier construction through the loader's supported 1.20.1 API. */
public final class ResourceIds {
    private ResourceIds() {}

    public static ResourceLocation of(String value) { return new ResourceLocation(value); }
    public static ResourceLocation of(String namespace, String path) { return new ResourceLocation(namespace, path); }
}
