package top.skyeyefast.mchjong.world;

import com.electronwill.nightconfig.core.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import top.skyeyefast.mchjong.config.TomlFiles;

/** One policy per world save, shared by every table and dimension. Server thread only. */
public final class WorldSettings {
    public record Policy(boolean invitationTeleport) {}
    private static final Map<MinecraftServer, WorldSettings> WORLDS = new WeakHashMap<>();
    private final Path path;
    private Policy policy;

    private WorldSettings(Path path) {
        this.path = path;
        try {
            if (Files.exists(path)) reload();
            else {
                policy = new Policy(false);
                write(policy);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load mahjong world policy: " + path, failure);
        }
    }

    public static WorldSettings of(MinecraftServer server) {
        return WORLDS.computeIfAbsent(server, world -> new WorldSettings(
            world.getWorldPath(LevelResource.ROOT).resolve("config/mchjong-world.toml")));
    }

    public Policy policy() { return policy; }

    public void reload() throws IOException {
        var config = TomlFiles.read(path);
        if (!config.valueMap().keySet().equals(Set.of("invitationTeleport"))
            || !(config.get("invitationTeleport") instanceof Boolean enabled))
            throw new IOException("Invalid mahjong world policy: " + path);
        policy = new Policy(enabled);
    }

    public void set(String name, boolean enabled) throws IOException {
        Policy next = switch (name) {
            case "invitationTeleport" -> new Policy(enabled);
            default -> throw new IllegalArgumentException("Unknown world setting");
        };
        write(next);
        policy = next;
    }

    private void write(Policy value) throws IOException {
        Config config = Config.inMemory();
        config.set("invitationTeleport", value.invitationTeleport());
        TomlFiles.write(path, config);
    }
}
