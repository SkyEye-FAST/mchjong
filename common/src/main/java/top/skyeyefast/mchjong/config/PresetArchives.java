package top.skyeyefast.mchjong.config;

import com.electronwill.nightconfig.toml.TomlParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** ZIP format shared by local and server config directories. */
public final class PresetArchives {
    public static final java.util.List<String> TILE_KEYS = java.util.List.of(
        "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m",
        "1p", "2p", "3p", "4p", "5p", "6p", "7p", "8p", "9p",
        "1s", "2s", "3s", "4s", "5s", "6s", "7s", "8s", "9s",
        "1z", "2z", "3z", "4z", "5z", "6z", "7z",
        "0m", "0p", "0s", "1q", "2q", "3q", "4q", "5q", "6q", "7q", "8q");
    public record Images(String name, Map<String, byte[]> tiles) {}
    public static final int MAX_ARCHIVE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    private static final String MANIFEST = "/preset.toml";
    private PresetArchives() {}

    public static Map<TileFacePreset, Images> read(InputStream input) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        int total = 0, entries = 0;
        try (var zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (++entries > 4096) throw new IOException("Too many entries in tile face archive");
                if (entry.isDirectory()) continue;
                byte[] bytes = zip.readNBytes(MAX_ENTRY_BYTES + 1);
                total += bytes.length;
                if (bytes.length > MAX_ENTRY_BYTES || total > MAX_ARCHIVE_BYTES)
                    throw new IOException("Tile face archive exceeds size limit");
                if (files.putIfAbsent(entry.getName(), bytes) != null)
                    throw new IOException("Duplicate tile face archive path: " + entry.getName());
            }
        }
        Map<TileFacePreset, Images> presets = new HashMap<>();
        for (var file : files.entrySet()) {
            String path = file.getKey();
            if (!path.endsWith(MANIFEST)) continue;
            int separator = path.indexOf('/');
            if (separator < 1 || separator >= path.length() - MANIFEST.length()) continue;
            String namespace = path.substring(0, separator);
            String name = path.substring(separator + 1, path.length() - MANIFEST.length());
            var id = new TileFacePreset(ResourceLocation.fromNamespaceAndPath(namespace, name));
            try {
                var config = new TomlParser().parse(new java.io.StringReader(new String(file.getValue(), StandardCharsets.UTF_8)));
                Map<String, byte[]> tiles = new HashMap<>();
                for (String key : TILE_KEYS) {
                    byte[] png = files.get(tilePath(id, key));
                    if (!png(png)) throw new IOException("Missing or invalid " + key + ".png for " + id.id());
                    tiles.put(key, png);
                }
                Object value = config.get("name");
                if (value != null && !(value instanceof String)) throw new IOException("Invalid name for " + id.id());
                String label = value == null ? id.getSerializedName() : (String) value;
                if (label.isBlank() || label.length() > 64) throw new IOException("Invalid name for " + id.id());
                presets.put(id, new Images(label, Map.copyOf(tiles)));
            } catch (RuntimeException failure) {
                throw new IOException("Invalid tile face manifest: " + path, failure);
            }
        }
        return Map.copyOf(presets);
    }

    public static Map<TileFacePreset, Images> loadDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        Map<TileFacePreset, Images> result = new TreeMap<>(java.util.Comparator.comparing(TileFacePreset::getSerializedName));
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".zip")).sorted().toList()) {
                if (Files.size(path) > MAX_ARCHIVE_BYTES) throw new IOException("Tile face archive exceeds size limit: " + path);
                try (var input = Files.newInputStream(path)) {
                    result.putAll(read(input));
                } catch (IOException | RuntimeException failure) {
                    throw new IOException("Cannot load tile face archive: " + path, failure);
                }
            }
        }
        return Map.copyOf(result);
    }

    public static byte[] bundle(Map<TileFacePreset, Images> presets) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var entry : presets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(TileFacePreset::getSerializedName))).toList()) {
                String root = entry.getKey().id().getNamespace() + "/";
                String name = entry.getKey().id().getPath();
                var config = com.electronwill.nightconfig.core.Config.inMemory();
                config.set("name", entry.getValue().name());
                var writer = new java.io.StringWriter();
                new com.electronwill.nightconfig.toml.TomlWriter().write(config, writer);
                put(zip, root + name + MANIFEST, writer.toString().getBytes(StandardCharsets.UTF_8));
                for (String key : TILE_KEYS) put(zip, tilePath(entry.getKey(), key), entry.getValue().tiles().get(key));
            }
        }
        if (output.size() > MAX_ARCHIVE_BYTES) throw new IOException("Tile face bundle exceeds size limit");
        return output.toByteArray();
    }

    private static void put(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }
    public static String tilePath(TileFacePreset id, String key) {
        return id.id().getNamespace() + "/" + id.id().getPath() + "/tiles/" + key + ".png";
    }
    private static boolean png(byte[] bytes) {
        if (bytes == null || bytes.length < 24) return false;
        int width = java.nio.ByteBuffer.wrap(bytes, 16, 4).getInt();
        int height = java.nio.ByteBuffer.wrap(bytes, 20, 4).getInt();
        return width > 0 && width <= 2048 && height > 0 && height <= 2048
            && bytes[0] == (byte) 137 && bytes[1] == 80
            && bytes[2] == 78 && bytes[3] == 71 && bytes[4] == 13 && bytes[5] == 10
            && bytes[6] == 26 && bytes[7] == 10;
    }
}
