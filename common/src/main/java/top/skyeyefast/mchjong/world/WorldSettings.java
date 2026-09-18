package top.skyeyefast.mchjong.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** One policy per world save, shared by every table and dimension. Server thread only. */
public final class WorldSettings {
    public record Policy(boolean openHands, boolean invitationTeleport) {}
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<MinecraftServer, WorldSettings> WORLDS = new WeakHashMap<>();
    private final Path path;
    private Policy policy;

    private WorldSettings(Path path) {
        this.path = path;
        try {
            if (Files.exists(path)) reload();
            else {
                policy = new Policy(false, false);
                write(policy);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load mahjong world policy: " + path, failure);
        }
    }

    public static WorldSettings of(MinecraftServer server) {
        return WORLDS.computeIfAbsent(server, world -> new WorldSettings(
            world.getWorldPath(LevelResource.ROOT).resolve("config/mchjong-world.json")));
    }

    public Policy policy() { return policy; }

    public void reload() throws IOException {
        try {
            var object = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (!object.keySet().equals(java.util.Set.of("openHands", "invitationTeleport")))
                throw new IllegalArgumentException("Expected openHands and invitationTeleport");
            for (var value : object.asMap().values())
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
                    throw new IllegalArgumentException("World settings must be booleans");
            policy = JSON.fromJson(object, Policy.class);
        } catch (RuntimeException failure) {
            throw new IOException("Invalid mahjong world policy: " + path, failure);
        }
    }

    public void set(String name, boolean enabled) throws IOException {
        Policy next = switch (name) {
            case "openHands" -> new Policy(enabled, policy.invitationTeleport());
            case "invitationTeleport" -> new Policy(policy.openHands(), enabled);
            default -> throw new IllegalArgumentException("Unknown world setting");
        };
        write(next);
        policy = next;
    }

    private void write(Policy value) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "mchjong-world-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(value) + "\n");
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(temporary); }
    }
}
