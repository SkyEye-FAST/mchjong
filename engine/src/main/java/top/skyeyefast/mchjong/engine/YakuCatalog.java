package top.skyeyefast.mchjong.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Canonical mapping between scoring engine / library yaku identifiers and localized translation keys. */
public final class YakuCatalog {
    private static final Map<String, String> TRANSLATION_KEYS;

    static {
        var map = new LinkedHashMap<String, String>();
        map.put("Chankan", "yaku.mchjong.chankan");
        map.put("Chanta", "yaku.mchjong.chanta");
        map.put("Chihou", "yaku.mchjong.chihou");
        map.put("Chinitsu", "yaku.mchjong.chinitsu");
        map.put("Chinroto", "yaku.mchjong.chinroutou");
        map.put("Chitoi", "yaku.mchjong.chiitoitsu");
        map.put("Chun", "yaku.mchjong.chun");
        map.put("Churen", "yaku.mchjong.chuuren_poutou");
        map.put("ChurenNineWaiting", "yaku.mchjong.chuuren_nine_waiting");
        map.put("Daisangen", "yaku.mchjong.daisangen");
        map.put("Daisushi", "yaku.mchjong.daisuushii");
        map.put("Haitei", "yaku.mchjong.haitei_raoyue");
        map.put("Haku", "yaku.mchjong.haku");
        map.put("Hatsu", "yaku.mchjong.hatsu");
        map.put("Honitsu", "yaku.mchjong.honitsu");
        map.put("Honroto", "yaku.mchjong.honroutou");
        map.put("Houtei", "yaku.mchjong.houtei_raoyui");
        map.put("Ipe", "yaku.mchjong.iipeikou");
        map.put("Ippatsu", "yaku.mchjong.ippatsu");
        map.put("Ittsu", "yaku.mchjong.ittsu");
        map.put("Junchan", "yaku.mchjong.junchan");
        map.put("Kokushi", "yaku.mchjong.kokushi_musou");
        map.put("KokushiThirteenWaiting", "yaku.mchjong.kokushi_thirteen_waiting");
        map.put("Lyuiso", "yaku.mchjong.ryuuiisou");
        map.put("Nagashi", "yaku.mchjong.nagashi_mangan");
        map.put("Pinhu", "yaku.mchjong.pinhu");
        map.put("Renhou", "yaku.mchjong.renhou");
        map.put("Richi", "yaku.mchjong.riichi");
        map.put("Rinshan", "yaku.mchjong.rinshan_kaihou");
        map.put("RoundWind", "yaku.mchjong.round_wind");
        map.put("Ryanpe", "yaku.mchjong.ryanpeikou");
        map.put("Sananko", "yaku.mchjong.sanankou");
        map.put("Sandoko", "yaku.mchjong.sanshoku_doukou");
        map.put("Sankantsu", "yaku.mchjong.sankantsu");
        map.put("Sanshoku", "yaku.mchjong.sanshoku_doujun");
        map.put("SelfWind", "yaku.mchjong.seat_wind");
        map.put("Shosangen", "yaku.mchjong.shousangen");
        map.put("Shousushi", "yaku.mchjong.shousuushii");
        map.put("Suanko", "yaku.mchjong.suuankou");
        map.put("SuankoTanki", "yaku.mchjong.suuankou_tanki");
        map.put("Sukantsu", "yaku.mchjong.suukantsu");
        map.put("Tanyao", "yaku.mchjong.tanyao");
        map.put("Tenhou", "yaku.mchjong.tenhou");
        map.put("Toitoi", "yaku.mchjong.toitoi");
        map.put("Tsuiso", "yaku.mchjong.tsuuiisou");
        map.put("Tsumo", "yaku.mchjong.menzen_tsumo");
        map.put("WRichi", "yaku.mchjong.double_riichi");
        TRANSLATION_KEYS = Collections.unmodifiableMap(map);
    }

    public static String translationKey(String name) {
        String key = TRANSLATION_KEYS.get(name);
        if (key != null) return key;
        for (var entry : TRANSLATION_KEYS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return "yaku.mchjong." + name.toLowerCase(Locale.ROOT);
    }

    public static Set<String> allTranslationKeys() {
        return Set.copyOf(TRANSLATION_KEYS.values());
    }

    private YakuCatalog() {}
}
