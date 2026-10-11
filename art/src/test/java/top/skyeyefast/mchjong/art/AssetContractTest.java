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
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.RiichiPreset;
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
        assertEquals(25 + top.skyeyefast.mchjong.engine.ScoreAnnouncements.SUBTITLES.size(), sounds.size());
        top.skyeyefast.mchjong.engine.ScoreAnnouncements.SUBTITLES.forEach((event, subtitle) ->
            assertEquals(subtitle, sounds.getAsJsonObject("voice." + event).get("subtitle").getAsString()));
        for (var entry : sounds.entrySet()) {
            JsonObject sound = entry.getValue().getAsJsonObject();
            assertTrue(translations.has(sound.get("subtitle").getAsString()), entry.getKey());
            if (entry.getKey().startsWith("voice."))
                assertTrue(sound.getAsJsonArray("sounds").isEmpty());
            else
                for (var choice : sound.getAsJsonArray("sounds")) {
                    var item = choice.getAsJsonObject();
                    assertFalse(item.has("type"), "Effects must use project-owned samples");
                    String name = item.get("name").getAsString();
                    assertTrue(name.startsWith("mchjong:table/"));
                    byte[] audio = Files.readAllBytes(languages.getParent().resolve("sounds/" + name.substring(8) + ".ogg"));
                    assertEquals("OggS", new String(audio, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
                    assertTrue(audio.length > 1000);
                    assertFalse(item.get("stream").getAsBoolean());
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
            for (RiichiPreset rules : RiichiPreset.values())
                assertTrue(translated.has(rules.translationKey()), language + ": " + rules);
            for (RiichiAction.Type action : RiichiAction.Type.values())
                assertTrue(translated.has(new RiichiAction(action).translationKey()), language + ": " + action);
            for (var action : top.skyeyefast.mchjong.engine.McrAction.Type.values())
                assertTrue(translated.has("mcr.mchjong.action." + action.name().toLowerCase(Locale.ROOT)), language + ": " + action);
            for (var action : top.skyeyefast.mchjong.engine.SichuanAction.Type.values())
                assertTrue(translated.has("sichuan.mchjong.action." + action.name().toLowerCase(Locale.ROOT)), language + ": " + action);
            for (var variant : top.skyeyefast.mchjong.engine.MahjongVariant.values())
                assertTrue(translated.has("variant.mchjong." + variant.name().toLowerCase(Locale.ROOT)), language + ": " + variant);
        }
    }

    @Test
    void sourcePresetsContainEveryDistinctFaceWithoutGeneratedCopies() throws Exception {
        Set<String> designs = new HashSet<>();
        for (String preset : TileArtwork.PRESETS) {
            Path directory = artwork.resolve(preset + "/tiles");
            var definition = JsonParser.parseString(Files.readString(artwork.resolve(preset + "/theme_metadata.json"))).getAsJsonObject();
            assertEquals(preset, definition.get("id").getAsString());
            assertEquals("mchjong:tile_faces/" + preset + "/tiles", definition.get("tiles").getAsString());
            assertEquals(preset.equals("kansai") || preset.equals("kanto"), definition.get("blank_white_dragon").getAsBoolean());
            Set<String> hashes = new HashSet<>();
            for (int i = 0; i < 45; i++) {
                BufferedImage image = ImageIO.read(directory.resolve(TileArtwork.tileKey(i) + ".png").toFile());
                assertNotNull(image);
                assertTrue(image.getWidth() > 0 && image.getWidth() <= 2048);
                assertTrue(image.getHeight() > 0 && image.getHeight() <= 2048);
                int[] actual = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
                var pixels = java.nio.ByteBuffer.allocate(actual.length * Integer.BYTES);
                pixels.asIntBuffer().put(actual);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pixels.array()));
                hashes.add(hash);
                if (i == 0)
                    designs.add(hash);
            }
            assertEquals(45, hashes.size(), "Distinct numbered, honor, red and flower faces");
        }
        assertEquals(TileArtwork.PRESETS.size(), designs.size(), "The presets must not render the same ordinary faces");
        assertFalse(Files.exists(resources.resolve("assets/mchjong/textures/tile_faces")), "Tile source images must not be stored twice");
        assertFalse(Files.exists(resources.resolve("assets/mchjong/tile_face_presets")), "Preset definitions must not be stored twice");
        assertFalse(Files.exists(resources.resolve("META-INF/licenses/tile-face-presets-NOTICE.md")), "Preset notice must not be stored twice");
        for (String preset : TileArtwork.PRESETS)
            assertFalse(Files.exists(resources.resolve("META-INF/licenses/" + preset + "-source.json")), "Preset metadata must not be stored twice");
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
        for (String name : List.of("creeper", "mojang")) {
            var image = ImageIO.read(resources.resolve("assets/mchjong/textures/preset/back/" + name + ".png").toFile());
            assertEquals(256, image.getWidth());
            assertEquals(384, image.getHeight());
            assertTrue(Arrays.stream(image.getRGB(0, 0, 256, 384, null, 0, 256)).anyMatch(pixel -> pixel != 0));
        }
        var mojang = ImageIO.read(resources.resolve("assets/mchjong/textures/preset/back/mojang.png").toFile());
        int left = mojang.getWidth(), right = -1;
        for (int y = 0; y < mojang.getHeight(); y++)
            for (int x = 0; x < mojang.getWidth(); x++)
                if ((mojang.getRGB(x, y) >>> 24) != 0) {
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
        assertTrue(right - left < 150, "One banner mark must fit on the tile back");
        assertFalse(Files.exists(resources.resolve("resourcepacks")));
        var cloth = ImageIO.read(resources.resolve("assets/mchjong/textures/furniture/cloth_pattern.png").toFile());
        assertTrue(Arrays.stream(cloth.getRGB(0, 0, cloth.getWidth(), cloth.getHeight(), null, 0, cloth.getWidth())).allMatch(pixel -> pixel == 0));
        var sticks = ImageIO.read(resources.resolve("assets/mchjong/textures/point_stick/1000.png").toFile());
        var honba = ImageIO.read(resources.resolve("assets/mchjong/textures/point_stick/100.png").toFile());
        var riichi = ImageIO.read(resources.resolve("assets/mchjong/textures/item/riichi_stick.png").toFile());
        var hudRiichi = ImageIO.read(resources.resolve("assets/mchjong/textures/gui/sprites/hud/riichi_stick.png").toFile());
        var hudHonba = ImageIO.read(resources.resolve("assets/mchjong/textures/gui/sprites/hud/honba_stick.png").toFile());
        assertArrayEquals(sticks.getRGB(0, 0, 384, 32, null, 0, 384), riichi.getRGB(0, 0, 384, 32, null, 0, 384));
        assertArrayEquals(sticks.getRGB(0, 0, 384, 32, null, 0, 384), hudRiichi.getRGB(0, 0, 384, 32, null, 0, 384));
        assertArrayEquals(honba.getRGB(0, 0, 384, 32, null, 0, 384), hudHonba.getRGB(0, 0, 384, 32, null, 0, 384));
        var expectedTextures = new HashSet<>(Set.of("back.png",
                "plain.png", "cloth_pattern.png", "riichi_stick.png", "honba_stick.png", "mahjong_dye.png", "creative_mahjong_dye.png", "red_dora_dye.png", "undo_dye.png",
                "mahjong_manual.png", "creeper.png", "mojang.png"));
        for (int points : PointStickArtwork.POINTS) expectedTextures.add(points + ".png");
        FurnitureArtwork.textures().keySet().forEach(name -> expectedTextures.add(name + ".png"));
        TileMaterialArtwork.textures().keySet().forEach(name -> expectedTextures.add(name + ".png"));
        for (int face = 1; face <= 6; face++) expectedTextures.add("dice_" + face + ".png");
        try (var textures = Files.walk(resources.resolve("assets/mchjong/textures"))) {
            assertEquals(expectedTextures, textures.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".png")).map(file -> file.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet()), "Only textures referenced at runtime should ship");
        }
        assertTrue(Files.readString(artwork.resolve("kansai/theme_metadata.json")).contains("lietxia"));
        assertTrue(Files.readString(artwork.resolve("hong_kong/theme_metadata.json")).contains("samoheen"));
    }

    @Test
    void creeperBackUsesTheVanillaPixelGrid() throws Exception {
        var image = ImageIO.read(resources.resolve("assets/mchjong/textures/preset/back/creeper.png").toFile());
        String[] mask = {"........", "........", ".##..##.", ".##..##.",
            "...##...", "..####..", "..####..", "..#..#.."};
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int col = (x - 64) / 16, row = (y - 112) / 16;
            boolean ink = x >= 64 && x < 192 && y >= 112 && y < 240 && mask[row].charAt(col) == '#';
            assertEquals(ink ? 0xffeee5d3 : 0, image.getRGB(x, y), x + "," + y);
        }
    }

    @Test
    void modelsUseFewCuboidsAndOnlyAvailableTextures() throws Exception {
        for (String name : List.of("mahjong_table", "automatic_mahjong_table", "mahjong_stool",
                "mahjong_tile", "mahjong_box", "table_cloth", "point_stick")) {
            JsonObject model = JsonParser
                    .parseString(Files.readString(resources.resolve("assets/mchjong/models/item/" + name + ".json")))
                    .getAsJsonObject();
            if (name.endsWith("mahjong_table") || name.equals("mahjong_stool"))
                assertFalse(model.has("display"), "Furniture context transforms belong to the shared renderer");
            else assertTrue(model.getAsJsonObject("display").has("head"), name + " needs a head-slot transform");
        }
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
    void checkedInResourcesMatchGeneration(@TempDir Path generated) throws Exception {
        Path assets = generated.resolve("assets");
        Path server = generated.resolve("data");
        GenerateAssets.main(new String[] { assets.toString() });
        assertFalse(Files.exists(assets.resolve("data")), "Artwork must not generate server data");
        GenerateData.main(new String[] { server.toString() });
        assertSameFiles(resources, assets);
        assertSameFiles(data, server);
    }

    private static void assertSameFiles(Path checkedIn, Path generated) throws Exception {
        List<Path> expected;
        List<Path> actual;
        try (var files = Files.walk(generated)) {
            expected = files.filter(Files::isRegularFile).map(generated::relativize).sorted().toList();
        }
        try (var files = Files.walk(checkedIn)) {
            actual = files.filter(Files::isRegularFile).map(checkedIn::relativize).sorted().toList();
        }
        assertEquals(expected, actual, checkedIn + ": refresh with the :art:generate tasks");
        for (Path file : expected)
            assertArrayEquals(Files.readAllBytes(generated.resolve(file)),
                    Files.readAllBytes(checkedIn.resolve(file)), checkedIn.resolve(file).toString());
    }
}
