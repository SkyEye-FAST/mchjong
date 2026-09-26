package top.skyeyefast.mchjong.platform;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Loader registry access for optional integration discovery. */
public final class ItemRegistry {
    private ItemRegistry() {}

    public static boolean containsKey(ResourceLocation id) { return net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id); }
    public static Item get(ResourceLocation id) { return net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id); }
    public static ResourceLocation getKey(Item item) { return net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item); }
}
