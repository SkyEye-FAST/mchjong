package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.SichuanView;
import top.skyeyefast.mchjong.engine.TableRoomView;

final class SichuanResults {
    record Row(Component text, List<Integer> tiles, int color) {
        Row(Component text, int color) { this(text, List.of(), color); }
        Row { tiles = List.copyOf(tiles); }
    }
    private SichuanResults() {}

    static List<Row> rows(SichuanView game, TableRoomView room) {
        if (game.result() == null) return List.of();
        var rows = new ArrayList<Row>();
        rows.add(new Row(Component.translatable("sichuan.mchjong.score_heading"), MahjongUi.MUTED));
        for (int seat = 0; seat < 4; seat++) rows.add(new Row(Component.translatable("sichuan.mchjong.score_row",
            name(room, seat), signed(game.result().deltas().get(seat)), game.scores().get(seat)),
            seat == game.viewerSeat() ? MahjongUi.ACCENT : MahjongUi.TEXT));
        rows.add(new Row(Component.translatable(game.result().exhaustive() ? "sichuan.mchjong.exhaustive" : "sichuan.mchjong.three_winners"), MahjongUi.ACCENT));
        for (var win : game.result().wins()) {
            rows.add(new Row(Component.translatable("sichuan.mchjong.win_row", name(room, win.seat()),
                Component.translatable(win.selfDraw() ? "sichuan.mchjong.self_draw" : win.robbingKong()
                    ? "sichuan.mchjong.robbing_kong" : "sichuan.mchjong.discard_win"),
                name(room, win.supplier()), win.score().fan(), win.score().value()), MahjongUi.TEXT));
            var tiles = new ArrayList<>(game.seats().get(win.seat()).hand());
            if (!tiles.contains(win.tile())) tiles.add(win.tile());
            for (var meld : game.seats().get(win.seat()).melds()) tiles.addAll(meld.tiles());
            rows.add(new Row(Component.empty(), tiles, MahjongUi.TEXT));
            if (win.score().patterns().isEmpty()) rows.add(new Row(Component.translatable("sichuan.mchjong.fan.basic"), MahjongUi.MUTED));
            for (var pattern : win.score().patterns().stream().distinct().toList()) {
                long count = win.score().patterns().stream().filter(other -> other == pattern).count();
                rows.add(new Row(Component.translatable("sichuan.mchjong.fan." + pattern.name().toLowerCase(Locale.ROOT))
                    .append(" ×" + count), MahjongUi.MUTED));
            }
        }
        if (game.result().exhaustive()) for (int seat = 0; seat < 4; seat++) rows.add(new Row(Component.literal(name(room, seat) + " · ")
            .append(Component.translatable("sichuan.mchjong.draw." + game.result().drawStatus().get(seat).name().toLowerCase(Locale.ROOT))), MahjongUi.MUTED));
        rows.add(new Row(Component.translatable("sichuan.mchjong.ledger"), MahjongUi.ACCENT));
        for (var entry : game.result().ledger()) {
            var text = Component.literal("#" + (entry.id() + 1) + " ")
                .append(Component.translatable("sichuan.mchjong.payment." + entry.type().name().toLowerCase(Locale.ROOT)))
                .append(" · " + name(room, entry.payer()) + " → ")
                .append(entry.recipient() < 0 ? Component.translatable("sichuan.mchjong.competition") : Component.literal(name(room, entry.recipient())))
                .append(" · " + entry.amount());
            if (entry.relatedEntry() >= 0) text.append(" · ").append(Component.translatable("sichuan.mchjong.related", entry.relatedEntry() + 1));
            rows.add(new Row(text, entry.recipient() < 0 ? MahjongUi.NEGATIVE : MahjongUi.TEXT));
        }
        return List.copyOf(rows);
    }

    private static String name(TableRoomView room, int seat) { return room.seats().get(seat).participant().name(); }
    private static String signed(int amount) { return amount > 0 ? "+" + amount : Integer.toString(amount); }
}
