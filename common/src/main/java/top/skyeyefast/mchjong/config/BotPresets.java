package top.skyeyefast.mchjong.config;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import top.skyeyefast.mchjong.engine.BotPreset;

/** Loaded once at server startup. Process commands never cross the network or enter a world save. */
public final class BotPresets {
    private static List<BotPreset> presets = List.of();
    private BotPresets() {}
    public static List<BotPreset> all() { return presets; }

    public static void load(Path configDirectory) {
        Path file = configDirectory.resolve("mchjong/bots.json");
        presets = List.of();
        if (!Files.exists(file)) return;
        try {
            if (Files.size(file) > 65536) throw new IOException("Bot presets exceed 64 KiB");
            var loaded = List.of(new Gson().fromJson(Files.readString(file), BotPreset[].class));
            if (loaded.size() > 12 || loaded.stream().map(BotPreset::id).distinct().count() != loaded.size())
                throw new IOException("At most twelve distinct bot presets are allowed");
            presets = loaded;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Cannot load mjai bot presets from " + file, error);
        }
    }
}
