package top.skyeyefast.mchjong.compat.maid.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Native HEAD item states preserve furniture components for both maid head slots. */
public final class MaidHeadItems {
    private MaidHeadItems() {}
    public interface State {
        ItemStackRenderState mchjong$headFurniture();
        ItemStackRenderState mchjong$displayFurniture();
    }
    public static boolean furniture(ItemStack stack) {
        return stack.is(MahjongContent.TABLE_ITEM) || stack.is(MahjongContent.AUTO_TABLE_ITEM) || stack.is(MahjongContent.STOOL_ITEM);
    }
    public static void submit(State state, PoseStack pose, SubmitNodeCollector collector, int light, int outline) {
        pose.pushPose();
        CustomHeadLayer.translateToHead(pose, CustomHeadLayer.Transforms.DEFAULT);
        state.mchjong$headFurniture().submit(pose, collector, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, outline);
        state.mchjong$displayFurniture().submit(pose, collector, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, outline);
        pose.popPose();
    }
}
