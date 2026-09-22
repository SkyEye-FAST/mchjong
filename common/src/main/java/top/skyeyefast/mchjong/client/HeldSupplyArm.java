package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Attach the native skin and sleeve to the supply's grip after vanilla hand animation. */
public final class HeldSupplyArm {
    @FunctionalInterface
    public interface ModelTransform {
        void apply(BakedModel model, ItemDisplayContext context, PoseStack pose, boolean leftHand);
    }

    private static ModelTransform modelTransform;
    private HeldSupplyArm() {}

    public static void initialize(ModelTransform transform) { modelTransform = transform; }

    public static void render(AbstractClientPlayer player, ItemStack stack, ItemDisplayContext context,
                              boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!context.firstPerson() || player.isInvisible() || player.isSpectator()
            || !(stack.is(MahjongContent.TILE_ITEM) || stack.is(MahjongContent.POINT_STICK))) return;

        var client = Minecraft.getInstance();
        var renderer = (PlayerRenderer) client.getEntityRenderDispatcher().getRenderer(player);
        var model = client.getItemRenderer().getModel(stack, player.level(), player, player.getId() + context.ordinal());
        var transform = new PoseStack();
        modelTransform.apply(model, context, transform, leftHand);
        // Mesh coordinates include ItemRenderer's centering and MahjongItemRenderer's local placement.
        var grip = stack.is(MahjongContent.TILE_ITEM)
            ? new Vector3f(0, -TileMesh.HEIGHT * 4.5f * .4f, 0)
            : new Vector3f((leftHand ? 1 : -1) * FurnitureMesh.STICK_HALF_LENGTH * .75f, -.05f, 0);
        grip.mulPosition(transform.last().pose());

        float side = leftHand ? -1 : 1;
        boolean slim = player.getModelName().equals("slim");
        pose.pushPose();
        pose.translate(grip.x(), grip.y(), grip.z());
        // Keep the arm at player scale; only the grip follows the resource-pack item transform.
        pose.mulPose(Axis.ZP.rotationDegrees(side * 20));
        pose.mulPose(Axis.XP.rotationDegrees(-35));
        pose.translate(side * (slim ? 5.5f : 6f) / 16, -(slim ? 12.5f : 12f) / 16, 0);
        if (leftHand) renderer.renderLeftHand(pose, buffers, light, player);
        else renderer.renderRightHand(pose, buffers, light, player);
        pose.popPose();
    }
}
