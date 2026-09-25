package top.skyeyefast.mchjong.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.skyeyefast.mchjong.item.TileFacePreset;
import net.minecraft.resources.ResourceLocation;

class PresetArchivesTest {
    @TempDir Path directory;
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+lm5kAAAAASUVORK5CYII=");

    @Test void loadsSeveralZipsAndSeveralPresetsPerZip() throws Exception {
        write(directory.resolve("first.zip"), List.of("smoke:ink", "smoke:paper"), false);
        write(directory.resolve("second.zip"), List.of("other:blue"), false);
        var presets = PresetArchives.loadDirectory(directory, PresetArchives.Kind.FACE);
        assertEquals(3, presets.faces().size());
        assertEquals("Ink", presets.faces().get(new TileFacePreset(ResourceLocation.parse("smoke:ink"))).name());
        assertEquals(45, presets.faces().get(new TileFacePreset(ResourceLocation.parse("other:blue"))).tiles().size());
        assertEquals(3, PresetArchives.read(new ByteArrayInputStream(PresetArchives.bundle(presets, PresetArchives.Kind.FACE)),
            PresetArchives.Kind.FACE).faces().size());
    }

    @Test void rejectsIncompletePreset() throws Exception {
        write(directory.resolve("incomplete.zip"), List.of("smoke:ink"), true);
        assertThrows(IOException.class, () -> PresetArchives.loadDirectory(directory, PresetArchives.Kind.FACE));
    }

    @Test void backArchivesUseTheirOwnCategoryAndRejectMixedFiles() throws Exception {
        byte[] back = PNG.clone();
        java.nio.ByteBuffer.wrap(back, 16, 4).putInt(256);
        java.nio.ByteBuffer.wrap(back, 20, 4).putInt(384);
        Path path = directory.resolve("backs.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            put(zip, "smoke/ink/preset.toml", "name = \"Ink back\"\n".getBytes(StandardCharsets.UTF_8));
            put(zip, "smoke/ink/back.png", back);
        }
        var loaded = PresetArchives.loadDirectory(directory, PresetArchives.Kind.BACK);
        assertEquals(1, loaded.backs().size());
        assertEquals("Ink back", loaded.backs().get(ResourceLocation.parse("smoke:ink")).name());
        assertEquals(1, PresetArchives.read(new ByteArrayInputStream(PresetArchives.bundle(loaded, PresetArchives.Kind.BACK)),
            PresetArchives.Kind.BACK).backs().size());
        assertThrows(IOException.class, () -> PresetArchives.loadDirectory(directory, PresetArchives.Kind.FACE));
    }

    @Test void stickArchivesBoundTheModelToALongBar() throws Exception {
        byte[] image = PNG.clone();
        java.nio.ByteBuffer.wrap(image, 16, 4).putInt(384);
        java.nio.ByteBuffer.wrap(image, 20, 4).putInt(32);
        Path path = directory.resolve("sticks.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
            put(zip, "smoke/wood/preset.toml", "name = \"Wood\"\nlength = 12\nwidth = 1\nheight = 0.5\n".getBytes(StandardCharsets.UTF_8));
            put(zip, "smoke/wood/stick.png", image);
            put(zip, "smoke/white/preset.toml", "name = \"White\"\nlength = 8\nwidth = 1.5\nheight = 1\n".getBytes(StandardCharsets.UTF_8));
            put(zip, "smoke/white/stick.png", image);
        }
        var presets = PresetArchives.loadDirectory(directory, PresetArchives.Kind.STICK);
        assertEquals(2, presets.sticks().size());
        assertEquals(12, presets.sticks().get(ResourceLocation.parse("smoke:wood")).length());
        assertEquals(2, PresetArchives.read(new ByteArrayInputStream(PresetArchives.bundle(presets, PresetArchives.Kind.STICK)),
            PresetArchives.Kind.STICK).sticks().size());
        assertThrows(IOException.class, () -> PresetArchives.loadDirectory(directory, PresetArchives.Kind.BACK));
        try (var zip = new ZipOutputStream(Files.newOutputStream(directory.resolve("invalid.zip")))) {
            put(zip, "smoke/short/preset.toml", "name = \"Short\"\nlength = 8\nwidth = 2\nheight = 0.5\n".getBytes(StandardCharsets.UTF_8));
            put(zip, "smoke/short/stick.png", image);
        }
        assertThrows(IOException.class, () -> PresetArchives.loadDirectory(directory, PresetArchives.Kind.STICK));
    }

    private static void write(Path target, List<String> ids, boolean omitLast) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(target))) {
            for (String raw : ids) {
                var id = new TileFacePreset(ResourceLocation.parse(raw));
                String manifest = id.id().getNamespace() + "/" + id.id().getPath() + "/preset.toml";
                put(zip, manifest, ("name = \"" + (raw.equals("smoke:ink") ? "Ink" : raw) + "\"\n")
                    .getBytes(StandardCharsets.UTF_8));
                for (int i = 0; i < PresetArchives.TILE_KEYS.size() - (omitLast ? 1 : 0); i++)
                    put(zip, PresetArchives.tilePath(id, PresetArchives.TILE_KEYS.get(i)), PNG);
            }
        }
    }

    private static void put(ZipOutputStream zip, String path, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(path)); zip.write(bytes); zip.closeEntry();
    }
}
