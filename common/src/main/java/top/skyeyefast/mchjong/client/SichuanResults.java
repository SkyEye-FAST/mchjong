package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.SichuanView;
import top.skyeyefast.mchjong.engine.SichuanRules;
import top.skyeyefast.mchjong.engine.SichuanSettlement;
import top.skyeyefast.mchjong.engine.TableRoomView;

final class SichuanResults {
    record Row(Component text, List<Integer> tiles, int color) {
        Row(Component text, int color) { this(text, List.of(), color); }
        Row { tiles = List.copyOf(tiles); }
    }
    private SichuanResults() {}

    static List<Row> rows(SichuanView game, TableRoomView room) {
        return rows(game.rules(), game.result(), game.seats(), game.scores(), game.viewerSeat(),
            room.seats().stream().map(seat -> seat.participant().name()).toList());
    }
    static List<Row> rows(SichuanRules rules, SichuanSettlement.Result result, List<SichuanView.Seat> seats,
                          List<Integer> scores, int viewer, List<String> names) {
        if (result == null) return List.of();
        var rows = new ArrayList<Row>();
        rows.add(new Row(Component.translatable("sichuan.mchjong.score_heading"), MahjongUi.MUTED));
        for (int seat = 0; seat < 4; seat++) rows.add(new Row(Component.translatable("sichuan.mchjong.score_row",
            names.get(seat), signed(result.deltas().get(seat)), scores.get(seat)),
            seat == viewer ? MahjongUi.ACCENT : MahjongUi.TEXT));
        rows.add(new Row(Component.translatable(result.exhaustive() ? "sichuan.mchjong.exhaustive" : "sichuan.mchjong.three_winners"), MahjongUi.ACCENT));
        for (var win : result.wins()) {
            rows.add(new Row(Component.translatable("sichuan.mchjong.win_row", names.get(win.seat()),
                Component.translatable(win.selfDraw() ? "sichuan.mchjong.self_draw" : win.robbingKong()
                    ? "sichuan.mchjong.robbing_kong" : "sichuan.mchjong.discard_win"),
                names.get(win.supplier()), win.score().fan(), win.score().value()), MahjongUi.TEXT));
            var tiles = new ArrayList<>(seats.get(win.seat()).hand());
            if (!tiles.contains(win.tile())) tiles.add(win.tile());
            for (var meld : seats.get(win.seat()).melds()) tiles.addAll(meld.tiles());
            rows.add(new Row(Component.empty(), tiles, MahjongUi.TEXT));
            if (win.score().patterns().isEmpty()) rows.add(new Row(Component.translatable("sichuan.mchjong.fan.basic"), MahjongUi.MUTED));
            for (var pattern : win.score().patterns().stream().distinct().toList()) {
                long count = win.score().patterns().stream().filter(other -> other == pattern).count();
                String name = pattern == SichuanSettlement.Fan.ROOT && !rules.separateKongFan()
                    ? "root_with_kong" : pattern.name().toLowerCase(Locale.ROOT);
                rows.add(new Row(Component.translatable("sichuan.mchjong.fan." + name)
                    .append(" ×" + count), MahjongUi.MUTED));
            }
        }
        if (result.exhaustive()) for (int seat = 0; seat < 4; seat++) rows.add(new Row(Component.literal(names.get(seat) + " · ")
            .append(Component.translatable("sichuan.mchjong.draw." + result.drawStatus().get(seat).name().toLowerCase(Locale.ROOT))), MahjongUi.MUTED));
        rows.add(new Row(Component.translatable("sichuan.mchjong.ledger"), MahjongUi.ACCENT));
        for (var entry : result.ledger()) {
            var text = Component.literal("#" + (entry.id() + 1) + " ")
                .append(Component.translatable("sichuan.mchjong.payment." + entry.type().name().toLowerCase(Locale.ROOT)))
                .append(" · " + names.get(entry.payer()) + " → ")
                .append(entry.recipient() < 0 ? Component.translatable("sichuan.mchjong.competition") : Component.literal(names.get(entry.recipient())))
                .append(" · " + entry.amount());
            if (entry.relatedEntry() >= 0) text.append(" · ").append(Component.translatable("sichuan.mchjong.related", entry.relatedEntry() + 1));
            rows.add(new Row(text, entry.recipient() < 0 ? MahjongUi.NEGATIVE : MahjongUi.TEXT));
        }
        return List.copyOf(rows);
    }

    private static String signed(int amount) { return amount > 0 ? "+" + amount : Integer.toString(amount); }
}
