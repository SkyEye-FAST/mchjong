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
        for (String directory : List.of("common/src/main", "engine/src/main", "fabric/src/main", "neoforge/src/main")) {
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
        for (var option : top.skyeyefast.mchjong.engine.RuleOption.values()) {
            used.add(option.translationKey());
            used.add(option.descriptionKey());
        }
        for (var group : top.skyeyefast.mchjong.engine.RuleOption.Group.values()) used.add(group.translationKey());
        for (String mode : List.of("preset", "details", "custom")) used.add("rules.mchjong.mode." + mode);
        // Registry-derived names can appear in Jade even when no source uses a literal translation key.
        for (String block : List.of("mahjong_table", "automatic_mahjong_table", "mahjong_stool", "table_space"))
            used.add("block.mchjong." + block);
        for (String item : List.of("mahjong_tile", "mahjong_box", "point_stick", "table_cloth", "mahjong_dye", "creative_mahjong_dye", "red_dora_dye", "undo_dye"))
            used.add("item.mchjong." + item);
        for (var composition : top.skyeyefast.mchjong.engine.RedFives.values()) used.add(composition.translationKey());
        used.addAll(top.skyeyefast.mchjong.engine.YakuCatalog.allTranslationKeys());
        for (String preset : List.of("kansai", "kanto")) used.add("preset.mchjong." + preset);
        used.add("entity.mchjong.seat");
        used.add("itemGroup.mchjong");
        for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped")) {
            used.add("block.mchjong.mahjong_table." + wood);
            used.add("block.mchjong.automatic_mahjong_table." + wood);
        }
        for (String color : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
            used.add("item.mchjong.table_cloth." + color);
        }
        for (int kind = 0; kind < 34; kind++) used.add("tile.mchjong." + top.skyeyefast.mchjong.engine.Tile.notation(kind));
        for (String style : List.of("name", "mpsz")) used.add("settings.mchjong.tile_labels." + style);
        for (String discard : List.of("direct", "single_click", "double_click", "confirm")) used.add("ui.mchjong.help." + discard);
        for (String language : List.of("en_us", "ja_jp", "zh_cn", "zh_tw")) {
            JsonObject translated = JsonParser.parseString(Files.readString(languages.resolve(language + ".json"))).getAsJsonObject();
            var missing = new TreeSet<>(used);
            missing.removeAll(translated.keySet());
            assertTrue(missing.isEmpty(), language + " has missing source translations: " + missing);
        }
    }
}
