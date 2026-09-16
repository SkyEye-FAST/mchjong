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

    @Test void audioEventsHaveTranslatedSubtitlesAndSeparateCustomVoices() throws Exception {
        JsonObject sounds = JsonParser.parseString(Files.readString(languages.getParent().resolve("sounds.json"))).getAsJsonObject();
        JsonObject translations = JsonParser.parseString(Files.readString(languages.resolve("en_us.json"))).getAsJsonObject();
        assertEquals(25, sounds.size());
        for (var entry : sounds.entrySet()) {
            JsonObject sound = entry.getValue().getAsJsonObject();
            assertTrue(translations.has(sound.get("subtitle").getAsString()), entry.getKey());
            if (entry.getKey().startsWith("voice.")) assertTrue(sound.getAsJsonArray("sounds").isEmpty());
            else for (var choice : sound.getAsJsonArray("sounds")) {
                assertEquals("event", choice.getAsJsonObject().get("type").getAsString());
                assertTrue(choice.getAsJsonObject().get("name").getAsString().startsWith("minecraft:"));
            }
        }
    }

    @Test void translationObjectsDoNotRepeatKeys() throws Exception {
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

    @Test void allFourLanguagesHaveIdenticalKeysAndFormatArguments() throws Exception {
        JsonObject reference = JsonParser.parseString(Files.readString(languages.resolve("en_us.json"))).getAsJsonObject();
        Pattern format = Pattern.compile("%(?:[0-9]+\\$)?[sd]");
        for (String language : List.of("en_us", "ja_jp", "zh_cn", "zh_tw")) {
            JsonObject translated = JsonParser.parseString(Files.readString(languages.resolve(language + ".json"))).getAsJsonObject();
            assertEquals(reference.keySet(), translated.keySet(), language);
            for (String key : reference.keySet()) {
                String value = translated.get(key).getAsString();
                assertFalse(value.isBlank(), language + ": " + key);
                assertEquals(format.matcher(reference.get(key).getAsString()).results().map(m -> m.group()).sorted().toList(),
                    format.matcher(value).results().map(m -> m.group()).sorted().toList(), language + ": " + key);
            }
            for (RuleSet rules : RuleSet.values()) assertTrue(translated.has(rules.translationKey()), language + ": " + rules);
            for (Action.Type action : Action.Type.values()) assertTrue(translated.has(new Action(action).translationKey()), language + ": " + action);
        }
    }

    @Test void atlasContainsEveryDistinctFaceAtItsDeclaredCoordinates() throws Exception {
        BufferedImage atlas = ImageIO.read(resources.resolve("assets/mchjong/textures/tiles.png").toFile());
        assertEquals(2048, atlas.getWidth()); assertEquals(4096, atlas.getHeight());
        Set<String> hashes = new HashSet<>();
        try (var reference = new TileArtwork(artwork)) {
            for (int i = 0; i < 45; i++) {
                assertFalse(Files.exists(resources.resolve("assets/mchjong/textures/tile/" + i + ".png")), "Unused individual face shipped");
                BufferedImage tile = reference.face(i);
                assertEquals(256, tile.getWidth()); assertEquals(384, tile.getHeight());
                var pixels = java.nio.ByteBuffer.allocate(TileArtwork.WIDTH * TileArtwork.HEIGHT * Integer.BYTES);
                for (int y = 0; y < TileArtwork.HEIGHT; y++) for (int x = 0; x < TileArtwork.WIDTH; x++) {
                    int pixel = atlas.getRGB(i % 8 * TileArtwork.WIDTH + x, i / 8 * TileArtwork.HEIGHT + y);
                    assertEquals(255, pixel >>> 24, "Transparent face " + i);
                    assertEquals(tile.getRGB(x, y), pixel, "Source artwork differs from atlas cell " + i);
                    pixels.putInt(pixel);
                }
                hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pixels.array())));
            }
        }
        assertEquals(45, hashes.size(), "A numbered, honor, red or flower face was duplicated");
        assertFalse(Files.exists(resources.resolve("assets/mchjong/textures/tile/37.png")));
        assertFalse(Files.exists(resources.resolve("assets/mchjong/textures/tile/edge.png")));
        for (int y = 4064; y < 4096; y++) for (int x = 2016; x < 2048; x++)
            assertEquals(0xffffffff, atlas.getRGB(x, y), "Neutral material swatch");
    }

    @Test void sourceOrderIncludesTheThreeRedFivesAndBlankWhiteDragon() throws Exception {
        for (int suit = 0; suit < 3; suit++) for (int number = 1; number <= 9; number++)
            assertEquals(List.of("Man", "Pin", "Sou").get(suit) + number, TileArtwork.sourceName(suit * 9 + number - 1));
        assertEquals(List.of("Ton", "Nan", "Shaa", "Pei", "Haku", "Hatsu", "Chun", "Man5-Dora", "Pin5-Dora", "Sou5-Dora"),
            java.util.stream.IntStream.range(27, 37).mapToObj(TileArtwork::sourceName).toList());
        assertThrows(IllegalArgumentException.class, () -> TileArtwork.sourceName(-1));
        assertEquals(List.of("Plum", "Orchid", "Chrysanthemum", "Bamboo", "Spring", "Summer", "Autumn", "Winter"),
            java.util.stream.IntStream.range(37, 45).mapToObj(TileArtwork::sourceName).toList());
        assertThrows(IllegalArgumentException.class, () -> TileArtwork.sourceName(45));
        BufferedImage white = ImageIO.read(resources.resolve("assets/mchjong/textures/tiles.png").toFile())
            .getSubimage(31 % 8 * TileArtwork.WIDTH, 31 / 8 * TileArtwork.HEIGHT, TileArtwork.WIDTH, TileArtwork.HEIGHT);
        for (int y = 8; y < 376; y++) for (int x = 8; x < 248; x++) assertEquals(0xffffffff, white.getRGB(x, y));
    }

    @Test void defaultBackIsSolidAndNoBuiltinPackShips() throws Exception {
        String backPath = "assets/mchjong/textures/tile/back.png";
        BufferedImage solid = ImageIO.read(resources.resolve(backPath).toFile());
        assertEquals(256, solid.getWidth()); assertEquals(384, solid.getHeight());
        for (int y = 0; y < TileArtwork.HEIGHT; y++) for (int x = 0; x < TileArtwork.WIDTH; x++) {
            assertEquals(TileArtwork.BACK, solid.getRGB(x, y));
        }
        assertFalse(Files.exists(resources.resolve("resourcepacks")));
        var expectedTextures = new HashSet<>(Set.of("tiles.png", "tile_glyphs.png", "back.png"));
        FurnitureArtwork.textures().keySet().forEach(name -> expectedTextures.add(name + ".png"));
        TileMaterialArtwork.textures().keySet().forEach(name -> expectedTextures.add(name + ".png"));
        try (var textures = Files.walk(resources.resolve("assets/mchjong/textures"))) {
            assertEquals(expectedTextures, textures.filter(Files::isRegularFile)
                .filter(file -> file.toString().endsWith(".png")).map(file -> file.getFileName().toString())
                .collect(java.util.stream.Collectors.toSet()), "Only textures referenced at runtime should ship");
        }
        assertTrue(Files.readString(resources.resolve("META-INF/licenses/riichi-mahjong-tiles-LICENSE.txt")).contains("public domain"));
    }

    @Test void changedSourceArtworkIsRejected(@TempDir Path directory) throws Exception {
        Path corrupt = directory.resolve("corrupt.zip");
        Files.writeString(corrupt, "Not the pinned artwork");
        assertTrue(assertThrows(java.io.IOException.class, () -> new TileArtwork(corrupt)).getMessage().contains("SHA-256 mismatch"));
    }

    @Test void modelsUseFewCuboidsAndOnlyAvailableTextures() throws Exception {
        for (String name : List.of("mahjong_table", "mahjong_stool")) {
            JsonObject model = JsonParser.parseString(Files.readString(resources.resolve("assets/mchjong/models/block/" + name + ".json"))).getAsJsonObject();
            assertTrue(model.getAsJsonArray("elements").size() <= 16);
            for (var texture : model.getAsJsonObject("textures").entrySet()) {
                String[] id = texture.getValue().getAsString().split(":", 2);
                assertTrue(id[0].equals("minecraft") || Files.isRegularFile(resources.resolve("assets/" + id[0] + "/textures/" + id[1] + ".png")), texture.getKey());
            }
            for (var element : model.getAsJsonArray("elements")) for (String edge : List.of("from", "to"))
                for (var coordinate : element.getAsJsonObject().getAsJsonArray(edge))
                    assertTrue(coordinate.getAsDouble() >= -16 && coordinate.getAsDouble() <= 32, "Minecraft model bounds");
            assertTrue(Files.isRegularFile(resources.resolve("assets/mchjong/blockstates/" + name + ".json")));
            assertTrue(Files.isRegularFile(data.resolve("data/mchjong/recipe/" + name + ".json")));
            assertTrue(Files.isRegularFile(data.resolve("data/mchjong/loot_table/blocks/" + name + ".json")));
        }
    }

    @Test void generationIsByteForByteReproducible(@TempDir Path second) throws Exception {
        GenerateAssets.main(new String[]{second.toString(), artwork.toString()});
        assertFalse(Files.exists(second.resolve("data")), "Artwork must not generate server data");
        GenerateData.main(new String[]{second.resolve("server").toString()});
        try (var files = Files.walk(data)) {
            for (Path file : files.filter(Files::isRegularFile).toList())
                assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(second.resolve("server").resolve(data.relativize(file))));
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
