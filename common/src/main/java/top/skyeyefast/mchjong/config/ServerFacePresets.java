package top.skyeyefast.mchjong.config;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.PresetBundlePayload;

/** One server-wide collection loaded from ZIPs in the mod config directory. */
public final class ServerFacePresets {
    private static byte[] bundle = new byte[0];
    private ServerFacePresets() {}

    public static void load(Path configDirectory) {
        Path directory = configDirectory.resolve("mchjong/server-presets");
        try {
            var presets = PresetArchives.loadDirectory(directory);
            bundle = PresetArchives.bundle(presets);
            com.mojang.logging.LogUtils.getLogger().info("Loaded {} server tile face presets from {}", presets.size(), directory);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load server tile face presets from " + directory, failure);
        }
    }

    public static void send(ServerPlayer player) {
        for (var part : PresetBundlePayload.split(bundle))
            player.connection.send(PayloadPackets.clientbound(part));
    }
}
