package top.skyeyefast.mchjong.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PresetDirectoryTest {
    @TempDir Path root;

    @Test void additionsReplacementsAndRemovalWaitForStableZipMetadata() throws Exception {
        var monitor = new PresetDirectory(root);
        Path voices = Files.createDirectories(root.resolve("voices"));
        Path archive = voices.resolve("test.zip");
        Files.writeString(archive, "partial");
        assertFalse(monitor.changed());
        Files.writeString(archive, "completed archive");
        assertFalse(monitor.changed());
        assertTrue(monitor.changed());
        assertFalse(monitor.changed());
        Files.writeString(voices.resolve("ignored.tmp"), "temporary");
        assertFalse(monitor.changed());
        Files.delete(archive);
        assertFalse(monitor.changed());
        assertTrue(monitor.changed());
        assertFalse(monitor.changed());
    }
}
