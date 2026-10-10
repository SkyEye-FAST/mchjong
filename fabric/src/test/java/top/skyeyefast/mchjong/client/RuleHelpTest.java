package top.skyeyefast.mchjong.client;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuleHelpTest {
    @Test void everyCatalogAwardKeepsItsExactPageAndExistingDiagram() throws Exception {
        var pages = new HashMap<String, RuleHelp.Topic>();
        var awards = new HashMap<String, RuleHelp.Topic>();
        for (String entry : List.of("riichi_yaku", "riichi_yakuman", "mcr_fan_low", "mcr_fan_middle", "mcr_fan_high", "sichuan_fan", "taiwan_tai")) {
            try (var reader = new InputStreamReader(getClass().getResourceAsStream("/assets/mchjong/patchouli_books/guide/en_us/entries/" + entry + ".json"), StandardCharsets.UTF_8)) {
                var data = JsonParser.parseReader(reader).getAsJsonObject();
                RuleHelp.addEntry(entry, data, pages, awards);
                var definitions = data.getAsJsonArray("pages");
                for (int i = 0; i < definitions.size(); i += 2) {
                    var topic = pages.get(definitions.get(i).getAsJsonObject().get("text").getAsString());
                    assertEquals(i, topic.page()); assertEquals(entry, topic.entry());
                    var spread = topic.diagram().spread(100);
                    assertTrue(spread.tileWidth() >= 12, entry + ":" + i);
                    int split = spread.across() ? spread.split() : 0;
                    int[] bounds = {0, split, topic.diagram().parts().size()};
                    for (int side = 0; side < 2; side++) {
                        int rows = 0;
                        for (int start = bounds[side]; start < bounds[side + 1]; rows++) {
                            int end = topic.diagram().rowEnd(start, bounds[side + 1], spread.tileWidth(), 100);
                            assertTrue(topic.diagram().width(start, end, spread.tileWidth()) <= 100);
                            start = end;
                        }
                        assertTrue(rows <= 2, entry + ":" + i);
                    }
                }
            }
        }
        assertEquals(175, awards.size());
        assertTrue(top.skyeyefast.mchjong.engine.YakuCatalog.allTranslationKeys().stream().allMatch(awards::containsKey));
        for (var pattern : top.skyeyefast.mchjong.engine.TaiwanRules.Pattern.values()) assertTrue(awards.containsKey("taiwan.mchjong.pattern." + pattern.name().toLowerCase(java.util.Locale.ROOT)));
        assertEquals("Two suits.\n\nExample text", RuleHelp.plain("$(bold)Two suits.$()$(br2)$(l:mchjong:rules)Example text$(/l)"));
    }
}
