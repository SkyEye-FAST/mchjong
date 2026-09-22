package top.skyeyefast.mchjong.forge.mixin;

import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Forge owns this field; bind the shared renderer without loader imports in shared items. */
@Mixin(Item.class)
public interface ItemRenderProperties {
    @Accessor(value = "renderProperties", remap = false)
    void mchjong$renderProperties(Object properties);
}
