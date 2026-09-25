package top.skyeyefast.mchjong.config;

import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
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

/** Bounded ZIP format shared by local and server cosmetic config directories. */
public final class PresetArchives {
    public static final java.util.List<String> TILE_KEYS = java.util.List.of(
        "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m",
        "1p", "2p", "3p", "4p", "5p", "6p", "7p", "8p", "9p",
        "1s", "2s", "3s", "4s", "5s", "6s", "7s", "8s", "9s",
        "1z", "2z", "3z", "4z", "5z", "6z", "7z",
        "0m", "0p", "0s", "1q", "2q", "3q", "4q", "5q", "6q", "7q", "8q");
    public record Images(String name, Map<String, byte[]> tiles) {}
    public record Back(String name, byte[] image) {}
    public record Stick(String name, byte[] image, float length, float width, float height) {}
    public record Collection(Map<TileFacePreset, Images> faces, Map<ResourceLocation, Back> backs,
                             Map<ResourceLocation, Stick> sticks) {}
    public enum Kind { FACE, BACK, STICK }
    public static final int MAX_ARCHIVE_BYTES = 32 * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    private PresetArchives() {}

    public static Collection read(InputStream input, Kind kind) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        long total = 0;
        int entries = 0;
        try (var zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (++entries > 4096) throw new IOException("Too many preset archive entries");
                if (entry.isDirectory()) continue;
                String path = entry.getName();
                if (path.startsWith("/") || path.contains("\\") || path.contains("../") || path.contains("/..")
                    || path.contains("//")) throw new IOException("Invalid preset archive path: " + path);
                byte[] bytes = zip.readNBytes(MAX_ENTRY_BYTES + 1);
                total += bytes.length;
                if (bytes.length > MAX_ENTRY_BYTES || total > MAX_ARCHIVE_BYTES)
                    throw new IOException("Preset archive exceeds size limit");
                if (files.putIfAbsent(path, bytes) != null) throw new IOException("Duplicate preset archive path: " + path);
            }
        }
        Map<TileFacePreset, Images> faces = new HashMap<>();
        Map<ResourceLocation, Back> backs = new HashMap<>();
        Map<ResourceLocation, Stick> sticks = new HashMap<>();
        var expected = new java.util.HashSet<String>();
        for (var file : files.entrySet()) {
            String path = file.getKey();
            if (!path.endsWith("/preset.toml")) continue;
            expected.add(path);
            String[] parts = path.split("/");
            if (parts.length != 3) throw new IOException("Invalid preset manifest path: " + path);
            try {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
                var config = new TomlParser().parse(new java.io.StringReader(new String(file.getValue(), StandardCharsets.UTF_8)));
                Object name = config.get("name");
                String label = name == null ? id.toString() : name instanceof String value ? value : null;
                if (label == null || label.isBlank() || label.length() > 64) throw new IOException("Invalid name for " + id);
                if (kind == Kind.FACE) {
                    var preset = new TileFacePreset(id);
                    Map<String, byte[]> tiles = new HashMap<>();
                    for (String key : TILE_KEYS) {
                        byte[] png = files.get(tilePath(preset, key));
                        if (!png(png, false)) throw new IOException("Missing or invalid " + key + ".png for " + id);
                        tiles.put(key, png);
                        expected.add(tilePath(preset, key));
                    }
                    faces.put(preset, new Images(label, Map.copyOf(tiles)));
                } else if (kind == Kind.BACK) {
                    byte[] image = files.get(root(id) + "back.png");
                    if (!png(image, true)) throw new IOException("Missing or invalid back.png for " + id);
                    expected.add(root(id) + "back.png");
                    backs.put(id, new Back(label, image));
                } else {
                    byte[] image = files.get(root(id) + "stick.png");
                    if (!png(image, 384, 32)) throw new IOException("Missing or invalid stick.png for " + id);
                    float length = dimension(config.get("length"), 8, 16, "length");
                    float width = dimension(config.get("width"), .25f, 2, "width");
                    float height = dimension(config.get("height"), .125f, 1, "height");
                    if (length < width * 5 || length < height * 5)
                        throw new IOException("Riichi stick must be long and narrow: " + id);
                    expected.add(root(id) + "stick.png");
                    sticks.put(id, new Stick(label, image, length, width, height));
                }
            } catch (RuntimeException failure) {
                throw new IOException("Invalid preset manifest: " + path, failure);
            }
        }
        if (!files.keySet().equals(expected)) throw new IOException("Archive contains files outside " + kind + " presets");
        return new Collection(Map.copyOf(faces), Map.copyOf(backs), Map.copyOf(sticks));
    }

    public static Collection loadDirectory(Path directory, Kind kind) throws IOException {
        Files.createDirectories(directory);
        Map<TileFacePreset, Images> faces = new TreeMap<>(java.util.Comparator.comparing(TileFacePreset::getSerializedName));
        Map<ResourceLocation, Back> backs = new TreeMap<>(java.util.Comparator.comparing(ResourceLocation::toString));
        Map<ResourceLocation, Stick> sticks = new TreeMap<>(java.util.Comparator.comparing(ResourceLocation::toString));
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".zip")).sorted().toList()) {
                if (Files.size(path) > MAX_ARCHIVE_BYTES) throw new IOException("Preset archive exceeds size limit: " + path);
                try (var input = Files.newInputStream(path)) {
                    var archive = read(input, kind);
                    for (var entry : archive.faces().entrySet())
                        if (faces.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                            throw new IOException("Duplicate face preset: " + entry.getKey().id());
                    for (var entry : archive.backs().entrySet())
                        if (backs.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                            throw new IOException("Duplicate back preset: " + entry.getKey());
                    for (var entry : archive.sticks().entrySet())
                        if (sticks.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                            throw new IOException("Duplicate stick preset: " + entry.getKey());
                } catch (IOException | RuntimeException failure) {
                    throw new IOException("Cannot load preset archive: " + path, failure);
                }
            }
        }
        return new Collection(Map.copyOf(faces), Map.copyOf(backs), Map.copyOf(sticks));
    }

    public static byte[] bundle(Collection presets, Kind kind) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            if (kind == Kind.FACE) for (var entry : presets.faces().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(TileFacePreset::getSerializedName))).toList()) {
                var preset = entry.getKey();
                put(zip, root(preset.id()) + "preset.toml", manifest(entry.getValue().name()));
                for (String key : TILE_KEYS) put(zip, tilePath(preset, key), entry.getValue().tiles().get(key));
            }
            if (kind == Kind.BACK) for (var entry : presets.backs().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString))).toList()) {
                put(zip, root(entry.getKey()) + "preset.toml", manifest(entry.getValue().name()));
                put(zip, root(entry.getKey()) + "back.png", entry.getValue().image());
            }
            if (kind == Kind.STICK) for (var entry : presets.sticks().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(ResourceLocation::toString))).toList()) {
                var stick = entry.getValue();
                put(zip, root(entry.getKey()) + "preset.toml",
                    manifest(stick.name(), stick.length(), stick.width(), stick.height()));
                put(zip, root(entry.getKey()) + "stick.png", stick.image());
            }
        }
        if (output.size() > MAX_ARCHIVE_BYTES) throw new IOException("Preset bundle exceeds size limit");
        return output.toByteArray();
    }

    private static byte[] manifest(String name) {
        var config = com.electronwill.nightconfig.core.Config.inMemory();
        config.set("name", name);
        var writer = new java.io.StringWriter();
        new TomlWriter().write(config, writer);
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static byte[] manifest(String name, float length, float width, float height) {
        var config = com.electronwill.nightconfig.core.Config.inMemory();
        config.set("name", name);
        config.set("length", length);
        config.set("width", width);
        config.set("height", height);
        var writer = new java.io.StringWriter();
        new TomlWriter().write(config, writer);
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void put(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }
    private static String root(ResourceLocation id) { return id.getNamespace() + "/" + id.getPath() + "/"; }
    public static String tilePath(TileFacePreset id, String key) { return root(id.id()) + "tiles/" + key + ".png"; }
    private static boolean png(byte[] bytes, boolean back) {
        if (bytes == null || bytes.length < 24) return false;
        int width = ByteBuffer.wrap(bytes, 16, 4).getInt();
        int height = ByteBuffer.wrap(bytes, 20, 4).getInt();
        return width > 0 && width <= 2048 && height > 0 && height <= 2048
            && (!back || width == 256 && height == 384)
            && bytes[0] == (byte) 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71
            && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
    }
    private static boolean png(byte[] bytes, int width, int height) {
        return png(bytes, false) && ByteBuffer.wrap(bytes, 16, 4).getInt() == width
            && ByteBuffer.wrap(bytes, 20, 4).getInt() == height;
    }
    private static float dimension(Object value, float min, float max, String name) throws IOException {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
            || number.doubleValue() < min || number.doubleValue() > max)
            throw new IOException("Invalid riichi stick " + name);
        return number.floatValue();
    }
}
