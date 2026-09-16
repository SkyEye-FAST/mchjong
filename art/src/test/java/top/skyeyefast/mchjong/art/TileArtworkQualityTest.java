package top.skyeyefast.mchjong.art;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TileArtworkQualityTest {
    private final Path resources = Path.of(System.getProperty("mchjong.resources"));

    @Test void vectorFacesRetainHighResolutionAndAntialiasedEdges() throws Exception {
        // A single-color source glyph should still have many edge-coverage shades.
        BufferedImage tile = ImageIO.read(resources.resolve("assets/mchjong/textures/tile/0.png").toFile());
        assertEquals(256, tile.getWidth());
        assertEquals(384, tile.getHeight());
        Set<Integer> shades = new HashSet<>();
        for (int y = 16; y < tile.getHeight() - 16; y++)
            for (int x = 16; x < tile.getWidth() - 16; x++) shades.add(tile.getRGB(x, y));
        assertTrue(shades.size() > 64, "SVG outlines must not regress to hard-edged pixel art");
    }

    @Test void highResolutionTexturesUseLinearFilteringAndClamping() throws Exception {
        for (String texture : new String[]{"tiles", "tile/back"}) {
            var metadata = JsonParser.parseString(Files.readString(resources.resolve(
                "assets/mchjong/textures/" + texture + ".png.mcmeta"))).getAsJsonObject().getAsJsonObject("texture");
            assertTrue(metadata.get("blur").getAsBoolean(), "Smooth oblique and UI sampling");
            assertTrue(metadata.get("clamp").getAsBoolean(), "No opposite-edge wrapping");
        }
    }
}
