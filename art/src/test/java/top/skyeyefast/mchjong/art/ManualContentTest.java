package top.skyeyefast.mchjong.art;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.YakuCatalog;
import top.skyeyefast.mchjong.engine.TaiwanRules;
import top.skyeyefast.mchjong.engine.SichuanSettlement;
import static org.junit.jupiter.api.Assertions.*;

class ManualContentTest {
    private final Path languages = Path.of(System.getProperty("mchjong.languages"));
    private final Path book = languages.getParent().resolve("patchouli_books/guide/en_us");
    private JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }

    @Test void everyAwardHasAFacingTileImageInsteadOfTextNotation() throws Exception {
        var referenced = new HashSet<String>();
        var notation = Pattern.compile("\\$\\(br2\\)(?:Example:|牌例：|例牌：)");
        for (String id : List.of("riichi_yaku", "riichi_yakuman", "mcr_fan_high", "mcr_fan_middle",
                "mcr_fan_low", "sichuan_fan", "taiwan_tai")) {
            var pages = read(book.resolve("entries/" + id + ".json")).getAsJsonArray("pages");
            assertEquals(0, pages.size() % 2, id);
            for (int i = 0; i < pages.size(); i += 2) {
                var description = pages.get(i).getAsJsonObject();
                var example = pages.get(i + 1).getAsJsonObject();
                assertEquals("patchouli:text", description.get("type").getAsString());
                assertEquals("patchouli:image", example.get("type").getAsString());
                String image = id.substring(0, id.indexOf('_')) + "/" + description.get("anchor").getAsString();
                assertEquals(List.of("mchjong:textures/manual/" + image + ".png"),
                    example.getAsJsonArray("images").asList().stream().map(JsonElement::getAsString).toList());
                assertTrue(referenced.add(image), image);
                for (String locale : List.of("en_us", "ja_jp", "zh_cn", "zh_tw")) {
                    String text = read(languages.resolve(locale + ".json")).get(description.get("text").getAsString()).getAsString();
                    assertFalse(notation.matcher(text).find(), locale + ":" + image);
                }
            }
        }
        var generated = new HashSet<String>();
        for (var hands : ManualExamples.HANDS.values()) generated.addAll(hands.keySet());
        assertEquals(generated, referenced);
        assertEquals(175, referenced.size());
    }

    @Test void diagramsUseTheRequestedRegionalFacesAndFitOnOneLine() throws Exception {
        Path resources = Path.of(System.getProperty("mchjong.resources")).resolve("assets/mchjong/textures/manual");
        var examples = Map.of("riichi/haku", "kansai", "mcr/dragon_pung", "hong_kong",
            "sichuan/kong", "sichuan", "taiwan/white_dragon", "taiwan");
        var firstFaces = Map.of("riichi/haku", 31, "mcr/dragon_pung", 33, "sichuan/kong", 0, "taiwan/white_dragon", 31);
        for (var example : examples.entrySet()) {
            var face = new TileArtwork(Path.of(System.getProperty("mchjong.artwork")), example.getValue()).face(firstFaces.get(example.getKey()));
            var thumbnail = new java.awt.image.BufferedImage(23, 35, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var g = thumbnail.createGraphics();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(face, 0, 0, 23, 35, null);
            } finally { g.dispose(); }
            var image = javax.imageio.ImageIO.read(resources.resolve(example.getKey() + ".png").toFile());
            assertArrayEquals(thumbnail.getRGB(0, 0, 23, 35, null, 0, 23), image.getRGB(4, 8, 23, 35, null, 0, 23), example.getKey());
        }
        try (var files = Files.walk(resources)) {
            for (var path : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                var image = javax.imageio.ImageIO.read(path.toFile());
                int painted = 0;
                for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) == 0) continue;
                    painted++;
                    assertTrue(x < 200 && y < 43, path + ":" + x + "," + y);
                }
                assertTrue(painted > 0, path.toString());
            }
        }
    }

    @Test void chaptersEntriesTranslationsLinksAndPatternCoverageAreComplete() throws Exception {
        var entries = new TreeMap<String, JsonObject>();
        var categories = new TreeSet<String>();
        try (var paths = Files.list(book.resolve("categories"))) {
            for (var path : paths.toList()) categories.add(path.getFileName().toString().replace(".json", ""));
        }
        assertTrue(categories.containsAll(Set.of("rules", "mcr", "sichuan", "taiwan")));
        try (var paths = Files.list(book.resolve("entries"))) {
            for (var path : paths.toList()) entries.put(path.getFileName().toString().replace(".json", ""), read(path));
        }
        assertTrue(entries.keySet().containsAll(Set.of("riichi_flow", "riichi_points", "riichi_presets", "riichi_sanma",
            "riichi_yaku", "riichi_yakuman", "mcr_flow", "mcr_special", "mcr_fan_high", "mcr_fan_middle", "mcr_fan_low",
            "sichuan_flow", "sichuan_fan", "sichuan_payments", "taiwan_flow", "taiwan_tai", "taiwan_payments")));
        var anchors = new HashMap<String, Set<String>>();
        for (var entry : entries.entrySet()) {
            assertTrue(categories.contains(entry.getValue().get("category").getAsString().replace("mchjong:", "")), entry.getKey());
            var set = new HashSet<String>();
            for (var page : entry.getValue().getAsJsonArray("pages")) {
                var object = page.getAsJsonObject();
                if (object.has("anchor")) assertTrue(set.add(object.get("anchor").getAsString()), entry.getKey());
                if (object.has("images")) for (var image : object.getAsJsonArray("images")) {
                    var path = Path.of(System.getProperty("mchjong.resources")).resolve("assets/" + image.getAsString().replace(':', '/'));
                    assertTrue(Files.exists(path), path.toString());
                    var png = javax.imageio.ImageIO.read(path.toFile());
                    assertEquals(256, png.getWidth()); assertEquals(256, png.getHeight());
                }
            }
            anchors.put(entry.getKey(), set);
        }
        var english = read(languages.resolve("en_us.json"));
        var mcr = new HashSet<String>();
        for (String id : List.of("mcr_fan_high", "mcr_fan_middle", "mcr_fan_low")) mcr.addAll(anchors.get(id));
        var expectedMcr = new HashSet<String>();
        for (String key : english.keySet()) if (key.startsWith("mcr.mchjong.fan.") && !key.endsWith("mixed_kong_pair")) expectedMcr.add(key.substring("mcr.mchjong.fan.".length()));
        assertEquals(81, expectedMcr.size()); assertEquals(expectedMcr, mcr);
        var yaku = new HashSet<String>(anchors.get("riichi_yaku")); yaku.addAll(anchors.get("riichi_yakuman"));
        assertEquals(YakuCatalog.allTranslationKeys().stream().map(k -> k.substring("yaku.mchjong.".length())).collect(java.util.stream.Collectors.toSet()), yaku);
        assertEquals(Arrays.stream(TaiwanRules.Pattern.values()).map(v -> v.name().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet()), anchors.get("taiwan_tai"));
        assertEquals(Arrays.stream(SichuanSettlement.Fan.values()).map(v -> v.name().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet()), anchors.get("sichuan_fan"));
        var link = Pattern.compile("[$][(]l:([^)]*)[)]");
        for (String locale : List.of("en_us", "ja_jp", "zh_cn", "zh_tw")) {
            var translations = read(languages.resolve(locale + ".json"));
            for (String category : categories) {
                var definition = read(book.resolve("categories/" + category + ".json"));
                for (String field : List.of("name", "description"))
                    assertTrue(translations.has(definition.get(field).getAsString()), locale + ":" + category + ":" + field);
            }
            for (var entry : entries.values()) {
                assertTrue(translations.has(entry.get("name").getAsString()));
                for (var page : entry.getAsJsonArray("pages")) {
                    var p = page.getAsJsonObject();
                    if (!p.has("text")) continue;
                    String key = p.get("text").getAsString(); assertTrue(translations.has(key), locale + ":" + key);
                    var matcher = link.matcher(translations.get(key).getAsString());
                    while (matcher.find()) {
                        String target = matcher.group(1);
                        if (target.startsWith("https://")) continue;
                        String[] parts = target.replace("mchjong:", "").split("#", 2);
                        assertTrue(entries.containsKey(parts[0]), target);
                        if (parts.length == 2) assertTrue(anchors.get(parts[0]).contains(parts[1]), target);
                    }
                }
            }
        }
    }
}
