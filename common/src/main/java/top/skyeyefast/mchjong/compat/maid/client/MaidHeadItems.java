package top.skyeyefast.mchjong.compat.maid.client;

import com.github.tartaricacid.touhoulittlemaid.client.model.bedrock.BedrockModel;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.GeckoEntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import top.skyeyefast.mchjong.world.MahjongContent;

/** TLM's native head layer accepts skulls only. Bridge furniture to the vanilla HEAD item path. */
public final class MaidHeadItems {
    private MaidHeadItems() {}

    public static void add(EntityMaidRenderer renderer) {
        // TLM backpack layers modify the shared pose without restoring it; run before them.
        ((top.skyeyefast.mchjong.mixin.LivingRendererAccessor) renderer).mchjong$layers().add(0, new RenderLayer<Mob, BedrockModel<Mob>>(renderer) {
            @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, Mob maid,
                    float limb, float amount, float partial, float age, float yaw, float pitch) {
                if (!furniture(maid) || !renderer.getMainInfo().isShowCustomHead() || !getParentModel().hasHead()) return;
                pose.pushPose();
                getParentModel().getHead().translateAndRotateAndScale(pose);
                renderItem(maid, pose, buffers, light);
                pose.popPose();
            }
        });
    }

    public static <T extends Mob> void add(GeckoEntityMaidRenderer<T> renderer) {
        renderer.getLayerRenderers().add(0, new GeckoHead<>(renderer));
    }

    private static final class GeckoHead<T extends Mob, R extends IGeoEntityRenderer<T>> extends GeoLayerRenderer<T, R> {
        GeckoHead(R renderer) { super(renderer); }
        @Override public GeoLayerRenderer<T, R> copy(R renderer) {
            return new GeckoHead<>(renderer);
        }
        @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, T maid,
                float limb, float amount, float partial, float age, float yaw, float pitch) {
            var entity = getGeoEntity(maid);
            var model = entity.getGeoModel();
            if (!furniture(maid) || model == null || !entity.getMaidInfo().isShowCustomHead() || model.headBones().isEmpty()) return;
            pose.pushPose();
            RenderUtils.prepMatrixForLocator(pose, model.headBones());
            // Gecko locators use opposite X/Y axes to vanilla model parts.
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            renderItem(maid, pose, buffers, light);
            pose.popPose();
        }
    }

    private static boolean furniture(Mob maid) {
        var stack = maid.getItemBySlot(EquipmentSlot.HEAD);
        return stack.is(MahjongContent.TABLE_ITEM) || stack.is(MahjongContent.AUTO_TABLE_ITEM) || stack.is(MahjongContent.STOOL_ITEM);
    }

    private static void renderItem(Mob maid, PoseStack pose, MultiBufferSource buffers, int light) {
        CustomHeadLayer.translateToHead(pose, false);
        Minecraft.getInstance().getItemRenderer().renderStatic(maid.getItemBySlot(EquipmentSlot.HEAD),
            ItemDisplayContext.HEAD, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
            pose, buffers, maid.level(), maid.getId());
    }
}
