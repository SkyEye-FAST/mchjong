package top.skyeyefast.mchjong.smoke;

import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Only the smoke source set loads these drivers, and only for the selected installed browser. */
interface BrowserDriver {
    boolean ready();
    List<ItemStack> catalogue();
    Set<ResourceLocation> query(ItemStack stack, boolean output);
    void showRecipe(ResourceLocation id);
}
