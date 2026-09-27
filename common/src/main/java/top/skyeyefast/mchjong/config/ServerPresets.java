package top.skyeyefast.mchjong.config;

import top.skyeyefast.mchjong.platform.ResourceIds;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.PresetBundlePayload;

/** One server-wide collection loaded from ZIPs in the mod config directory. */
public final class ServerPresets {
    private static byte[] faces = new byte[0], backs = new byte[0], sticks = new byte[0], voices = new byte[0];
    private static Map<ResourceLocation, PresetArchives.Stick> sharedSticks = Map.of();
    private static final Map<String, ResourceLocation> appearances = new HashMap<>();
    private static final Map<String, ResourceLocation> voiceChoices = new HashMap<>();
    private static java.util.Set<ResourceLocation> sharedVoices = java.util.Set.of();
    private static Path directory;
    private static PresetDirectory monitor;
    private static int ticks;
    private static final ResourceLocation DEFAULT_PRESET = ResourceIds.of("mchjong", "default");
    private ServerPresets() {}

    public static void load(Path configDirectory) {
        directory = configDirectory;
        appearances.clear();
        voiceChoices.clear();
        ticks = 0;
        try { monitor = new PresetDirectory(configDirectory.resolve("mchjong/server-presets")); }
        catch (IOException failure) { throw new IllegalStateException("Cannot watch server presets", failure); }
        reload(configDirectory);
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (++ticks % 20 != 0 || monitor == null) return;
        try {
            if (!monitor.changed()) return;
            reload(directory);
            appearances.replaceAll((name, id) -> sharedSticks.containsKey(id) || BuiltinPresets.STICKS.contains(id) ? id : DEFAULT_PRESET);
            voiceChoices.replaceAll((name, id) -> sharedVoices.contains(id) ? id : DEFAULT_PRESET);
            for (var player : server.getPlayerList().getPlayers()) send(player);
        } catch (IOException | RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Cannot reload server presets", failure);
        }
    }

    private static void reload(Path configDirectory) {
        Path faceDirectory = configDirectory.resolve("mchjong/server-presets/faces");
        Path backDirectory = configDirectory.resolve("mchjong/server-presets/backs");
        Path stickDirectory = configDirectory.resolve("mchjong/server-presets/sticks");
        Path voiceDirectory = configDirectory.resolve("mchjong/server-presets/voices");
        try {
            var facePresets = PresetArchives.loadDirectory(faceDirectory, PresetArchives.Kind.FACE);
            var backPresets = PresetArchives.loadDirectory(backDirectory, PresetArchives.Kind.BACK);
            var stickPresets = PresetArchives.loadDirectory(stickDirectory, PresetArchives.Kind.STICK);
            var voicePresets = PresetArchives.loadDirectory(voiceDirectory, PresetArchives.Kind.VOICE);
            byte[] newFaces = PresetArchives.bundle(facePresets, PresetArchives.Kind.FACE);
            byte[] newBacks = PresetArchives.bundle(backPresets, PresetArchives.Kind.BACK);
            byte[] newSticks = PresetArchives.bundle(stickPresets, PresetArchives.Kind.STICK);
            byte[] newVoices = PresetArchives.bundle(voicePresets, PresetArchives.Kind.VOICE);
            sharedSticks = stickPresets.sticks();
            sharedVoices = java.util.Set.copyOf(voicePresets.voices().keySet());
            faces = newFaces; backs = newBacks; sticks = newSticks; voices = newVoices;
            com.mojang.logging.LogUtils.getLogger().info("Loaded {} face, {} back, {} stick and {} voice presets from {}",
                facePresets.faces().size(), backPresets.backs().size(), stickPresets.sticks().size(),
                voicePresets.voices().size(), configDirectory.resolve("mchjong"));
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
        for (var part : PresetBundlePayload.split(voices, PresetArchives.Kind.VOICE))
            player.connection.send(PayloadPackets.clientbound(part));
        for (var appearance : appearances.entrySet())
            player.connection.send(PayloadPackets.clientbound(new top.skyeyefast.mchjong.network.StickAppearancePayload(
                appearance.getKey(), appearance.getValue())));
        for (var choice : voiceChoices.entrySet())
            player.connection.send(PayloadPackets.clientbound(new top.skyeyefast.mchjong.network.VoiceAppearancePayload(
                choice.getKey(), choice.getValue())));
    }

    public static void chooseStick(ServerPlayer player, ResourceLocation requested) {
        ResourceLocation publicId = sharedSticks.containsKey(requested) || BuiltinPresets.STICKS.contains(requested)
            ? requested : DEFAULT_PRESET;
        String name = player.getGameProfile().getName();
        appearances.put(name, publicId);
        var payload = new top.skyeyefast.mchjong.network.StickAppearancePayload(name, publicId);
        for (var target : player.serverLevel().getServer().getPlayerList().getPlayers())
            target.connection.send(PayloadPackets.clientbound(payload));
    }

    public static void chooseVoice(ServerPlayer player, ResourceLocation requested) {
        ResourceLocation publicId = sharedVoices.contains(requested) ? requested : DEFAULT_PRESET;
        String name = player.getGameProfile().getName();
        if (publicId.equals(voiceChoices.put(name, publicId))) return;
        var payload = new top.skyeyefast.mchjong.network.VoiceAppearancePayload(name, publicId);
        for (var target : player.serverLevel().getServer().getPlayerList().getPlayers())
            target.connection.send(PayloadPackets.clientbound(payload));
    }
}
