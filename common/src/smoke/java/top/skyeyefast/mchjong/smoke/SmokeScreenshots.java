package top.skyeyefast.mchjong.smoke;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

/** Capture only explicitly requested filenames; ordinary smoke runs produce assertions and logs. */
public final class SmokeScreenshots {
    private static final Set<String> REQUESTED = Arrays.stream(System.getProperty("mchjong.smoke.screenshots", "").split(","))
        .map(String::trim).filter(name -> !name.isEmpty()).collect(Collectors.toSet());

    private SmokeScreenshots() {}

    public static void grab(File output, String name, RenderTarget target, Consumer<Component> callback) {
        if (REQUESTED.contains(name)) Screenshot.grab(output, name, target, callback);
    }
}
