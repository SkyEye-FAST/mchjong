package top.skyeyefast.mchjong.art;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.RuleSet;
import static org.junit.jupiter.api.Assertions.*;

class TranslationReferenceTest {
    @Test void everyCompleteSourceKeyAndRulePresetExistsInEveryLanguage() throws Exception {
        Path root = Path.of(System.getProperty("mchjong.sourceRoot"));
        Path languages = Path.of(System.getProperty("mchjong.languages"));
        Pattern key = Pattern.compile("\"([A-Za-z]+\\.mchjong\\.[A-Za-z0-9_.]+)\"");
        var used = new TreeSet<String>();
        for (String directory : List.of("common/src/main", "engine/src/main", "src/main", "neoforge/src/main")) {
            try (var paths = Files.walk(root.resolve(directory))) {
                for (Path path : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".kt")).toList()) {
                    var matches = key.matcher(Files.readString(path));
                    while (matches.find()) {
                        String value = matches.group(1);
                        if (!value.endsWith(".") && !value.endsWith("_")) used.add(value);
                    }
                }
            }
        }
        for (RuleSet rules : RuleSet.values()) { used.add(rules.translationKey()); used.add(rules.presetKey()); }
        // Registry-derived names can appear in Jade even when no source uses a literal translation key.
        for (String block : List.of("mahjong_table", "automatic_mahjong_table", "mahjong_stool", "table_space"))
            used.add("block.mchjong." + block);
        for (String item : List.of("mahjong_tile", "mahjong_box", "point_stick", "table_cloth"))
            used.add("item.mchjong." + item);
        used.add("entity.mchjong.seat");
        for (String language : List.of("en_us", "ja_jp", "zh_cn", "zh_tw")) {
            JsonObject translated = JsonParser.parseString(Files.readString(languages.resolve(language + ".json"))).getAsJsonObject();
            var missing = new TreeSet<>(used);
            missing.removeAll(translated.keySet());
            assertTrue(missing.isEmpty(), language + " has missing source translations: " + missing);
        }
    }
}
