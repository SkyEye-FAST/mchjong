package top.skyeyefast.mchjong.client;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuleHelpTest {
    @Test void everyCatalogAwardKeepsItsExactPage() throws Exception {
        var pages = new HashMap<String, RuleHelp.Topic>();
        var awards = new HashMap<String, RuleHelp.Topic>();
        for (String entry : List.of("riichi_yaku", "riichi_yakuman", "mcr_fan_low", "mcr_fan_middle", "mcr_fan_high", "sichuan_fan", "taiwan_tai")) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream("/assets/mchjong/patchouli_books/guide/en_us/entries/" + entry + ".json"), StandardCharsets.UTF_8)) {
                var data = JsonParser.parseReader(reader).getAsJsonObject();
                RuleHelp.addEntry(entry, data, pages, awards);
                var definitions = data.getAsJsonArray("pages");
                for (int i = 0; i < definitions.size(); i++) {
                    var page = definitions.get(i).getAsJsonObject();
                    if (!page.has("anchor")) continue;
                    var topic = pages.get(page.get("text").getAsString());
                    assertEquals(i, topic.page()); assertEquals(entry, topic.entry());
                }
            }
        }
        assertTrue(top.skyeyefast.mchjong.engine.YakuCatalog.allTranslationKeys().stream().allMatch(awards::containsKey));
        for (var pattern : top.skyeyefast.mchjong.engine.TaiwanRules.Pattern.values()) assertTrue(awards.containsKey("taiwan.mchjong.pattern." + pattern.name().toLowerCase(java.util.Locale.ROOT)));
        for (var fan : top.skyeyefast.mchjong.engine.SichuanSettlement.Fan.values()) assertTrue(awards.containsKey("sichuan.mchjong.fan." + fan.name().toLowerCase(java.util.Locale.ROOT)));
        assertEquals("Two suits.\n\nExample text", RuleHelp.plain("$(bold)Two suits.$()$(br2)$(l:mchjong:rules)Example text$(/l)"));
    }
}
