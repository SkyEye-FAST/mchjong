package top.skyeyefast.mchjong.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import top.skyeyefast.mchjong.config.PresetArchives;
import top.skyeyefast.mchjong.engine.ScoreAnnouncements;
import top.skyeyefast.mchjong.world.MahjongSounds;

/** ZIP recordings are decoded from memory through Minecraft's sound engine. */
public final class VoicePresets {
    public static final ResourceLocation DEFAULT = ResourceLocation.fromNamespaceAndPath("mchjong", "default");
    private record Definition(String name, Map<String, ResourceLocation> recordings) {}
    private static Map<ResourceLocation, Definition> local = Map.of(), server = Map.of();
    private static Map<ResourceLocation, byte[]> localAudio = Map.of(), serverAudio = Map.of();
    private static volatile Map<ResourceLocation, byte[]> audio = Map.of();
    private static List<ResourceLocation> choices = List.of(DEFAULT);
    private static SoundInstance current;
    private static ChannelAccess.ChannelHandle channel;
    private static java.util.concurrent.CompletableFuture<?> decoding;
    private static long started;
    private VoicePresets() {}

    public static List<ResourceLocation> choices() { return choices; }
    public static Component label(ResourceLocation id) {
        if (DEFAULT.equals(id)) return Component.translatable("preset.mchjong.default_voice");
        var definition = server.getOrDefault(id, local.get(id));
        return definition == null ? Component.literal(id.toString()) : Component.literal(definition.name());
    }
    public static byte[] audio(ResourceLocation path) { return audio.get(path); }
    public static ResourceLocation audioPath(ResourceLocation preset, String event) {
        var definition = server.getOrDefault(preset, local.get(preset));
        if (definition == null) return null;
        var sound = definition.recordings().get(event);
        return sound == null ? null : ResourceLocation.fromNamespaceAndPath(sound.getNamespace(),
            "sounds/" + sound.getPath() + ".ogg");
    }

    public static void installLocal(Map<ResourceLocation, PresetArchives.Voice> presets) throws IOException {
        var loaded = install(presets);
        local = loaded.definitions(); localAudio = loaded.audio(); update();
    }
    public static void installServer(Map<ResourceLocation, PresetArchives.Voice> presets) throws IOException {
        var loaded = install(presets);
        server = loaded.definitions(); serverAudio = loaded.audio(); update();
    }
    public static void clearServer() {
        server = Map.of(); serverAudio = Map.of(); update();
    }
    private record Loaded(Map<ResourceLocation, Definition> definitions, Map<ResourceLocation, byte[]> audio) {}
    private static Loaded install(Map<ResourceLocation, PresetArchives.Voice> presets) throws IOException {
        var definitions = new HashMap<ResourceLocation, Definition>();
        var sounds = new HashMap<ResourceLocation, byte[]>();
        for (var entry : presets.entrySet()) {
            if (DEFAULT.equals(entry.getKey())) throw new IOException("Reserved voice preset ID");
            var paths = new HashMap<String, ResourceLocation>();
            for (var recording : entry.getValue().recordings().entrySet()) {
                validateDecoded(recording.getValue());
                String hash = digest(recording.getValue());
                String stem = "voice_presets/" + hash + "/" + recording.getKey();
                var location = ResourceLocation.fromNamespaceAndPath("mchjong", stem);
                var path = ResourceLocation.fromNamespaceAndPath("mchjong", "sounds/" + stem + ".ogg");
                sounds.put(path, recording.getValue());
                paths.put(recording.getKey(), location);
            }
            definitions.put(entry.getKey(), new Definition(entry.getValue().name(), Map.copyOf(paths)));
        }
        return new Loaded(Map.copyOf(definitions), Map.copyOf(sounds));
    }

    private static void validateDecoded(byte[] bytes) throws IOException {
        try (var stream = new JOrbisAudioStream(new ByteArrayInputStream(bytes))) {
            int rate = Math.round(stream.getFormat().getSampleRate());
            int channels = stream.getFormat().getChannels();
            if (rate < 8000 || rate > 96000 || channels < 1 || channels > 2)
                throw new IOException("Unsupported voice recording format");
            long limit = 8L * rate * channels;
            long[] samples = {0};
            try {
                while (stream.readChunk(value -> {
                    if (++samples[0] > limit) throw new TooLong();
                })) { }
            } catch (TooLong failure) {
                throw new IOException("Voice recording exceeds eight seconds", failure);
            }
        }
    }
    private static final class TooLong extends RuntimeException {}
    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes), 0, 16);
        } catch (NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
    }
    private static void update() {
        stopCurrent();
        var sounds = new HashMap<>(localAudio); sounds.putAll(serverAudio);
        audio = Map.copyOf(sounds);
        var ids = new HashSet<>(local.keySet()); ids.addAll(server.keySet());
        choices = java.util.stream.Stream.concat(java.util.stream.Stream.of(DEFAULT),
            ids.stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString))).toList();
    }

    public static void play(String event, float volume) {
        stopCurrent();
        if (volume <= 0 || !MahjongSounds.VOICES.contains(event)) return;
        var selected = TableSettings.get().voicePreset;
        var definition = server.getOrDefault(selected, local.get(selected));
        var manager = Minecraft.getInstance().getSoundManager();
        if (DEFAULT.equals(selected) || definition == null) {
            current = SimpleSoundInstance.forUI(MahjongSounds.voice(event), 1, volume);
            if (current.resolve(manager) == null || current.getSound() == SoundManager.EMPTY_SOUND) current = null;
        } else {
            var sound = definition.recordings().get(event);
            if (sound != null) current = new Recording(sound, volume);
        }
        if (current != null) {
            started = Util.getMillis();
            manager.play(current);
        }
    }

    public static void trackChannel(SoundInstance sound, ChannelAccess.ChannelHandle handle, SoundBufferLibrary buffers) {
        if (sound != current) return;
        channel = handle;
        if (handle != null && !sound.getSound().shouldStream()) {
            decoding = buffers.getCompleteBuffer(sound.getSound().getPath());
            decoding.exceptionally(failure -> {
                org.slf4j.LoggerFactory.getLogger("mchjong").warn("Cannot decode voice {}", sound.getLocation(), failure);
                return null;
            });
        }
    }

    /** Allow asynchronous decoding to start, and bound resource-pack recordings too. */
    public static boolean playing() {
        if (current == null) return false;
        if (decoding != null && decoding.isCompletedExceptionally()) { stopCurrent(); return false; }
        long elapsed = Util.getMillis() - started;
        if (elapsed < ScoreAnnouncements.MAX_VOICE_MILLIS + 250
                && (elapsed < 250 || channel != null && !channel.isStopped())) return true;
        stopCurrent();
        return false;
    }

    private static void stopCurrent() {
        if (current != null) Minecraft.getInstance().getSoundManager().stop(current);
        current = null;
        channel = null;
        decoding = null;
    }
    public static void stop() {
        stopCurrent();
    }

    private static final class Recording extends AbstractSoundInstance {
        private final Sound file;
        private final WeighedSoundEvents events;
        private Recording(ResourceLocation location, float volume) {
            super(location, SoundSource.MASTER, RandomSource.create());
            this.volume = volume;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            file = new Sound(location, ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE,
                false, false, 16);
            events = new WeighedSoundEvents(location, null);
            events.addSound(file);
        }
        @Override public WeighedSoundEvents resolve(SoundManager manager) {
            sound = file;
            return events;
        }
    }
}
