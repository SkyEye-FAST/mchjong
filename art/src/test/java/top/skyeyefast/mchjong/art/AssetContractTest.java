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
        assertEquals(2048, atlas.getWidth()); assertEquals(2048, atlas.getHeight());
        Set<String> hashes = new HashSet<>();
        for (int i = 0; i < 37; i++) {
            Path path = resources.resolve("assets/mchjong/textures/tile/" + i + ".png");
            BufferedImage tile = ImageIO.read(path.toFile());
            assertEquals(256, tile.getWidth()); assertEquals(384, tile.getHeight());
            hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))));
            for (int y = 0; y < TileArtwork.HEIGHT; y++) for (int x = 0; x < TileArtwork.WIDTH; x++) {
                assertEquals(255, tile.getRGB(x, y) >>> 24, "Transparent face " + i);
                assertEquals(tile.getRGB(x, y), atlas.getRGB(i % 8 * TileArtwork.WIDTH + x, i / 8 * TileArtwork.HEIGHT + y), "Face " + i);
            }
        }
        assertEquals(37, hashes.size(), "A numbered, honor or red face was duplicated");
        assertFalse(Files.exists(resources.resolve("assets/mchjong/textures/tile/37.png")));
        assertFalse(Files.exists(resources.resolve("assets/mchjong/textures/tile/edge.png")));
        for (int y = 2016; y < 2048; y++) for (int x = 2016; x < 2048; x++)
            assertEquals(0xffffffff, atlas.getRGB(x, y), "Neutral material swatch");
    }

    @Test void sourceOrderIncludesTheThreeRedFivesAndBlankWhiteDragon() throws Exception {
        for (int suit = 0; suit < 3; suit++) for (int number = 1; number <= 9; number++)
            assertEquals(List.of("Man", "Pin", "Sou").get(suit) + number, TileArtwork.sourceName(suit * 9 + number - 1));
        assertEquals(List.of("Ton", "Nan", "Shaa", "Pei", "Haku", "Hatsu", "Chun", "Man5-Dora", "Pin5-Dora", "Sou5-Dora"),
            java.util.stream.IntStream.range(27, 37).mapToObj(TileArtwork::sourceName).toList());
        assertThrows(IllegalArgumentException.class, () -> TileArtwork.sourceName(-1));
        assertThrows(IllegalArgumentException.class, () -> TileArtwork.sourceName(37));
        BufferedImage white = ImageIO.read(resources.resolve("assets/mchjong/textures/tile/31.png").toFile());
        for (int y = 8; y < 376; y++) for (int x = 8; x < 248; x++) assertEquals(TileArtwork.IVORY, white.getRGB(x, y));
    }

    @Test void defaultBackIsSolidAndOptionalPatternOnlyOverridesTheBack() throws Exception {
        String backPath = "assets/mchjong/textures/tile/back.png";
        BufferedImage solid = ImageIO.read(resources.resolve(backPath).toFile());
        Path pack = resources.resolve("resourcepacks/patterned_backs");
        BufferedImage pattern = ImageIO.read(pack.resolve(backPath).toFile());
        assertEquals(256, solid.getWidth()); assertEquals(384, solid.getHeight());
        assertEquals(solid.getWidth(), pattern.getWidth()); assertEquals(solid.getHeight(), pattern.getHeight());
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < TileArtwork.HEIGHT; y++) for (int x = 0; x < TileArtwork.WIDTH; x++) {
            assertEquals(TileArtwork.BACK, solid.getRGB(x, y));
            assertEquals(255, pattern.getRGB(x, y) >>> 24);
            assertEquals(pattern.getRGB(x, y), pattern.getRGB(TileArtwork.WIDTH - 1 - x, TileArtwork.HEIGHT - 1 - y), "Back must be 180-degree symmetric");
            if (x < 16 || x >= 240 || y < 16 || y >= 368) assertEquals(TileArtwork.BACK, pattern.getRGB(x, y));
            colors.add(pattern.getRGB(x, y));
        }
        assertTrue(colors.size() > 1, "Optional pattern must differ visibly");
        try (var files = Files.walk(pack.resolve("assets"))) {
            assertEquals(List.of(pack.resolve(backPath)), files.filter(Files::isRegularFile).toList());
        }
        JsonObject metadata = JsonParser.parseString(Files.readString(pack.resolve("pack.mcmeta"))).getAsJsonObject().getAsJsonObject("pack");
        assertEquals(34, metadata.get("pack_format").getAsInt());
        String description = metadata.getAsJsonObject("description").get("translate").getAsString();
        for (String language : List.of("en_us", "ja_jp", "zh_cn", "zh_tw")) {
            JsonObject translations = JsonParser.parseString(Files.readString(languages.resolve(language + ".json"))).getAsJsonObject();
            assertTrue(translations.has(description));
            assertTrue(translations.has("resourcePack.mchjong.patterned_backs.name"));
        }
        assertNotNull(ImageIO.read(pack.resolve("pack.png").toFile()));
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
                assertTrue(Files.isRegularFile(resources.resolve("assets/" + id[0] + "/textures/" + id[1] + ".png")), texture.getKey());
            }
            for (var element : model.getAsJsonArray("elements")) for (String edge : List.of("from", "to"))
                for (var coordinate : element.getAsJsonObject().getAsJsonArray(edge))
                    assertTrue(coordinate.getAsDouble() >= -16 && coordinate.getAsDouble() <= 32, "Minecraft model bounds");
            assertTrue(Files.isRegularFile(resources.resolve("assets/mchjong/blockstates/" + name + ".json")));
            assertTrue(Files.isRegularFile(resources.resolve("data/mchjong/recipe/" + name + ".json")));
            assertTrue(Files.isRegularFile(resources.resolve("data/mchjong/loot_table/blocks/" + name + ".json")));
        }
    }

    @Test void generationIsByteForByteReproducible(@TempDir Path second) throws Exception {
        GenerateAssets.main(new String[]{second.toString(), artwork.toString()});
        try (var files = Files.walk(resources)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path counterpart = second.resolve(resources.relativize(file));
                assertTrue(Files.isRegularFile(counterpart), file.toString());
                assertArrayEquals(Files.readAllBytes(file), Files.readAllBytes(counterpart), file.toString());
            }
        }
    }
}
