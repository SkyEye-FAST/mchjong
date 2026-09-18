package top.skyeyefast.mchjong.art;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlowerTileArtworkTest {
    private final Path resources = Path.of(System.getProperty("mchjong.resources"));

    @Test void unicodeOutlinesFollowThePhysicalFlowerNumbers() {
        assertEquals(List.of(0x1f026, 0x1f027, 0x1f028, 0x1f029, 0x1f022, 0x1f023, 0x1f024, 0x1f025),
            java.util.stream.IntStream.range(0, 8).mapToObj(FlowerTileArtwork::codePoint).toList());
        assertThrows(IllegalArgumentException.class, () -> FlowerTileArtwork.codePoint(-1));
        assertThrows(IllegalArgumentException.class, () -> FlowerTileArtwork.codePoint(8));
    }

    @Test void everyFlowerHasThreeInksDistinctEngravingAndClearMargins() throws Exception {
        var source = new FlowerTileArtwork(Path.of(System.getProperty("mchjong.flowerArtwork")));
        var atlas = ImageIO.read(resources.resolve("assets/mchjong/textures/tile_glyphs.png").toFile());
        var silhouettes = new HashSet<Integer>();
        for (int flower = 0; flower < 8; flower++) {
            var expected = new BufferedImage(256, 384, BufferedImage.TYPE_INT_ARGB);
            var g = expected.createGraphics();
            try { source.draw(g, flower, 256, 384); } finally { g.dispose(); }
            int red = 0, green = 0, blue = 0, printed = 0;
            int[] alpha = new int[256 * 384];
            for (int y = 0; y < 384; y++) for (int x = 0; x < 256; x++) {
                int pixel = expected.getRGB(x, y);
                int opacity = pixel >>> 24, color = pixel & 0xffffff;
                alpha[y * 256 + x] = opacity;
                int face = flower + 37;
                assertEquals(pixel, atlas.getRGB(face % 8 * 256 + x, face / 8 * 384 + y));
                if (x < 12 || x >= 244 || y < 30 || y >= 354)
                    assertEquals(0, opacity, "Frame-free margin: " + flower + " at " + x + "," + y);
                if (opacity > 0) printed++;
                if (opacity == 255) {
                    if (color == FlowerTileArtwork.RED) red++;
                    if (color == FlowerTileArtwork.GREEN) green++;
                    if (color == FlowerTileArtwork.BLUE) blue++;
                }
            }
            assertTrue(printed > 4000 && printed < 35000, "Engraving weight and negative space: " + flower);
            assertTrue(red > 100 && green > 100 && blue > 100, "Labels and botanical ink: " + flower);
            assertTrue(silhouettes.add(Arrays.hashCode(alpha)), "Distinct flower engraving: " + flower);
        }
    }

    @Test void sourceLicenseAndAdaptationNoticeShipWithOnlyRasterizedArtwork() throws Exception {
        var license = Files.readString(resources.resolve("META-INF/licenses/I.Mahjong-LICENSE.txt"));
        assertTrue(license.contains("Unlimited permission is granted"));
        var notice = Files.readString(resources.resolve("META-INF/licenses/I.Mahjong-NOTICE.txt"));
        assertTrue(notice.contains(FlowerTileArtwork.REVISION));
        assertTrue(notice.contains("Gutenberg Labo"));
        assertTrue(notice.contains("red, green and blue"));
        try (var files = Files.walk(resources)) {
            assertTrue(files.filter(Files::isRegularFile).noneMatch(file ->
                file.toString().matches("(?i).*\\.(otf|ttf|zip)$")), "Source fonts and archives remain build dependencies");
        }
    }

    @Test void petalTipsAndCalligraphyKeepTheirWholeContourColor() throws Exception {
        var atlas = ImageIO.read(resources.resolve("assets/mchjong/textures/tile_glyphs.png").toFile());
        // Registered source-coordinate regions clear of neighboring stems. These extremities were
        // cut into green fragments by broad elliptical masks or rectangular character crops.
        int[][] regions = {
            {3, 175, 171, 225, 190, FlowerTileArtwork.RED},
            {2, 135, 50, 149, 100, FlowerTileArtwork.RED},
            {0, 141, 100, 153, 134, FlowerTileArtwork.RED},
            {1, 60, 303, 85, 312, FlowerTileArtwork.RED},
            {4, 18, 24, 116, 144, FlowerTileArtwork.BLUE}
        };
        for (var region : regions) {
            int face = region[0] + 37, printed = 0;
            int top = (int) Math.ceil(64.0 / 3 + region[2] * 8.0 / 9);
            int bottom = (int) Math.floor(64.0 / 3 + region[4] * 8.0 / 9);
            for (int y = top; y < bottom; y++) for (int x = region[1]; x < region[3]; x++) {
                int pixel = atlas.getRGB(face % 8 * 256 + x, face / 8 * 384 + y);
                if (pixel >>> 24 < 255) continue;
                assertEquals(region[5], pixel & 0xffffff, "Whole engraving contour: flower " + region[0]);
                printed++;
            }
            assertTrue(printed > 3, "Color probe must intersect the engraving: flower " + region[0]);
        }
    }
}
