package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;

/** Dispatches first-person arms through the loader's native rendering hooks. */
public final class FirstPersonArm {
    private FirstPersonArm() {}

    public static void render(AvatarRenderer<?> renderer, AbstractClientPlayer player, PoseStack pose,
            SubmitNodeCollector collector, int light, Identifier skin, boolean left) {
        if (left) renderer.renderLeftHand(pose, collector, light, skin, true, player);
        else renderer.renderRightHand(pose, collector, light, skin, true, player);
    }
}
