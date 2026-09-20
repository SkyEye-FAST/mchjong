package top.skyeyefast.mchjong.client;

import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Registered by each loader; screen input uses the player's actual bindings. */
public final class TableKeys {
    public static final KeyMapping INSPECT = key("inspect", GLFW.GLFW_KEY_C);
    public static final KeyMapping VIEW = key("view", GLFW.GLFW_KEY_V);
    public static final KeyMapping RESET = key("reset", GLFW.GLFW_KEY_HOME);
    public static final KeyMapping RIICHI = key("riichi", GLFW.GLFW_KEY_R);
    public static final KeyMapping PASS = key("pass", GLFW.GLFW_KEY_P);
    public static final KeyMapping DRAWER = key("drawer", GLFW.GLFW_KEY_E);
    public static final java.util.List<KeyMapping> ALL = java.util.List.of(INSPECT, VIEW, RESET, RIICHI, PASS, DRAWER);
    private TableKeys() {}
    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.mchjong." + name, code, "key.categories.mchjong");
    }
}
