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
        var presets = PresetArchives.loadDirectory(directory);
        assertEquals(3, presets.size());
        assertEquals("Ink", presets.get(new TileFacePreset(ResourceLocation.parse("smoke:ink"))).name());
        assertEquals(45, presets.get(new TileFacePreset(ResourceLocation.parse("other:blue"))).tiles().size());
        assertEquals(3, PresetArchives.read(new ByteArrayInputStream(PresetArchives.bundle(presets))).size());
    }

    @Test void rejectsIncompletePreset() throws Exception {
        write(directory.resolve("incomplete.zip"), List.of("smoke:ink"), true);
        assertThrows(IOException.class, () -> PresetArchives.loadDirectory(directory));
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
