package top.skyeyefast.mchjong.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.PresetBundlePayload;

/** One server-wide collection loaded from ZIPs in the mod config directory. */
public final class ServerFacePresets {
    private static byte[] faces = new byte[0], backs = new byte[0], sticks = new byte[0];
    private static Map<ResourceLocation, PresetArchives.Stick> sharedSticks = Map.of();
    private static final Map<String, ResourceLocation> appearances = new HashMap<>();
    private static final ResourceLocation DEFAULT_STICK = ResourceLocation.fromNamespaceAndPath("mchjong", "default");
    private ServerFacePresets() {}

    public static void load(Path configDirectory) {
        Path faceDirectory = configDirectory.resolve("mchjong/server-presets/faces");
        Path backDirectory = configDirectory.resolve("mchjong/server-presets/backs");
        Path stickDirectory = configDirectory.resolve("mchjong/server-presets/sticks");
        try {
            var facePresets = PresetArchives.loadDirectory(faceDirectory, PresetArchives.Kind.FACE);
            var backPresets = PresetArchives.loadDirectory(backDirectory, PresetArchives.Kind.BACK);
            var stickPresets = PresetArchives.loadDirectory(stickDirectory, PresetArchives.Kind.STICK);
            sharedSticks = stickPresets.sticks();
            appearances.clear();
            faces = PresetArchives.bundle(facePresets, PresetArchives.Kind.FACE);
            backs = PresetArchives.bundle(backPresets, PresetArchives.Kind.BACK);
            sticks = PresetArchives.bundle(stickPresets, PresetArchives.Kind.STICK);
            com.mojang.logging.LogUtils.getLogger().info("Loaded {} face, {} back and {} stick presets from {}",
                facePresets.faces().size(), backPresets.backs().size(), stickPresets.sticks().size(), configDirectory.resolve("mchjong"));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load server presets from " + configDirectory, failure);
        }
    }

    public static void send(ServerPlayer player) {
        for (var part : PresetBundlePayload.split(faces, PresetArchives.Kind.FACE))
            player.connection.send(PayloadPackets.clientbound(part));
        for (var part : PresetBundlePayload.split(backs, PresetArchives.Kind.BACK))
            player.connection.send(PayloadPackets.clientbound(part));
        for (var part : PresetBundlePayload.split(sticks, PresetArchives.Kind.STICK))
            player.connection.send(PayloadPackets.clientbound(part));
        for (var appearance : appearances.entrySet())
            player.connection.send(PayloadPackets.clientbound(new top.skyeyefast.mchjong.network.StickAppearancePayload(
                appearance.getKey(), appearance.getValue())));
    }

    public static void chooseStick(ServerPlayer player, ResourceLocation requested) {
        ResourceLocation publicId = sharedSticks.containsKey(requested) ? requested : DEFAULT_STICK;
        String name = player.getGameProfile().getName();
        appearances.put(name, publicId);
        var payload = new top.skyeyefast.mchjong.network.StickAppearancePayload(name, publicId);
        for (var target : player.serverLevel().getServer().getPlayerList().getPlayers())
            target.connection.send(PayloadPackets.clientbound(payload));
    }
}
