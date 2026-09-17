package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.HumanoidArm;

/** Two representative native first-person item renders. */
final class ItemPresentationSmoke {
    private int sample, ticks, originalSlot;
    private HumanoidArm originalArm;

    boolean tick(Minecraft client, Path output) {
        if (sample == 2) return true;
        if (originalArm == null) {
            originalArm = client.options.mainHand().get();
            originalSlot = client.player.getInventory().selected;
        }
        if (ticks == 0) {
            client.options.mainHand().set(HumanoidArm.RIGHT);
            client.player.getInventory().selected = sample == 0 ? 4 : 5;
            if (client.player.getMainHandItem().isEmpty()) throw new IllegalStateException("Missing held-item fixture");
        }
        if (++ticks < 20) return false;
        Screenshot.grab(output.toFile(), sample == 0 ? "07-held-glass-tile.png" : "07-held-point-stick.png",
            client.getMainRenderTarget(), ignored -> {});
        ticks = 0;
        if (++sample == 2) {
            client.options.mainHand().set(originalArm);
            client.player.getInventory().selected = originalSlot;
            return true;
        }
        return false;
    }
}
