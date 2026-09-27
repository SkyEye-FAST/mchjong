package top.skyeyefast.mchjong.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;

/** Polls ZIP metadata; two identical observations let archive writers finish before loading. */
public final class PresetDirectory {
    private record Stamp(long size, FileTime modified, Object key) {}
    private final Path root;
    private Map<Path, Stamp> observed, applied;

    public PresetDirectory(Path root) throws IOException {
        this.root = root;
        observed = applied = snapshot();
    }

    public boolean changed() throws IOException {
        var current = snapshot();
        boolean ready = current.equals(observed) && !current.equals(applied);
        observed = current;
        if (ready) applied = current;
        return ready;
    }

    private Map<Path, Stamp> snapshot() throws IOException {
        var result = new HashMap<Path, Stamp>();
        for (String kind : new String[] {"faces", "backs", "sticks", "voices"}) {
            Path directory = root.resolve(kind);
            if (!Files.exists(directory)) continue;
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".zip")).toList()) {
                    var attributes = Files.readAttributes(file, BasicFileAttributes.class);
                    if (attributes.isRegularFile()) result.put(file,
                        new Stamp(attributes.size(), attributes.lastModifiedTime(), attributes.fileKey()));
                }
            }
        }
        return Map.copyOf(result);
    }
}
