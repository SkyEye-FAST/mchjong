package top.skyeyefast.mchjong.config;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.PresetBundlePayload;

/** One server-wide collection loaded from ZIPs in the mod config directory. */
public final class ServerFacePresets {
    private static byte[] faces = new byte[0], backs = new byte[0];
    private ServerFacePresets() {}

    public static void load(Path configDirectory) {
        Path faceDirectory = configDirectory.resolve("mchjong/server-presets/faces");
        Path backDirectory = configDirectory.resolve("mchjong/server-presets/backs");
        try {
            var facePresets = PresetArchives.loadDirectory(faceDirectory, PresetArchives.Kind.FACE);
            var backPresets = PresetArchives.loadDirectory(backDirectory, PresetArchives.Kind.BACK);
            faces = PresetArchives.bundle(facePresets, PresetArchives.Kind.FACE);
            backs = PresetArchives.bundle(backPresets, PresetArchives.Kind.BACK);
            com.mojang.logging.LogUtils.getLogger().info("Loaded {} face and {} back presets from {}",
                facePresets.faces().size(), backPresets.backs().size(), configDirectory.resolve("mchjong"));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load server presets from " + configDirectory, failure);
        }
    }

    public static void send(ServerPlayer player) {
        for (var part : PresetBundlePayload.split(faces, PresetArchives.Kind.FACE))
            player.connection.send(PayloadPackets.clientbound(part));
        for (var part : PresetBundlePayload.split(backs, PresetArchives.Kind.BACK))
            player.connection.send(PayloadPackets.clientbound(part));
    }
}
