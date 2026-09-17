package top.skyeyefast.mchjong.art;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
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
            assertTrue(contrast >= (entry.getKey().equals("edge") ? 8 : 15), "Visible material relief: " + entry.getKey());
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
    }

    @Test void tileBodiesHaveDistinctNeutralReliefAndHeldFacesTurnTowardBothHands() throws Exception {
        var signatures = new HashSet<Integer>();
        for (var entry : TileMaterialArtwork.textures().entrySet()) {
            var texture = ImageIO.read(textures.getParent().resolve("tile_material/" + entry.getKey() + ".png").toFile());
            int min = 255, max = 0, signature = 1;
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                int color = texture.getRGB(x, y), v = color & 255;
                assertEquals(v, color >> 8 & 255);
                assertEquals(v, color >> 16 & 255);
                assertEquals(255, color >>> 24);
                min = Math.min(min, v); max = Math.max(max, v);
                signature = 31 * signature + color;
            }
            assertTrue(max - min >= 12, entry.getKey());
            assertTrue(signatures.add(signature));
        }
        var model = com.google.gson.JsonParser.parseString(Files.readString(textures.getParent().getParent()
            .resolve("models/item/mahjong_tile.json"))).getAsJsonObject().getAsJsonObject("display");
        var right = model.getAsJsonObject("firstperson_righthand");
        assertEquals(right, model.getAsJsonObject("firstperson_lefthand"), "Vanilla mirrors the left-hand transform");
        assertTrue(right.getAsJsonArray("rotation").get(1).getAsFloat() < 0);
        assertTrue(right.getAsJsonArray("translation").get(1).getAsFloat() > 0);
        assertTrue(right.getAsJsonArray("scale").get(0).getAsFloat() <= .45);
    }

    @Test void fabricIsNeutralSoEveryDyeKeepsItsHue() throws Exception {
        var felt = ImageIO.read(textures.resolve("felt.png").toFile());
        for (int y = 0; y < felt.getHeight(); y++) for (int x = 0; x < felt.getWidth(); x++) {
            int rgb = felt.getRGB(x, y);
            assertEquals(rgb & 255, rgb >> 8 & 255);
            assertEquals(rgb & 255, rgb >> 16 & 255);
        }
    }

    @Test void furnitureDoesNotReferenceVanillaTextureSurrogates() throws Exception {
        Path root = Path.of(System.getProperty("mchjong.sourceRoot"));
        String renderer = Files.readString(root.resolve("common/src/main/java/top/skyeyefast/mchjong/client/FurnitureMesh.java"));
        for (String retired : List.of("textures/block/", "_planks", "_wool", "iron_block", "copper_block"))
            assertFalse(renderer.contains(retired), retired);
        for (String wood : FurnitureArtwork.WOODS.keySet()) {
            String definition = Files.readString(root.resolve("common/src/main/java/top/skyeyefast/mchjong/item/FurnitureWood.java"));
            assertTrue(definition.contains(wood.toUpperCase(java.util.Locale.ROOT)), wood);
        }
    }
}
