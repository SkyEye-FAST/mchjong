package top.skyeyefast.mchjong.replay;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
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
        byte[] contents = json.toJson(match).getBytes(StandardCharsets.UTF_8);
        if (contents.length > MAX_BYTES) throw new IOException("Replay exceeds the archive size limit");
        atomicWrite(root.resolve(match.id() + ".json"), contents);
        byte[] header = json.toJson(match.header()).getBytes(StandardCharsets.UTF_8);
        for (var player : match.participants()) if (!player.bot())
            atomicWrite(index(player.id()).resolve(match.id() + ".json"), header);
    }

    public ReplayMatch load(UUID player, UUID matchId) throws IOException {
        // The index is an early reject, not the authorization check.
        if (!Files.isRegularFile(index(player).resolve(matchId + ".json"))) throw new IOException("Replay is not available to this player");
        ReplayMatch match = read(root.resolve(matchId + ".json"), ReplayMatch.class, MAX_BYTES);
        if (!match.id().equals(matchId) || !match.permits(player) || match.hands().isEmpty())
            throw new IOException("Replay is not available to this player");
        return match;
    }

    public ReplayMatch.Index list(UUID player, int page) throws IOException {
        if (page < 0 || page > 100_000) throw new IllegalArgumentException("Invalid replay page");
        Path directory = index(player);
        if (!Files.isDirectory(directory)) return new ReplayMatch.Index(page, java.util.List.of(), false);
        try (var paths = Files.list(directory)) {
            var files = paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.comparingLong(ReplayStore::modified).reversed().thenComparing(Path::toString))
                .skip((long) page * PAGE_SIZE).limit(PAGE_SIZE + 1).toList();
            var matches = new ArrayList<ReplayMatch.Header>();
            for (Path file : files.subList(0, Math.min(PAGE_SIZE, files.size())))
                matches.add(read(file, ReplayMatch.Header.class, 16 * 1024));
            return new ReplayMatch.Index(page, matches, files.size() > PAGE_SIZE);
        } catch (UncheckedIOException failure) { throw failure.getCause(); }
    }

    private Path index(UUID player) { return root.resolve("by-player").resolve(player.toString()); }
    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
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
