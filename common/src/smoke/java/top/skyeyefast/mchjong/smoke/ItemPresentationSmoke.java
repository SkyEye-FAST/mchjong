package top.skyeyefast.mchjong.smoke;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.HumanoidArm;
import org.lwjgl.glfw.GLFW;

/** Native empty hands alongside tile and point-stick grips with each main-hand preference. */
final class ItemPresentationSmoke {
    private int sample, ticks, originalSlot, windowWidth, windowHeight;
    private HumanoidArm originalArm;

    boolean tick(Minecraft client, Path output) {
        if (sample == 9) return true;
        if (originalArm == null) {
            originalArm = client.options.mainHand().get();
            originalSlot = client.player.getInventory().getSelectedSlot();
            windowWidth = client.getWindow().getScreenWidth();
            windowHeight = client.getWindow().getScreenHeight();
        }
        if (ticks == 0) {
            if (sample == 6) {
                GLFW.glfwSetWindowSize(client.getWindow().handle(), 640, 480);
                client.resizeGui();
            }
            client.options.mainHand().set(sample < 3 || sample >= 6 ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
            client.player.getInventory().setSelectedSlot(switch (sample % 3) { case 0 -> 8; case 1 -> 4; default -> 5; });
            if (client.player.getMainHandItem().isEmpty() != (sample % 3 == 0))
                throw new IllegalStateException("Incorrect held-item fixture");
        }
        if (++ticks < 20) return false;
        String name = switch (sample % 3) {
            case 0 -> "07-native-empty-hand";
            case 1 -> "07-held-glass-tile";
            default -> "07-held-point-stick";
        };
        String side = sample < 3 || sample >= 6 ? "-right" : "-left";
        Screenshot.grab(output.toFile(), name + side + (sample >= 6 ? "-small.png" : ".png"),
            client.getMainRenderTarget(), 1, ignored -> {});
        ticks = 0;
        if (++sample == 9) {
            client.options.mainHand().set(originalArm);
            client.player.getInventory().setSelectedSlot(originalSlot);
            GLFW.glfwSetWindowSize(client.getWindow().handle(), windowWidth, windowHeight);
            client.resizeGui();
            return true;
        }
        return false;
    }
}
