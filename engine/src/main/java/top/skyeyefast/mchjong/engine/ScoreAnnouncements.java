package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable recording IDs derived from the scored receipt, never from translated text. */
public final class ScoreAnnouncements {
    public static final int MAX_VOICE_MILLIS = 8000;
    public static final Map<String, String> SUBTITLES;
    static {
        var subtitles = new LinkedHashMap<String, String>();
        YakuCatalog.allTranslationKeys().stream().sorted().forEach(key ->
            subtitles.put("yaku." + key.substring("yaku.mchjong.".length()), key));
        subtitles.put("yaku.dora", "ui.mchjong.dora.short");
        for (int count = 2; count <= 12; count++) subtitles.put("yaku.dora_" + count, "ui.mchjong.dora.short");
        subtitles.put("yaku.dora_many", "ui.mchjong.dora.short");
        for (String limit : List.of("mangan", "haneman", "baiman", "sanbaiman", "kazoe_yakuman", "yakuman",
                "yakuman_2", "yakuman_3", "yakuman_4", "yakuman_5", "yakuman_6"))
            subtitles.put("score." + limit, "score.mchjong." + limit);
        SUBTITLES = Collections.unmodifiableMap(subtitles);
    }

    private ScoreAnnouncements() {}

    public static String yaku(String name) {
        return "yaku." + YakuCatalog.translationKey(name).substring("yaku.mchjong.".length());
    }

    /** Dora is one counted receipt row, after all actual yaku. */
    public static List<String> rows(HandScore score) {
        var result = new ArrayList<String>();
        for (String name : score.yaku()) result.add(yaku(name));
        if (score.dora() > 0) result.add(score.dora() == 1 ? "yaku.dora"
            : score.dora() <= 12 ? "yaku.dora_" + score.dora() : "yaku.dora_many");
        return List.copyOf(result);
    }

    /** Use the server's payment, including kiriage and preset caps, not han alone. */
    public static String limit(HandScore score, boolean dealer) {
        if (score.yakuman() > 0) return score.yakuman() == 1 || score.yakuman() > 6
            ? "score.yakuman" : "score.yakuman_" + score.yakuman();
        int base = score.ron() > 0 ? score.ron() / (dealer ? 6 : 4)
            : score.tsumoDealer() / 2;
        if (base >= 8000) return "score.kazoe_yakuman";
        if (base >= 6000) return "score.sanbaiman";
        if (base >= 4000) return "score.baiman";
        if (base >= 3000) return "score.haneman";
        if (base >= 2000) return "score.mangan";
        return null;
    }

    /** A finite server fallback also covers missing/disconnected presentation clients. */
    public static int maximumTicks(List<TableView.Win> wins) {
        if (wins.isEmpty()) return Game.SETTLEMENT_TICKS;
        int recordings = 1; // Opening ron/tsumo, then every winner's rows and final grade.
        for (var win : wins) recordings += rows(win.score()).size() + 2;
        return Game.SETTLEMENT_TICKS + recordings * (MAX_VOICE_MILLIS / 50 + 10);
    }
}
