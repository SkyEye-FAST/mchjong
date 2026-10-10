package top.skyeyefast.mchjong.client;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.text.CountedText;
import static org.junit.jupiter.api.Assertions.*;

class CountedTextTest {
    @Test void quantitiesUseTheActualCountAndKeepNestedTranslations() throws Exception {
        var previous = Language.getInstance();
        try {
            load("en_us");
            assertEquals("0 tiles left", CountedText.of("ui.mchjong.remaining", 0, 0).getString());
            assertEquals("1 tile left", CountedText.of("ui.mchjong.remaining", 0, 1).getString());
            assertEquals("2 tiles left", CountedText.of("ui.mchjong.remaining", 0, 2).getString());
            assertEquals("-1 point", CountedText.of("ui.mchjong.points", 0, -1L).getString());
            assertEquals("2147483648 points", CountedText.of("ui.mchjong.points", 0, 2147483648L).getString());
            assertEquals("Confirmed 1/4 (2 seconds left)", CountedText.of("sichuan.mchjong.reading", 2, 1, 4, 2).getString());
            assertEquals("Confirmed 2/4 (1 second left)", CountedText.of("sichuan.mchjong.reading", 2, 2, 4, 1).getString());
            var round = Component.translatable("taiwan.mchjong.round", Component.translatable("wind.mchjong.east.short"), 1, 0);
            load("zh_tw");
            assertEquals("東風 1 局，連莊 0 次", round.getString());
            assertEquals("赤五萬", Component.translatable("tile.mchjong.red", Component.translatable("tile.mchjong.5m")).getString());
            assertEquals("東家座位：1, 2, 3", Component.translatable("room.mchjong.position", Component.translatable("wind.mchjong.east.short"), 1, 2, 3).getString());
            assertEquals("輪到「小明」", Component.translatable("ui.mchjong.turn", "小明").getString());
            assertEquals("輪到「Alex」", Component.translatable("ui.mchjong.turn", "Alex").getString());
            assertEquals("1 點", CountedText.of("ui.mchjong.points", 0, 1).getString());
        } finally { Language.inject(previous); }
    }

    private void load(String locale) throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/assets/mchjong/lang/" + locale + ".json"), StandardCharsets.UTF_8)) {
            var translations = JsonParser.parseReader(reader).getAsJsonObject();
            Language.inject(new Language() {
                @Override public String getOrDefault(String key, String fallback) { return translations.has(key) ? translations.get(key).getAsString() : fallback; }
                @Override public boolean has(String key) { return translations.has(key); }
                @Override public boolean isDefaultRightToLeft() { return false; }
                @Override public FormattedCharSequence getVisualOrder(FormattedText text) { return FormattedCharSequence.forward(text.getString(), Style.EMPTY); }
            });
        }
    }
}
