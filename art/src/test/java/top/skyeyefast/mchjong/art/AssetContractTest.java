package top.skyeyefast.mchjong.art;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.RuleSet;
import static org.junit.jupiter.api.Assertions.*;

class AssetContractTest {
    private final Path resources = Path.of(System.getProperty("mchjong.resources"));
    private final Path data = Path.of(System.getProperty("mchjong.data"));
    private final Path languages = Path.of(System.getProperty("mchjong.languages"));
    private final Path artwork = Path.of(System.getProperty("mchjong.artwork"));

    @Test
    void ordinaryItemsHaveDefinitionsForTheCurrentModelPipeline() throws Exception {
        for (String name : List.of("dice", "mahjong_dye", "creative_mahjong_dye", "red_dora_dye", "undo_dye")) {
            JsonObject definition = JsonParser.parseString(Files.readString(resources.resolve(
                "assets/mchjong/items/" + name + ".json"))).getAsJsonObject();
            assertEquals("minecraft:model", definition.getAsJsonObject("model").get("type").getAsString(), name);
            assertEquals("mchjong:item/" + name, definition.getAsJsonObject("model").get("model").getAsString(), name);
            assertTrue(Files.exists(resources.resolve("assets/mchjong/models/item/" + name + ".json")), name);
        }
    }

    @Test
    void audioEventsHaveTranslatedSubtitlesAndSeparateCustomVoices() throws Exception {
        JsonObject sounds = JsonParser.parseString(Files.readString(languages.getParent().resolve("sounds.json")))
                .getAsJsonObject();
        JsonObject translations = JsonParser.parseString(Files.readString(languages.resolve("en_us.json")))
                .getAsJsonObject();
        assertEquals(25, sounds.size());
        for (var entry : sounds.entrySet()) {
            JsonObject sound = entry.getValue().getAsJsonObject();
            assertTrue(translations.has(sound.get("subtitle").getAsString()), entry.getKey());
            if (entry.getKey().startsWith("voice."))
                assertTrue(sound.getAsJsonArray("sounds").isEmpty());
            else
                for (var choice : sound.getAsJsonArray("sounds")) {
                    assertEquals("event", choice.getAsJsonObject().get("type").getAsString());
                    assertTrue(choice.getAsJsonObject().get("name").getAsString().startsWith("minecraft:"));
                }
        }
    }

    @Test
    void translationObjectsDoNotRepeatKeys() throws Exception {
        for (String language : List.of("en_us", "ja_jp", "zh_cn", "zh_tw")) {
            try (var reader = new JsonReader(Files.newBufferedReader(languages.resolve(language + ".json")))) {
                Set<String> keys = new HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    assertTrue(keys.add(key), language + ": duplicate translation " + key);
                    reader.nextString();
                }
                reader.endObject();
                assertEquals(JsonToken.END_DOCUMENT, reader.peek(), language);
            }
        }
    }

    @Test
    void allFourLanguagesHaveIdenticalKeysAndFormatArguments() throws Exception {
        JsonObject reference = JsonParser.parseString(Files.readString(languages.resolve("en_us.json")))
                .getAsJsonObject();
        Pattern format = Pattern.compile("%(?:[0-9]+\\$)?[sd]");
        for (String language : List.of("en_us", "ja_jp", "zh_cn", "zh_tw")) {
            JsonObject translated = JsonParser.parseString(Files.readString(languages.resolve(language + ".json")))
                    .getAsJsonObject();
            assertEquals(reference.keySet(), translated.keySet(), language);
            for (String key : reference.keySet()) {
                String value = translated.get(key).getAsString();
                assertFalse(value.isBlank(), language + ": " + key);
                assertEquals(
                        format.matcher(reference.get(key).getAsString()).results().map(m -> m.group()).sorted()
                                .toList(),
                        format.matcher(value).results().map(m -> m.group()).sorted().toList(), language + ": " + key);
            }
            for (RuleSet rules : RuleSet.values())
                assertTrue(translated.has(rules.translationKey()), language + ": " + rules);
            for (Action.Type action : Action.Type.values())
                assertTrue(translated.has(new Action(action).translationKey()), language + ": " + action);
        }
    }

    @Test
    void atlasContainsEveryDistinctFaceAtItsDeclaredCoordinates() throws Exception {
        Set<String> designs = new HashSet<>();
        for (String preset : TileArtwork.PRESETS) {
            String prefix = preset.equals("kanto") ? "kanto/" : "";
            BufferedImage atlas = ImageIO
                    .read(resources.resolve("assets/mchjong/textures/" + prefix + "tiles.png").toFile());
            assertEquals(2048, atlas.getWidth());
            assertEquals(4096, atlas.getHeight());
            Set<String> hashes = new HashSet<>();
            var reference = new TileArtwork(artwork, preset);
            for (int i = 0; i < 45; i++) {
                assertFalse(Files.exists(resources.resolve("assets/mchjong/textures/tile/" + i + ".png")),
                        "Unused individual face shipped");
                BufferedImage tile = reference.face(i);
                assertEquals(256, tile.getWidth());
                assertEquals(384, tile.getHeight());
                int[] actual = atlas.getRGB(i % 8 * 256, i / 8 * 384, 256, 384, null, 0, 256);
                assertArrayEquals(tile.getRGB(0, 0, 256, 384, null, 0, 256), actual, preset + ": " + i);
                assertTrue(Arrays.stream(actual).allMatch(pixel -> pixel >>> 24 == 255), "Opaque face " + i);
                var pixels = java.nio.ByteBuffer.allocate(actual.length * Integer.BYTES);
                pixels.asIntBuffer().put(actual);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pixels.array()));
                hashes.add(hash);
                if (i == 0)
                    designs.add(hash);
                if (i == 31)
                    assertTrue(Arrays.stream(tile.getRGB(8, 8, 240, 368, null, 0, 240))
                            .allMatch(pixel -> pixel == 0xffffffff), "White dragon remains blank");
            }
            assertEquals(45, hashes.size(), "Distinct numbered, honor, red and flower faces");
            assertTrue(Arrays.stream(atlas.getRGB(2016, 4064, 32, 32, null, 0, 32))
                    .allMatch(pixel -> pixel == 0xffffffff), "Neutral material swatch");
        }
        assertEquals(2, designs.size(), "The presets must not render the same ordinary faces");
        assertFalse(Files.exists(resources.resolve("assets/mchjong/textures/tile/edge.png")));
    }

    @Test
    void faceKeysIncludeTheThreeRedFivesAndBlankWhiteDragon() throws Exception {
        assertEquals("1m", TileArtwork.tileKey(0));
        assertEquals("9m", TileArtwork.tileKey(8));
        assertEquals("1p", TileArtwork.tileKey(9));
        assertEquals("9p", TileArtwork.tileKey(17));
        assertEquals("1s", TileArtwork.tileKey(18));
        assertEquals("9s", TileArtwork.tileKey(26));
        assertEquals("1z", TileArtwork.tileKey(27));
        assertEquals("5z", TileArtwork.tileKey(31));
        assertEquals("7z", TileArtwork.tileKey(33));
        assertEquals("0m", TileArtwork.tileKey(34));
        assertEquals("0p", TileArtwork.tileKey(35));
        assertEquals("0s", TileArtwork.tileKey(36));
        assertEquals("1q", TileArtwork.tileKey(37));
        assertEquals("8q", TileArtwork.tileKey(44));
        assertEquals(45, TileArtwork.FACE_KEYS.size());
        assertThrows(IllegalArgumentException.class, () -> TileArtwork.tileKey(-1));
        assertThrows(IllegalArgumentException.class, () -> TileArtwork.tileKey(45));
        assertThrows(IllegalArgumentException.class, () -> new TileArtwork(artwork, "unknown"));
    }

    @Test
    void defaultPatternsAreTransparentAndResourcesAreComplete() throws Exception {
        String backPath = "assets/mchjong/textures/tile/back.png";
        BufferedImage solid = ImageIO.read(resources.resolve(backPath).toFile());
        assertEquals(256, solid.getWidth());
        assertEquals(384, solid.getHeight());
        for (int y = 0; y < TileArtwork.HEIGHT; y++)
            for (int x = 0; x < TileArtwork.WIDTH; x++) {
                assertEquals(0, solid.getRGB(x, y));
            }
        assertFalse(Files.exists(resources.resolve("resourcepacks")));
        var cloth = ImageIO.read(resources.resolve("assets/mchjong/textures/furniture/cloth_pattern.png").toFile());
        assertTrue(Arrays.stream(cloth.getRGB(0, 0, cloth.getWidth(), cloth.getHeight(), null, 0, cloth.getWidth())).allMatch(pixel -> pixel == 0));
        var sticks = ImageIO.read(resources.resolve("assets/mchjong/textures/point_sticks.png").toFile());
        var riichi = ImageIO.read(resources.resolve("assets/mchjong/textures/item/riichi_stick.png").toFile());
        assertArrayEquals(sticks.getRGB(0, 64, 384, 32, null, 0, 384), riichi.getRGB(0, 0, 384, 32, null, 0, 384));
        var expectedTextures = new HashSet<>(Set.of("tiles.png", "tile_glyphs.png", "back.png", "point_sticks.png",
                "plain.png", "cloth_pattern.png", "riichi_stick.png", "mahjong_dye.png", "creative_mahjong_dye.png", "red_dora_dye.png", "undo_dye.png"));
        FurnitureArtwork.textures().keySet().forEach(name -> expectedTextures.add(name + ".png"));
        TileMaterialArtwork.textures().keySet().forEach(name -> expectedTextures.add(name + ".png"));
        for (int face = 1; face <= 6; face++) expectedTextures.add("dice_" + face + ".png");
        try (var textures = Files.walk(resources.resolve("assets/mchjong/textures"))) {
            assertEquals(expectedTextures, textures.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".png")).map(file -> file.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet()), "Only textures referenced at runtime should ship");
        }
        assertTrue(Files.readString(resources.resolve("META-INF/licenses/kanto-source.json")).contains("kanto"));
        assertFalse(
                Files.readString(resources.resolve("META-INF/licenses/kanto-source.json")).contains("unauthorized"));
        assertTrue(Files.readString(resources.resolve("META-INF/licenses/kansai-source.json")).contains("lietxia"));
    }

    @Test
    void modelsUseFewCuboidsAndOnlyAvailableTextures() throws Exception {
        for (String name : List.of("mahjong_table", "automatic_mahjong_table", "mahjong_stool")) {
            JsonObject model = JsonParser
                    .parseString(Files.readString(resources.resolve("assets/mchjong/models/block/" + name + ".json")))
                    .getAsJsonObject();
            assertFalse(model.getAsJsonArray("elements").isEmpty(), name + " must cast terrain shadows");
            assertTrue(model.getAsJsonArray("elements").size() <= 16);
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                String[] id = texture.getValue().getAsString().split(":", 2);
                assertTrue(
                        id[0].equals("minecraft") || Files
                                .isRegularFile(resources.resolve("assets/" + id[0] + "/textures/" + id[1] + ".png")),
                        texture.getKey());
            }
            for (var element : model.getAsJsonArray("elements")) {
                assertEquals(6, element.getAsJsonObject().getAsJsonObject("faces").size(), name);
                for (String edge : List.of("from", "to"))
                    for (var coordinate : element.getAsJsonObject().getAsJsonArray(edge))
                        assertTrue(coordinate.getAsDouble() >= -16 && coordinate.getAsDouble() <= 32,
                                "Minecraft model bounds");
            }
            assertTrue(Files.isRegularFile(resources.resolve("assets/mchjong/blockstates/" + name + ".json")));
            if (!name.equals("automatic_mahjong_table"))
                assertTrue(Files.isRegularFile(data.resolve("data/mchjong/recipe/" + name + ".json")));
            assertTrue(Files.isRegularFile(data.resolve("data/mchjong/loot_table/blocks/" + name + ".json")));
        }
    }

    @Test
    void diceKeepSixReadableFacesAndTraditionalPipColors() throws Exception {
        for (int face = 1; face <= 6; face++) {
            var image = ImageIO.read(resources.resolve("assets/mchjong/textures/item/dice_" + face + ".png").toFile());
            assertEquals(32, image.getWidth());
            assertEquals(32, image.getHeight());
            var marks = new HashSet<Integer>();
            boolean red = face == 1 || face == 4;
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                int color = image.getRGB(x, y);
                assertEquals(255, color >>> 24);
                if ((color >> 8 & 255) < 150) {
                    assertEquals(red, (color >> 16 & 255) > (color & 255), "Pip hue on face " + face);
                    marks.add(y * 32 + x);
                }
            }
            int count = 0;
            var pending = new ArrayDeque<Integer>();
            while (!marks.isEmpty()) {
                int start = marks.iterator().next();
                marks.remove(start);
                pending.add(start);
                count++;
                while (!pending.isEmpty()) {
                    int pixel = pending.remove();
                    for (int next : new int[]{pixel - 1, pixel + 1, pixel - 32, pixel + 32})
                        if (marks.remove(next)) pending.add(next);
                }
            }
            assertEquals(face, count, "Separate pips on face " + face);
        }
    }

    @Test
    void generationIsByteForByteReproducible(@TempDir Path second) throws Exception {
        GenerateAssets.main(new String[] { second.toString(), artwork.toString() });
        assertFalse(Files.exists(second.resolve("data")), "Artwork must not generate server data");
        GenerateData.main(new String[] { second.resolve("server").toString() });
        try (var files = Files.walk(data)) {
            for (Path file : files.filter(Files::isRegularFile).toList())
                assertArrayEquals(Files.readAllBytes(file),
                        Files.readAllBytes(second.resolve("server").resolve(data.relativize(file))));
        }
        try (var files = Files.walk(resources)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path counterpart = second.resolve(resources.relativize(file));
                assertTrue(Files.isRegularFile(counterpart), file.toString());
                assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(counterpart), file.toString());
            }
        }
    }
}
