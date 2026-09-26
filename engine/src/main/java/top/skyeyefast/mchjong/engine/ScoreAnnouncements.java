package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable recording IDs derived from the scored receipt, never from translated text. */
public final class ScoreAnnouncements {
    public static final int MAX_VOICE_MILLIS = 8000;
    private static final List<String> ORDER = List.of(
        "Richi", "WRichi", "Ippatsu", "Chankan", "Rinshan", "Haitei", "Houtei", "Tsumo",
        "Pinhu", "Ipe", "Ryanpe", "Chitoi", "Haku", "Hatsu", "Chun", "SelfWind", "RoundWind",
        "Tanyao", "Chanta", "Ittsu", "Sanshoku", "Sandoko", "Sankantsu", "Toitoi", "Sananko",
        "Shosangen", "Honroto", "Junchan", "Honitsu", "Chinitsu", "Renhou", "Nagashi",
        "Tenhou", "Chihou", "Daisangen", "Suanko", "Tsuiso", "Lyuiso", "Chinroto", "Kokushi",
        "Shousushi", "Sukantsu", "Churen", "ChurenNineWaiting", "SuankoTanki", "KokushiThirteenWaiting", "Daisushi");
    private static final List<String> WINDS = List.of("east", "south", "west", "north");
    public record Row(String translationKey, int han, String voice) {}
    public static final Map<String, String> SUBTITLES;
    static {
        var subtitles = new LinkedHashMap<String, String>();
        YakuCatalog.allTranslationKeys().stream().sorted()
            .filter(key -> !key.equals("yaku.mchjong.seat_wind") && !key.equals("yaku.mchjong.round_wind")).forEach(key ->
            subtitles.put("yaku." + key.substring("yaku.mchjong.".length()), key));
        for (String wind : WINDS) {
            subtitles.put("yaku.seat_wind_" + wind, "yaku.mchjong.seat_wind");
            subtitles.put("yaku.round_wind_" + wind, "yaku.mchjong.round_wind");
        }
        subtitles.put("yaku.dora", "yaku.mchjong.dora");
        for (int count = 2; count <= 12; count++) subtitles.put("yaku.dora_" + count, "yaku.mchjong.dora");
        subtitles.put("yaku.dora_many", "yaku.mchjong.dora");
        for (String limit : List.of("mangan", "haneman", "baiman", "sanbaiman", "kazoe_yakuman", "yakuman",
                "yakuman_2", "yakuman_3", "yakuman_4", "yakuman_5", "yakuman_6"))
            subtitles.put("score." + limit, "score.mchjong." + limit);
        SUBTITLES = Collections.unmodifiableMap(subtitles);
    }

    private ScoreAnnouncements() {}

    public static String yaku(String name) {
        return "yaku." + YakuCatalog.translationKey(name).substring("yaku.mchjong.".length());
    }

    /** One shared receipt order for the visible rows and their recordings. */
    public static List<Row> rows(TableView view, TableView.Win win) {
        var score = win.score();
        var player = view.seats().get(win.seat());
        var result = new ArrayList<Row>();
        var values = HandAnalyzer.yakuValues(score.yaku(), player.melds().stream().allMatch(Meld::closed), view.rules());
        values.stream().filter(value -> score.yakuman() == 0 || value.yakuman())
            .sorted(Comparator.comparingInt(value -> ORDER.indexOf(value.name()))).forEach(value -> {
                String voice = switch (value.name()) {
                    case "SelfWind" -> "yaku.seat_wind_" + WINDS.get(Math.floorMod(win.seat() - view.dealer(), view.rules().players()));
                    case "RoundWind" -> "yaku.round_wind_" + WINDS.get(view.round() / view.rules().players());
                    default -> yaku(value.name());
                };
                result.add(new Row(YakuCatalog.translationKey(value.name()), value.yakuman() ? 0 : value.han(), voice));
            });
        if (score.yakuman() == 0 && score.dora() > 0) {
            var tiles = new ArrayList<>(player.hand());
            if (win.tile() >= 0 && tiles.size() + player.melds().size() * 3 == 13) tiles.add(win.tile());
            player.melds().forEach(meld -> tiles.addAll(meld.tiles()));
            tiles.addAll(player.norths());
            int red = (int) tiles.stream().filter(Tile::red).count();
            int north = player.norths().size();
            int ura = 0;
            if (view.rules().uraDora() && player.riichi()) {
                for (int i = 0; i < 5; i++) {
                    int index = view.wall().size() - 6 - 2 * i;
                    if (index >= 0 && view.wall().get(index) >= 0) {
                        int kind = Tile.doraAfter(Tile.kind(view.wall().get(index)), view.rules().sanma());
                        ura += (int) tiles.stream().filter(tile -> Tile.kind(tile) == kind).count();
                    }
                }
            }
            bonus(result, "dora", score.dora() - red - north - ura);
            bonus(result, "red_dora", red);
            bonus(result, "nuki_dora", north);
            bonus(result, "ura_dora", ura);
        }
        return List.copyOf(result);
    }

    private static void bonus(List<Row> rows, String name, int count) {
        if (count > 0) rows.add(new Row("yaku.mchjong." + name, count,
            "yaku.dora" + (count == 1 ? "" : count <= 12 ? "_" + count : "_many")));
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
        for (var win : wins) recordings += win.score().yaku().size() + Math.min(4, win.score().dora()) + 2;
        return Game.SETTLEMENT_TICKS + recordings * (MAX_VOICE_MILLIS / 50 + 10);
    }
}
