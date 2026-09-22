package top.skyeyefast.mchjong.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplyItem;
import top.skyeyefast.mchjong.item.MahjongBoxItem;
import net.minecraft.world.item.BlockItem;
import top.skyeyefast.mchjong.world.MahjongStoolBlock;
import top.skyeyefast.mchjong.world.MahjongTableItem;

/** 1.20 NBT has no component defaults; normalize only this mod's item data at native stack boundaries. */
@Mixin(ItemStack.class)
public abstract class ItemStackDataMixin {
    @Shadow private CompoundTag tag;
    @Shadow public abstract Item getItem();

    @Inject(method = {"setTag", "<init>(Lnet/minecraft/nbt/CompoundTag;)V"}, at = @At("RETURN"))
    private void mchjong$normalize(CompoundTag input, CallbackInfo callback) {
        if (tag == null || !tag.contains("mchjong", 10)) return;
        Item item = getItem();
        if (!(item instanceof MahjongSupplyItem || item instanceof MahjongBoxItem || item instanceof MahjongTableItem
            || item instanceof BlockItem block && block.getBlock() instanceof MahjongStoolBlock)) return;
        MahjongComponents.normalize(item, tag);
        if (tag != null && tag.isEmpty()) tag = null;
    }
}
