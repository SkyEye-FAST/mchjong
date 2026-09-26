package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Renders the native first-person arm at the grip of a special-rendered supply item. */
public final class HeldSupplyArm {
    private record Held(AbstractClientPlayer player, ItemStack stack, ItemDisplayContext context, PoseStack.Pose basePose) {}
    private static final ThreadLocal<Held> CURRENT = new ThreadLocal<>();

    private HeldSupplyArm() {}

    public static void begin(AbstractClientPlayer player, ItemStack stack, ItemDisplayContext context, PoseStack pose) {
        CURRENT.set(new Held(player, stack, context, pose.last().copy()));
    }

    public static void end() { CURRENT.remove(); }

    public static void render(ItemStack stack, PoseStack transformed, SubmitNodeCollector collector, int light) {
        Held held = CURRENT.get();
        if (held == null || !held.context().firstPerson() || held.player().isInvisible() || held.player().isSpectator()
            || !ItemStack.isSameItemSameComponents(held.stack(), stack)
            || !(stack.is(MahjongContent.TILE_ITEM) || stack.is(MahjongContent.POINT_STICK))) return;

        var grip = stack.is(MahjongContent.TILE_ITEM)
            ? new Vector3f(.5f, .5f - TileMesh.HEIGHT * 4.5f / 2, .5f)
            : new Vector3f(.5f + (held.context().leftHand() ? 1 : -1) * FurnitureMesh.STICK_HALF_LENGTH * .75f, .45f, .5f);
        // The special renderer already includes the camera/equip pose and item centering.
        // Recover its local grip before applying the unscaled arm pose, exactly once.
        grip.mulPosition(new Matrix4f(held.basePose().pose()).invert().mul(transformed.last().pose()));

        var client = Minecraft.getInstance();
        var renderer = (AvatarRenderer<?>) client.getEntityRenderDispatcher().getRenderer(held.player());
        boolean leftHand = held.context().leftHand();
        boolean slim = held.player().getSkin().model() == PlayerModelType.SLIM;
        float side = leftHand ? -1 : 1;
        var armPose = new PoseStack();
        armPose.last().set(held.basePose());
        armPose.translate(grip.x(), grip.y(), grip.z());
        // Keep the arm at player scale; only the grip follows the resource-pack item transform.
        // A shallow pitch keeps the forearm below the grip instead of pointing it into the camera.
        armPose.mulPose(Axis.ZP.rotationDegrees(side * 20));
        armPose.mulPose(Axis.XP.rotationDegrees(-15));
        armPose.translate(side * (slim ? 5.5f : 6f) / 16, -(slim ? 12.5f : 12f) / 16, 0);
        var skin = held.player().getSkin().body().texturePath();
        FirstPersonArm.render(renderer, held.player(), armPose, collector, light, skin, leftHand);
    }
}
