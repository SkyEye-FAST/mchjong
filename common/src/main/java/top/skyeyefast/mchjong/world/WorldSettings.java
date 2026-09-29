package top.skyeyefast.mchjong.world;

import com.electronwill.nightconfig.core.Config;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import top.skyeyefast.mchjong.config.TomlFiles;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.SpectatorHandVisibility;
import top.skyeyefast.mchjong.engine.WorldPolicy;

/** One policy per world save, shared by every table and dimension. Server thread only. */
public final class WorldSettings {
    private static final Set<String> KEYS = Set.of(
        "invitationTeleport", "invitationsEnabled", "spectatingEnabled", "spectatorHandVisibility",
        "allowConvenienceHints", "allowExperienceRewards", "deductNegativeExperience", "maxExperienceChange",
        "replaysEnabled", "allowBots", "allowCompanionPlayers", "allowCustomRules", "forcedPreset");

    public record Policy(boolean invitationTeleport, boolean invitationsEnabled, boolean spectatingEnabled,
                         SpectatorHandVisibility spectatorHandVisibility, boolean allowConvenienceHints,
                         boolean allowExperienceRewards, boolean deductNegativeExperience, int maxExperienceChange,
                         boolean replaysEnabled, boolean allowBots, boolean allowCompanionPlayers,
                         boolean allowCustomRules, RiichiPreset forcedPreset) {
        public static final Policy DEFAULT = new Policy(false, true, true, SpectatorHandVisibility.HIDDEN,
            true, false, true, 5_000, true, true, true, true, null);

        public Policy {
            if (spectatorHandVisibility == null) throw new IllegalArgumentException("Missing spectator hand visibility");
            if (maxExperienceChange < 0 || maxExperienceChange > WorldPolicy.MAX_EXPERIENCE_LIMIT)
                throw new IllegalArgumentException("Invalid experience limit");
        }

        public WorldPolicy gamePolicy() {
            return new WorldPolicy(allowConvenienceHints, allowExperienceRewards, deductNegativeExperience,
                maxExperienceChange, replaysEnabled, allowBots, allowCompanionPlayers, allowCustomRules, forcedPreset);
        }
    }

    private static final Map<MinecraftServer, WorldSettings> WORLDS = new WeakHashMap<>();
    private final Path path;
    private Policy policy;

    private WorldSettings(Path path) {
        this.path = path;
        try {
            if (Files.exists(path)) reload();
            else {
                policy = Policy.DEFAULT;
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
        if (!Set.copyOf(config.entrySet().stream().map(entry -> entry.getKey()).toList()).equals(KEYS))
            throw new IOException("Invalid mahjong world policy keys: " + path);
        try {
            policy = new Policy(bool(config, "invitationTeleport"), bool(config, "invitationsEnabled"),
                bool(config, "spectatingEnabled"), enumValue(config, "spectatorHandVisibility", SpectatorHandVisibility.class),
                bool(config, "allowConvenienceHints"), bool(config, "allowExperienceRewards"),
                bool(config, "deductNegativeExperience"), integer(config, "maxExperienceChange"),
                bool(config, "replaysEnabled"), bool(config, "allowBots"), bool(config, "allowCompanionPlayers"),
                bool(config, "allowCustomRules"), forcedPreset(config));
        } catch (IllegalArgumentException failure) {
            throw new IOException("Invalid mahjong world policy: " + path, failure);
        }
    }

    public void set(String name, boolean enabled) throws IOException {
        Policy p = policy;
        Policy next = switch (name) {
            case "invitationTeleport" -> new Policy(enabled, p.invitationsEnabled(), p.spectatingEnabled(), p.spectatorHandVisibility(), p.allowConvenienceHints(), p.allowExperienceRewards(), p.deductNegativeExperience(), p.maxExperienceChange(), p.replaysEnabled(), p.allowBots(), p.allowCompanionPlayers(), p.allowCustomRules(), p.forcedPreset());
            case "invitationsEnabled" -> new Policy(p.invitationTeleport(), enabled, p.spectatingEnabled(), p.spectatorHandVisibility(), p.allowConvenienceHints(), p.allowExperienceRewards(), p.deductNegativeExperience(), p.maxExperienceChange(), p.replaysEnabled(), p.allowBots(), p.allowCompanionPlayers(), p.allowCustomRules(), p.forcedPreset());
            case "spectatingEnabled" -> new Policy(p.invitationTeleport(), p.invitationsEnabled(), enabled, p.spectatorHandVisibility(), p.allowConvenienceHints(), p.allowExperienceRewards(), p.deductNegativeExperience(), p.maxExperienceChange(), p.replaysEnabled(), p.allowBots(), p.allowCompanionPlayers(), p.allowCustomRules(), p.forcedPreset());
            case "allowConvenienceHints" -> new Policy(p.invitationTeleport(), p.invitationsEnabled(), p.spectatingEnabled(), p.spectatorHandVisibility(), enabled, p.allowExperienceRewards(), p.deductNegativeExperience(), p.maxExperienceChange(), p.replaysEnabled(), p.allowBots(), p.allowCompanionPlayers(), p.allowCustomRules(), p.forcedPreset());
            case "allowExperienceRewards" -> new Policy(p.invitationTeleport(), p.invitationsEnabled(), p.spectatingEnabled(), p.spectatorHandVisibility(), p.allowConvenienceHints(), enabled, p.deductNegativeExperience(), p.maxExperienceChange(), p.replaysEnabled(), p.allowBots(), p.allowCompanionPlayers(), p.allowCustomRules(), p.forcedPreset());
            case "deductNegativeExperience" -> new Policy(p.invitationTeleport(), p.invitationsEnabled(), p.spectatingEnabled(), p.spectatorHandVisibility(), p.allowConvenienceHints(), p.allowExperienceRewards(), enabled, p.maxExperienceChange(), p.replaysEnabled(), p.allowBots(), p.allowCompanionPlayers(), p.allowCustomRules(), p.forcedPreset());
            case "replaysEnabled" -> new Policy(p.invitationTeleport(), p.invitationsEnabled(), p.spectatingEnabled(), p.spectatorHandVisibility(), p.allowConvenienceHints(), p.allowExperienceRewards(), p.deductNegativeExperience(), p.maxExperienceChange(), enabled, p.allowBots(), p.allowCompanionPlayers(), p.allowCustomRules(), p.forcedPreset());
            case "allowBots" -> new Policy(p.invitationTeleport(), p.invitationsEnabled(), p.spectatingEnabled(), p.spectatorHandVisibility(), p.allowConvenienceHints(), p.allowExperienceRewards(), p.deductNegativeExperience(), p.maxExperienceChange(), p.replaysEnabled(), enabled, p.allowCompanionPlayers(), p.allowCustomRules(), p.forcedPreset());
            case "allowCompanionPlayers" -> new Policy(p.invitationTeleport(), p.invitationsEnabled(), p.spectatingEnabled(), p.spectatorHandVisibility(), p.allowConvenienceHints(), p.allowExperienceRewards(), p.deductNegativeExperience(), p.maxExperienceChange(), p.replaysEnabled(), p.allowBots(), enabled, p.allowCustomRules(), p.forcedPreset());
            case "allowCustomRules" -> new Policy(p.invitationTeleport(), p.invitationsEnabled(), p.spectatingEnabled(), p.spectatorHandVisibility(), p.allowConvenienceHints(), p.allowExperienceRewards(), p.deductNegativeExperience(), p.maxExperienceChange(), p.replaysEnabled(), p.allowBots(), p.allowCompanionPlayers(), enabled, p.forcedPreset());
            default -> throw new IllegalArgumentException("Unknown boolean world setting: " + name);
        };
        update(next);
    }

    public void setSpectatorHandVisibility(SpectatorHandVisibility visibility) throws IOException {
        Policy p = policy;
        update(new Policy(p.invitationTeleport(), p.invitationsEnabled(), p.spectatingEnabled(), visibility,
            p.allowConvenienceHints(), p.allowExperienceRewards(), p.deductNegativeExperience(), p.maxExperienceChange(),
            p.replaysEnabled(), p.allowBots(), p.allowCompanionPlayers(), p.allowCustomRules(), p.forcedPreset()));
    }

    public void setMaxExperienceChange(int amount) throws IOException {
        Policy p = policy;
        update(new Policy(p.invitationTeleport(), p.invitationsEnabled(), p.spectatingEnabled(), p.spectatorHandVisibility(),
            p.allowConvenienceHints(), p.allowExperienceRewards(), p.deductNegativeExperience(), amount,
            p.replaysEnabled(), p.allowBots(), p.allowCompanionPlayers(), p.allowCustomRules(), p.forcedPreset()));
    }

    public void setForcedPreset(RiichiPreset preset) throws IOException {
        Policy p = policy;
        update(new Policy(p.invitationTeleport(), p.invitationsEnabled(), p.spectatingEnabled(), p.spectatorHandVisibility(),
            p.allowConvenienceHints(), p.allowExperienceRewards(), p.deductNegativeExperience(), p.maxExperienceChange(),
            p.replaysEnabled(), p.allowBots(), p.allowCompanionPlayers(), p.allowCustomRules(), preset));
    }

    private void update(Policy next) throws IOException {
        write(next);
        policy = next;
    }

    private void write(Policy value) throws IOException {
        Config config = Config.inMemory();
        config.set("invitationTeleport", value.invitationTeleport());
        config.set("invitationsEnabled", value.invitationsEnabled());
        config.set("spectatingEnabled", value.spectatingEnabled());
        config.set("spectatorHandVisibility", value.spectatorHandVisibility().name().toLowerCase(Locale.ROOT));
        config.set("allowConvenienceHints", value.allowConvenienceHints());
        config.set("allowExperienceRewards", value.allowExperienceRewards());
        config.set("deductNegativeExperience", value.deductNegativeExperience());
        config.set("maxExperienceChange", value.maxExperienceChange());
        config.set("replaysEnabled", value.replaysEnabled());
        config.set("allowBots", value.allowBots());
        config.set("allowCompanionPlayers", value.allowCompanionPlayers());
        config.set("allowCustomRules", value.allowCustomRules());
        config.set("forcedPreset", value.forcedPreset() == null ? "none" : value.forcedPreset().name().toLowerCase(Locale.ROOT));
        TomlFiles.write(path, config);
    }

    private static boolean bool(Config config, String key) {
        Object value = config.get(key);
        if (value instanceof Boolean result) return result;
        throw new IllegalArgumentException("Expected boolean: " + key);
    }

    private static int integer(Config config, String key) {
        Object value = config.get(key);
        if (value instanceof Number number) return Math.toIntExact(number.longValue());
        throw new IllegalArgumentException("Expected integer: " + key);
    }

    private static <T extends Enum<T>> T enumValue(Config config, String key, Class<T> type) {
        Object value = config.get(key);
        if (!(value instanceof String name)) throw new IllegalArgumentException("Expected string: " + key);
        return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
    }

    private static RiichiPreset forcedPreset(Config config) {
        Object value = config.get("forcedPreset");
        if (!(value instanceof String name)) throw new IllegalArgumentException("Expected string: forcedPreset");
        return name.equalsIgnoreCase("none") ? null : RiichiPreset.valueOf(name.toUpperCase(Locale.ROOT));
    }
}
