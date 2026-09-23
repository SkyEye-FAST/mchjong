package top.skyeyefast.mchjong.art;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FurnitureArtworkTest {
    private final Path textures = Path.of(System.getProperty("mchjong.resources"), "assets/mchjong/textures/furniture");

    @Test void everyWoodAndSurfaceHasItsOwnOpaquePixelTexture() throws Exception {
        assertEquals(11, FurnitureArtwork.WOODS.size());
        assertEquals(15, FurnitureArtwork.textures().size());
        var signatures = new HashSet<Integer>();
        for (var entry : FurnitureArtwork.textures().entrySet()) {
            var actual = ImageIO.read(textures.resolve(entry.getKey() + ".png").toFile());
            assertNotNull(actual, entry.getKey());
            assertEquals(16, actual.getWidth());
            assertEquals(16, actual.getHeight());
            var colors = new HashSet<Integer>();
            int signature = 1;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int color = actual.getRGB(x, y);
                assertEquals(255, color >>> 24, entry.getKey());
                assertEquals(entry.getValue().getRGB(x, y), color);
                colors.add(color);
                signature = 31 * signature + color;
            }
            assertTrue(colors.size() >= 2, "A material must contain original surface detail: " + entry.getKey());
            assertTrue(colors.size() <= 5, "A restrained pixel palette: " + entry.getKey());
            int contrast = colors.stream().mapToInt(color -> color & 255).max().orElseThrow()
                - colors.stream().mapToInt(color -> color & 255).min().orElseThrow();
            assertTrue(contrast >= (entry.getKey().equals("felt") ? 6 : entry.getKey().equals("edge") ? 8 : 15),
                "Visible material relief: " + entry.getKey());
            if (entry.getKey().equals("felt")) assertTrue(contrast <= 10, "Cloth must stay quiet behind the tiles");
            var metadata = com.google.gson.JsonParser.parseString(Files.readString(textures.resolve(entry.getKey() + ".png.mcmeta")))
                .getAsJsonObject().getAsJsonObject("texture");
            assertFalse(metadata.get("blur").getAsBoolean(), "Crisp furniture pixels");
            assertFalse(metadata.get("clamp").getAsBoolean(), "Continuous local-space repeats");
            assertTrue(signatures.add(signature), "Duplicate material: " + entry.getKey());
        }
    }

    @Test void customParticleIsStitchedAndWhiteTileIconsUseFrontLighting() throws Exception {
        Path resources = Path.of(System.getProperty("mchjong.resources"));
        var atlas = com.google.gson.JsonParser.parseString(Files.readString(resources.resolve("assets/minecraft/atlases/blocks.json")))
            .getAsJsonObject().getAsJsonArray("sources");
        assertEquals(1, atlas.size());
        assertEquals("minecraft:single", atlas.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("mchjong:furniture/wood_oak", atlas.get(0).getAsJsonObject().get("resource").getAsString());
        var tile = com.google.gson.JsonParser.parseString(Files.readString(resources.resolve("assets/mchjong/models/item/mahjong_tile.json")))
            .getAsJsonObject();
        assertEquals("front", tile.get("gui_light").getAsString());
        var guiRotation = tile.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("rotation");
        assertTrue(guiRotation.get(0).getAsFloat() > 0);
        assertTrue(guiRotation.get(1).getAsFloat() > 0, "Inventory view exposes tile depth");
    }

    @Test void tileBodiesHaveDistinctNeutralReliefAndHeldFacesTurnTowardBothHands() throws Exception {
        var signatures = new HashSet<Integer>();
        for (var entry : TileMaterialArtwork.textures().entrySet()) {
            var texture = ImageIO.read(textures.getParent().resolve("tile_material/" + entry.getKey() + ".png").toFile());
            assertEquals(16, texture.getWidth());
            assertEquals(16, texture.getHeight());
            int min = 255, max = 0, signature = 1;
            var colors = new HashSet<Integer>();
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int color = texture.getRGB(x, y), v = color & 255;
                colors.add(color);
                assertEquals(v, color >> 8 & 255);
                assertEquals(v, color >> 16 & 255);
                assertEquals(255, color >>> 24);
                min = Math.min(min, v); max = Math.max(max, v);
                signature = 31 * signature + color;
            }
            assertTrue(max - min >= 12, entry.getKey());
            assertTrue(colors.size() <= 5, "Discrete material palette: " + entry.getKey());
            assertTrue(signatures.add(signature));
        }
        var model = com.google.gson.JsonParser.parseString(Files.readString(textures.getParent().getParent()
            .resolve("models/item/mahjong_tile.json"))).getAsJsonObject().getAsJsonObject("display");
        var right = model.getAsJsonObject("firstperson_righthand");
        assertEquals(right, model.getAsJsonObject("firstperson_lefthand"), "Vanilla mirrors the left-hand transform");
        assertTrue(right.getAsJsonArray("rotation").get(1).getAsFloat() < 0);
        assertTrue(right.getAsJsonArray("rotation").get(2).getAsFloat() < 0, "Tile top leans right while the face turns left");
        assertTrue(right.getAsJsonArray("translation").get(0).getAsFloat() >= 0, "Keep the grip out of the screen center");
        assertTrue(right.getAsJsonArray("translation").get(1).getAsFloat() > 0);
        assertTrue(right.getAsJsonArray("scale").get(0).getAsFloat() <= .45);
    }

    @Test void heldSticksExposeTheirMarkedTopAndExtendUpwardIntoTheViewport() throws Exception {
        var item = com.google.gson.JsonParser.parseString(Files.readString(textures.getParent().getParent()
            .resolve("models/item/point_stick.json"))).getAsJsonObject();
        assertEquals("minecraft:builtin/entity", item.get("parent").getAsString(), "Keep the physical cuboid renderer");
        var display = item.getAsJsonObject("display");
        var right = display.getAsJsonObject("firstperson_righthand");
        assertEquals(right, display.getAsJsonObject("firstperson_lefthand"));
        assertTrue(right.getAsJsonArray("rotation").get(0).getAsFloat() > 45, "Expose printed top");
        assertTrue(right.getAsJsonArray("rotation").get(2).getAsFloat() > 0, "Free end rises toward the right like a sword");
        assertTrue(right.getAsJsonArray("translation").get(0).getAsFloat() >= 0, "Keep the grip out of the screen center");
        assertTrue(right.getAsJsonArray("translation").get(1).getAsFloat() <= 6, "Keep the hand near its native height");
        assertTrue(right.getAsJsonArray("translation").get(2).getAsFloat() < 0, "Allow narrow-window clearance");
    }

    @Test void fabricIsNeutralSoEveryDyeKeepsItsHue() throws Exception {
        var felt = ImageIO.read(textures.resolve("felt.png").toFile());
        for (int y = 0; y < felt.getHeight(); y++) for (int x = 0; x < felt.getWidth(); x++) {
            int rgb = felt.getRGB(x, y);
            assertEquals(rgb & 255, rgb >> 8 & 255);
            assertEquals(rgb & 255, rgb >> 16 & 255);
        }
    }

    @Test void pointStickStripsHaveTheReferenceColorsAndSeparateRoundMarks() throws Exception {
        var image = ImageIO.read(textures.getParent().resolve("point_sticks.png").toFile());
        assertEquals(384, image.getWidth());
        assertEquals(192, image.getHeight());
        int[] colors = {0xf4f4ef, 0xf4f4ef, 0x007cbe, 0xefc400, 0xd81427, 0x202326};
        int[] dots = {0, 8, 1, 5, 9, 9};
        for (int row = 0; row < 6; row++) {
            assertEquals(0xff000000 | colors[row], image.getRGB(0, row * 32 + 16));
            int ink = 0xff000000 | (row == 1 ? 0xb5bbba : 0xe9eeed);
            var pixels = new HashSet<Integer>();
            for (int y = row * 32; y < (row + 1) * 32; y++) for (int x = 0; x < 384; x++) {
                assertEquals(255, image.getRGB(x, y) >>> 24);
                if (image.getRGB(x, y) == ink) pixels.add(y * 384 + x);
            }
            int components = 0;
            var pending = new java.util.ArrayDeque<Integer>();
            while (!pixels.isEmpty()) {
                int first = pixels.iterator().next();
                pixels.remove(first);
                pending.add(first);
                components++;
                while (!pending.isEmpty()) {
                    int pixel = pending.remove();
                    for (int adjacent : new int[]{pixel - 1, pixel + 1, pixel - 384, pixel + 384})
                        if (pixels.remove(adjacent)) pending.add(adjacent);
                }
            }
            assertEquals(dots[row], components, "Separate printed dots in strip " + row);
        }
    }
}
