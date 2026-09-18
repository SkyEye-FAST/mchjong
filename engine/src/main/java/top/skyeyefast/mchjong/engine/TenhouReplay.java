package top.skyeyefast.mchjong.engine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tenhou /6 JSON interchange, mlog 2.3 (not compressed XML .mjlog).
 * Protocol reference: https://github.com/Equim-chan/tensoul/blob/main/convert.js
 * Serialization belongs to the application. Payments are recorded, never re-scored here.
 */
public final class TenhouReplay {
    private TenhouReplay() {}

    public static Map<String, Object> export(ReplayMatch match) {
        if (match.hands().isEmpty()) throw new IllegalArgumentException("There are no completed hands to export");
        var root = new LinkedHashMap<String, Object>();
        root.put("ver", "2.3");
        root.put("ref", match.id().toString());
        root.put("title", List.of("MCjhong · " + match.rules().name(), Instant.ofEpochMilli(match.startedAt()).toString()));
        var names = new ArrayList<String>();
        for (int i = 0; i < 4; i++) names.add(i < match.rules().players()
            ? match.participants().get(nativeSeat(match, i)).name() : "");
        root.put("name", names);
        root.put("ratingc", "PF" + match.rules().players());
        root.put("lobby", 0);
        root.put("dan", Collections.nCopies(4, ""));
        root.put("rate", Collections.nCopies(4, 0));
        root.put("sx", Collections.nCopies(4, ""));
        root.put("rule", Map.of("disp", (match.rules().sanma() ? "三" : "") + "南喰"
            + (match.redFives() == RedFives.NONE ? "" : "赤") + " · " + match.rules().name(),
            "aka51", match.rules().sanma() ? 0 : match.redFives().count(0),
            "aka52", match.redFives().count(1), "aka53", match.redFives().count(2)));
        root.put("log", match.hands().stream().map(hand -> hand(match, hand)).toList());
        if (match.complete()) {
            var last = match.hands().getLast();
            var score = new ArrayList<Number>();
            for (int i = 0; i < 4; i++) {
                if (i < match.rules().players()) {
                    int nativeSeat = nativeSeat(match, i);
                    score.add(last.finalSeats().get(nativeSeat).points());
                    score.add(last.finalScores().get(nativeSeat));
                } else { score.add(0); score.add(0); }
            }
            root.put("sc", score);
        }
        return root;
    }

    public static int tile(int physical) {
        int kind = Tile.kind(physical);
        return Tile.red(physical) ? 51 + kind / 9 : (kind / 9 + 1) * 10 + kind % 9 + 1;
    }

    private static int nativeSeat(ReplayMatch match, int external) {
        return (external + match.initialDealer()) % match.rules().players();
    }
    private static int externalSeat(ReplayMatch match, int nativeSeat) {
        return Math.floorMod(nativeSeat - match.initialDealer(), match.rules().players());
    }
    private static List<Integer> faces(List<Integer> physical) { return physical.stream().map(TenhouReplay::tile).toList(); }
    private static List<Integer> rotate(ReplayMatch match, List<Integer> values) {
        var result = new ArrayList<Integer>();
        for (int seat = 0; seat < 4; seat++) result.add(seat < match.rules().players() ? values.get(nativeSeat(match, seat)) : 0);
        return result;
    }

    private static List<Object> hand(ReplayMatch match, ReplayHand hand) {
        var result = new ArrayList<Object>();
        int players = match.rules().players();
        result.add(List.of(hand.round() / players * 4 + hand.round() % players, hand.honba(), hand.sticks()));
        result.add(rotate(match, hand.initialPoints()));
        result.add(faces(hand.dora()));
        result.add(faces(hand.ura()));
        var incoming = new ArrayList<List<Object>>();
        var outgoing = new ArrayList<List<Object>>();
        for (int seat = 0; seat < players; seat++) { incoming.add(new ArrayList<>()); outgoing.add(new ArrayList<>()); }
        for (var event : hand.events()) {
            if (event.kind() == ReplayHand.Kind.DORA || event.kind() == ReplayHand.Kind.RIICHI) continue;
            var draws = incoming.get(event.seat());
            var discards = outgoing.get(event.seat());
            switch (event.kind()) {
                case DRAW -> draws.add(tile(event.tile()));
                case DISCARD -> {
                    int symbol = event.tsumogiri() ? 60 : tile(event.tile());
                    discards.add(event.riichi() ? "r" + symbol : symbol);
                }
                case NUKI -> discards.add("f44");
                case MELD -> {
                    Meld meld = event.meld();
                    int who = externalSeat(match, event.seat());
                    int from = externalSeat(match, meld.fromSeat());
                    switch (meld.type()) {
                        case CHI, PON -> draws.add(call(meld.type(), who, from, meld.tiles(), meld.calledTile()));
                        case OPEN_KAN -> {
                            draws.add(call(meld.type(), who, from, meld.tiles(), meld.calledTile()));
                            discards.add(0);
                        }
                        case CLOSED_KAN -> {
                            var tiles = new ArrayList<>(faces(meld.tiles()));
                            Collections.sort(tiles);
                            discards.add("" + tiles.get(0) + tiles.get(1) + tiles.get(2) + "a" + tiles.get(3));
                        }
                        case ADDED_KAN -> {
                            var pon = new ArrayList<>(meld.tiles());
                            if (!pon.remove(Integer.valueOf(event.tile()))) throw new IllegalArgumentException("Missing added kan tile");
                            discards.add(call(Meld.Type.PON, who, from, pon, meld.calledTile())
                                .replace("p", "k" + tile(event.tile())));
                        }
                    }
                }
                default -> throw new IllegalStateException("Unexpected replay event");
            }
        }
        for (int seat = 0; seat < 4; seat++) {
            if (seat >= players) { result.add(List.of()); result.add(List.of()); result.add(List.of()); }
            else {
                int nativeSeat = nativeSeat(match, seat);
                result.add(faces(hand.initialHands().get(nativeSeat)));
                result.add(incoming.get(nativeSeat));
                result.add(outgoing.get(nativeSeat));
            }
        }
        result.add(result(match, hand));
        return result;
    }

    /** Marker position describes the source seat, and the following tile is the claimed tile. */
    static String call(Meld.Type type, int who, int from, List<Integer> tiles, int called) {
        var owned = new ArrayList<>(tiles);
        if (!owned.remove(Integer.valueOf(called))) throw new IllegalArgumentException("Missing called tile");
        owned.sort(Tile.ORDER);
        var tokens = new ArrayList<>(owned.stream().map(id -> Integer.toString(tile(id))).toList());
        int direction = Math.floorMod(who - from - 1, 4);
        if (type != Meld.Type.CHI && direction > 2) throw new IllegalArgumentException("A meld cannot call from itself");
        int index = type == Meld.Type.CHI ? 0 : type == Meld.Type.OPEN_KAN && direction == 2 ? 3 : direction;
        String marker = type == Meld.Type.CHI ? "c" : type == Meld.Type.PON ? "p" : "m";
        tokens.add(index, marker + tile(called));
        return String.join("", tokens);
    }

    private static List<Object> result(ReplayMatch match, ReplayHand hand) {
        var result = new ArrayList<Object>();
        if (hand.wins().isEmpty()) {
            result.add(switch (hand.result()) {
                case "exhaustive" -> "流局";
                case "nagashi" -> "流し満貫";
                case "nine_terminals" -> "九種九牌";
                case "four_winds" -> "四風連打";
                case "four_riichi" -> "四家立直";
                case "four_kans" -> "四開槓";
                case "triple_ron" -> "三家和";
                default -> throw new IllegalArgumentException("Unknown completed hand result: " + hand.result());
            });
            result.add(rotate(match, hand.deltas()));
            return result;
        }
        result.add("和了");
        for (var win : hand.wins()) {
            result.add(rotate(match, win.deltas()));
            var details = new ArrayList<Object>();
            int who = externalSeat(match, win.seat());
            details.add(who);
            details.add(win.from() < 0 ? who : externalSeat(match, win.from()));
            details.add(win.pao() < 0 ? who : externalSeat(match, win.pao()));
            details.add(score(win.score(), win.from() < 0, win.seat() == hand.dealer()));
            for (var yaku : win.yaku()) details.add(yakuName(yaku.name(), hand, win.seat(), match.rules().players())
                + "(" + (yaku.yakuman() ? "役満" : yaku.han() + "飜") + ")");
            if (win.dora() > 0) details.add("ドラ(" + win.dora() + "飜)");
            if (win.ura() > 0) details.add("裏ドラ(" + win.ura() + "飜)");
            if (win.redDora() > 0) details.add("赤ドラ(" + win.redDora() + "飜)");
            if (win.nukiDora() > 0) details.add("抜きドラ(" + win.nukiDora() + "飜)");
            result.add(details);
        }
        return result;
    }

    private static String score(HandScore score, boolean tsumo, boolean dealer) {
        int basic = score.ron() / (dealer ? 6 : 4);
        String prefix = basic >= 8000 ? score.yakuman() == 0 ? "数え役満"
            : score.yakuman() > 1 ? score.yakuman() + "倍役満" : "役満" : switch (basic) {
            case 6000 -> "三倍満";
            case 4000 -> "倍満";
            case 3000 -> "跳満";
            case 2000 -> "満貫";
            default -> score.fu() + "符" + score.han() + "飜";
        };
        return prefix + (!tsumo ? score.ron() + "点" : dealer ? score.tsumoChild() + "点∀"
            : score.tsumoChild() + "-" + score.tsumoDealer() + "点");
    }

    private static String yakuName(String name, ReplayHand hand, int seat, int players) {
        return switch (name) {
            case "SelfWind" -> "自風 " + "東南西北".charAt(Math.floorMod(seat - hand.dealer(), players));
            case "RoundWind" -> "場風 " + "東南西北".charAt(hand.round() / players);
            case "Tsumo" -> "門前清自摸和";
            case "Pinhu" -> "平和";
            case "Tanyao" -> "断幺九";
            case "Ipe" -> "一盃口";
            case "Haku" -> "役牌 白";
            case "Hatsu" -> "役牌 發";
            case "Chun" -> "役牌 中";
            case "Sanshoku" -> "三色同順";
            case "Ittsu" -> "一気通貫";
            case "Chanta" -> "混全帯幺九";
            case "Chitoi" -> "七対子";
            case "Toitoi" -> "対々和";
            case "Sananko" -> "三暗刻";
            case "Honroto" -> "混老頭";
            case "Sandoko" -> "三色同刻";
            case "Sankantsu" -> "三槓子";
            case "Shosangen" -> "小三元";
            case "Honitsu" -> "混一色";
            case "Junchan" -> "純全帯幺九";
            case "Ryanpe" -> "二盃口";
            case "Chinitsu" -> "清一色";
            case "Kokushi" -> "国士無双";
            case "Suanko" -> "四暗刻";
            case "Daisangen" -> "大三元";
            case "Tsuiso" -> "字一色";
            case "Shousushi" -> "小四喜";
            case "Lyuiso" -> "緑一色";
            case "Chinroto" -> "清老頭";
            case "Sukantsu" -> "四槓子";
            case "Churen" -> "九蓮宝燈";
            case "Daisushi" -> "大四喜";
            case "ChurenNineWaiting" -> "純正九蓮宝燈";
            case "SuankoTanki" -> "四暗刻単騎";
            case "KokushiThirteenWaiting" -> "国士無双十三面待ち";
            case "Richi" -> "立直";
            case "Ippatsu" -> "一発";
            case "Rinshan" -> "嶺上開花";
            case "Chankan" -> "槍槓";
            case "Haitei" -> "海底摸月";
            case "Houtei" -> "河底撈魚";
            case "WRichi" -> "ダブル立直";
            case "Tenhou" -> "天和";
            case "Chihou" -> "地和";
            case "Renhou" -> "人和";
            default -> throw new IllegalArgumentException("Unknown scoring yaku: " + name);
        };
    }
}
