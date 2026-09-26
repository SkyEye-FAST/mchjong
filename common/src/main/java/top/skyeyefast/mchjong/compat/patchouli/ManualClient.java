package top.skyeyefast.mchjong.compat.patchouli;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import top.skyeyefast.mchjong.client.TableSettings;

/** The absent-dependency path deliberately contains no Patchouli API references. */
public final class ManualClient {
    public static final KeyMapping OPEN = new KeyMapping("key.mchjong.manual", GLFW.GLFW_KEY_H, "key.categories.mchjong");
    private static boolean recommended;

    private ManualClient() {}

    public static void tick(boolean installed, Runnable openBook) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.screen != null) return;
        if (!installed && !recommended && TableSettings.get().recommendPatchouli) {
            recommended = true;
            client.setScreen(new ManualRecommendationScreen());
            return;
        }
        while (OPEN.consumeClick()) {
            if (installed) openBook.run();
            else client.setScreen(new ManualRecommendationScreen());
        }
    }
}
