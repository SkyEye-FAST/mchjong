package top.skyeyefast.mchjong.art;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import static org.junit.jupiter.api.Assertions.*;

class AssetContractTest {
    private final Path resources = Path.of(System.getProperty("mchjong.resources"));
    private final Path languages = Path.of(System.getProperty("mchjong.languages"));
    private final Path artwork = Path.of(System.getProperty("mchjong.artwork"));

    @Test
    void audioEventsHaveTranslatedSubtitlesAndAvailableRecordings() throws Exception {
        JsonObject sounds = JsonParser.parseString(Files.readString(languages.getParent().resolve("sounds.json")))
                .getAsJsonObject();
        JsonObject translations = JsonParser.parseString(Files.readString(languages.resolve("en_us.json")))
                .getAsJsonObject();
        top.skyeyefast.mchjong.engine.ScoreAnnouncements.SUBTITLES.forEach((event, subtitle) ->
            assertEquals(subtitle, sounds.getAsJsonObject("voice." + event).get("subtitle").getAsString()));
        for (var entry : sounds.entrySet()) {
            JsonObject sound = entry.getValue().getAsJsonObject();
            assertTrue(translations.has(sound.get("subtitle").getAsString()), entry.getKey());
            for (var choice : sound.getAsJsonArray("sounds")) {
                String name = choice.getAsJsonObject().get("name").getAsString();
                assertTrue(name.startsWith("mchjong:"), name);
                byte[] audio = Files.readAllBytes(languages.getParent().resolve("sounds/" + name.substring(8) + ".ogg"));
                assertEquals("OggS", new String(audio, 0, 4, java.nio.charset.StandardCharsets.US_ASCII), name);
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
                        format.matcher(reference.get(key).getAsString()).results().map(m -> m.group()).sorted().toList(),
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
    void sourcePresetsContainEveryFace() throws Exception {
        for (String preset : TileArtwork.PRESETS) {
            Path directory = artwork.resolve(preset + "/tiles");
            var definition = JsonParser.parseString(Files.readString(artwork.resolve(preset + "/theme_metadata.json")))
                    .getAsJsonObject();
            assertEquals(preset, definition.get("id").getAsString());
            assertEquals("mchjong:tile_faces/" + preset + "/tiles", definition.get("tiles").getAsString());
            for (String key : TileArtwork.FACE_KEYS) {
                var image = ImageIO.read(directory.resolve(key + ".png").toFile());
                assertNotNull(image, preset + ": " + key);
                assertTrue(image.getWidth() > 0 && image.getWidth() <= 2048);
                assertTrue(image.getHeight() > 0 && image.getHeight() <= 2048);
            }
        }
    }

    @Test
    void checkedInResourcesMatchGeneration(@TempDir Path generated) throws Exception {
        Path assets = generated.resolve("assets");
        GenerateAssets.main(new String[] { assets.toString() });
        List<Path> expected;
        List<Path> actual;
        try (var files = Files.walk(assets)) {
            expected = files.filter(Files::isRegularFile).map(assets::relativize).sorted().toList();
        }
        try (var files = Files.walk(resources)) {
            actual = files.filter(Files::isRegularFile).map(resources::relativize).sorted().toList();
        }
        assertEquals(expected, actual, "Refresh checked-in resources with :art:generateAssets");
        for (Path file : expected)
            assertArrayEquals(Files.readAllBytes(assets.resolve(file)),
                    Files.readAllBytes(resources.resolve(file)), file.toString());
    }
}
