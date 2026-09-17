package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.HumanoidArm;

/** Native tile and point-stick grips with each main-hand preference. */
final class ItemPresentationSmoke {
    private int sample, ticks, originalSlot;
    private HumanoidArm originalArm;

    boolean tick(Minecraft client, Path output) {
        if (sample == 4) return true;
        if (originalArm == null) {
            originalArm = client.options.mainHand().get();
            originalSlot = client.player.getInventory().selected;
        }
        if (ticks == 0) {
            client.options.mainHand().set(sample < 2 ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
            client.player.getInventory().selected = sample % 2 == 0 ? 4 : 5;
            if (client.player.getMainHandItem().isEmpty()) throw new IllegalStateException("Missing held-item fixture");
        }
        if (++ticks < 20) return false;
        String name = sample % 2 == 0 ? "07-held-glass-tile" : "07-held-point-stick";
        Screenshot.grab(output.toFile(), name + (sample < 2 ? "-right.png" : "-left.png"),
            client.getMainRenderTarget(), ignored -> {});
        ticks = 0;
        if (++sample == 4) {
            client.options.mainHand().set(originalArm);
            client.player.getInventory().selected = originalSlot;
            return true;
        }
        return false;
    }
}
