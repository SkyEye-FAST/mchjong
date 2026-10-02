package top.skyeyefast.mchjong.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.state.EntityMaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.compat.maid.client.MaidHeadItems;

@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.state.EntityMaidRenderState", remap = false)
abstract class MaidFurnitureStateMixin implements MaidHeadItems.State {
    @Unique private final ItemStackRenderState mchjong$head = new ItemStackRenderState();
    @Unique private final ItemStackRenderState mchjong$display = new ItemStackRenderState();
    @Override public ItemStackRenderState mchjong$headFurniture() { return mchjong$head; }
    @Override public ItemStackRenderState mchjong$displayFurniture() { return mchjong$display; }
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private static void mchjong$furniture(EntityMaid maid, EntityMaidRenderState state, float partial,
            BlockModelResolver blocks, ItemModelResolver items, GeckoMaidEntity<?> gecko, CallbackInfo callback) {
        var custom = (MaidHeadItems.State) state;
        var head = maid.getItemBySlot(EquipmentSlot.HEAD); var display = maid.getBackpackShowItem();
        custom.mchjong$headFurniture().clear(); custom.mchjong$displayFurniture().clear();
        if (MaidHeadItems.furniture(head)) items.updateForLiving(custom.mchjong$headFurniture(), head, ItemDisplayContext.HEAD, maid);
        if (MaidHeadItems.furniture(display)) {
            state.headBlock.clear();
            items.updateForLiving(custom.mchjong$displayFurniture(), display, ItemDisplayContext.HEAD, maid);
        }
    }
}
