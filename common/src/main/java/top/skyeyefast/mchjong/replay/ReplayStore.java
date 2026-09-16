package top.skyeyefast.mchjong.replay;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import top.skyeyefast.mchjong.engine.ReplayMatch;

/** One canonical replay, plus small per-participant indexes. No live hand is persisted here. */
public final class ReplayStore {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int PAGE_SIZE = 12;
    private final Path root;
    private final Gson json;

    public ReplayStore(Path root, Gson json) { this.root = root; this.json = json; }

    public void save(ReplayMatch match) throws IOException {
        if (match.hands().isEmpty()) throw new IllegalArgumentException("Only completed hands can be archived");
        if (match.participants().stream().filter(player -> !player.bot())
                .allMatch(player -> Files.exists(deleted(player.id(), match.id())))) {
            Files.deleteIfExists(root.resolve(match.id() + ".json"));
            return;
        }
        byte[] contents = json.toJson(match).getBytes(StandardCharsets.UTF_8);
        if (contents.length > MAX_BYTES) throw new IOException("Replay exceeds the archive size limit");
        atomicWrite(root.resolve(match.id() + ".json"), contents);
        byte[] header = json.toJson(match.header()).getBytes(StandardCharsets.UTF_8);
        for (var player : match.participants()) if (!player.bot() && !Files.exists(deleted(player.id(), match.id())))
            atomicWrite(index(player.id()).resolve(match.id() + ".json"), header);
    }

    public ReplayMatch load(UUID player, UUID matchId) throws IOException {
        // The index is an early reject, not the authorization check.
        if (Files.exists(deleted(player, matchId)) || !Files.isRegularFile(index(player).resolve(matchId + ".json")))
            throw new IOException("Replay is not available to this player");
        ReplayMatch match = read(root.resolve(matchId + ".json"), ReplayMatch.class, MAX_BYTES);
        if (!match.id().equals(matchId) || !match.permits(player) || match.hands().isEmpty())
            throw new IOException("Replay is not available to this player");
        return match;
    }

    /** Removal is private to a participant; a durable marker prevents later hands from restoring it. */
    public void delete(UUID player, UUID matchId) throws IOException {
        ReplayMatch match = load(player, matchId);
        atomicWrite(deleted(player, matchId), new byte[0]);
        Files.deleteIfExists(index(player).resolve(matchId + ".json"));
        if (match.participants().stream().filter(participant -> !participant.bot())
                .allMatch(participant -> Files.exists(deleted(participant.id(), matchId))))
            Files.deleteIfExists(root.resolve(matchId + ".json"));
    }

    public ReplayMatch.Index list(UUID player, int page, String search, boolean oldestFirst) throws IOException {
        if (page < 0 || page > 100_000 || search == null || search.length() > 80)
            throw new IllegalArgumentException("Invalid replay query");
        String query = search.strip().toLowerCase(Locale.ROOT);
        Path directory = index(player);
        if (!Files.isDirectory(directory)) return new ReplayMatch.Index(page, search, oldestFirst, java.util.List.of(), false);
        var matches = new ArrayList<ReplayMatch.Header>();
        try (var paths = Files.list(directory)) {
            for (Path file : paths.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                var header = read(file, ReplayMatch.Header.class, 16 * 1024);
                if (!Files.exists(deleted(player, header.id())) && (query.isEmpty()
                        || header.id().toString().contains(query)
                        || header.names().stream().anyMatch(name -> name.toLowerCase(Locale.ROOT).contains(query))))
                    matches.add(header);
            }
        }
        Comparator<ReplayMatch.Header> order = Comparator.comparingLong(ReplayMatch.Header::updatedAt);
        if (!oldestFirst) order = order.reversed();
        matches.sort(order.thenComparing(ReplayMatch.Header::id));
        long start = (long) page * PAGE_SIZE;
        var entries = matches.stream().skip(start).limit(PAGE_SIZE).toList();
        return new ReplayMatch.Index(page, search, oldestFirst, entries, start + PAGE_SIZE < matches.size());
    }

    private Path index(UUID player) { return root.resolve("by-player").resolve(player.toString()); }
    private Path deleted(UUID player, UUID match) { return index(player).resolve(match + ".deleted"); }
    private <T> T read(Path path, Class<T> type, int limit) throws IOException {
        if (Files.size(path) > limit) throw new IOException("Replay file exceeds its size limit");
        try {
            T value = json.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
            if (value == null) throw new IOException("Empty replay file");
            return value;
        } catch (RuntimeException failure) { throw new IOException("Invalid replay file", failure); }
    }

    public static void atomicWrite(Path destination, byte[] contents) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".mchjong-", ".tmp");
        try {
            Files.write(temporary, contents);
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
