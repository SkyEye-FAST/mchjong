package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.*;

/** Rule adapters supply receipts; the result widget owns their common presentation. */
record TableResultState(Component heading, int viewerSeat, List<Seat> seats, List<Win> wins,
                        List<? extends Number> deltas, List<Integer> finalRanks, List<Double> finalUma,
                        List<Double> finalScores, List<Component> payments) {
    record Seat(long points, List<Integer> hand, List<Meld> melds, boolean exposed, Component name,
                boolean occupied, boolean bot, boolean entityBot, Component status, MahjongVariant variant) {
        MeldLayout layout(Meld meld, int owner) { return MeldLayout.of(meld, owner, variant, exposed); }
    }
    record Row(Component label, Component badge, String voice) {}
    record Win(int seat, int tile, Component source, Component score, List<Row> rows, Component grade,
               String limitVoice, List<Integer> indicators, List<Integer> ura) {}
    Component name(int seat) { return seats.get(seat).name(); }
    static TableResultState riichi(RiichiView view) {
        var seats = java.util.stream.IntStream.range(0, view.seats().size()).mapToObj(i -> {
            var s = view.seats().get(i);
            return new Seat(s.points(), s.hand(), s.melds(), s.exposed(), RiichiTableScreen.playerName(view, i),
                s.occupied(), s.bot(), s.entityBot(), Component.translatable(view.result().equals("exhaustive")
                    ? s.exposed() ? "ui.mchjong.tenpai" : "ui.mchjong.noten" : "ui.mchjong.no_winner"), MahjongVariant.RIICHI);
        }).toList();
        var wins = view.wins().stream().map(w -> {
            var rows = ScoreAnnouncements.rows(view, w).stream().map(r -> new Row(Component.translatable(r.translationKey()),
                r.han() > 0 ? Component.translatable("ui.mchjong.han", r.han()) : Component.empty(), r.voice())).toList();
            String limit = ScoreAnnouncements.limit(w.score(), w.seat() == view.dealer());
            return new Win(w.seat(), w.tile(), w.from() < 0 ? Component.translatable("result.mchjong." + view.result())
                : Component.translatable("ui.mchjong.ron_from", seats.get(w.from()).name()),
                w.score().yakuman() > 0 ? Component.translatable("ui.mchjong.yakuman", w.score().yakuman())
                    : Component.translatable("ui.mchjong.han_fu", w.score().han(), w.score().fu()), rows,
                limit == null ? Component.empty() : Component.translatable(ScoreAnnouncements.SUBTITLES.get(limit)), limit,
                indicators(view, false), view.seats().get(w.seat()).riichi() ? indicators(view, true) : List.of());
        }).toList();
        return new TableResultState(Component.translatable("result.mchjong." + view.result()), view.viewerSeat(), seats,
            wins, view.deltas(), view.finalRanks(), view.finalUma(), view.finalScores(), List.of());
    }
    TableResultState withViewer(int viewer) {
        return new TableResultState(heading, viewer, seats, wins, deltas, finalRanks, finalUma, finalScores, payments);
    }
    static TableResultState replay(ReplayMatch match, ReplayHand hand, int viewer) {
        var seats = java.util.stream.IntStream.range(0, hand.finalSeats().size()).mapToObj(i -> {
            var s = hand.finalSeats().get(i);
            return new Seat(s.points(), s.hand(), s.melds(), s.exposed(), Component.literal(match.participants().get(i).name()),
                s.occupied(), s.bot(), s.entityBot(), Component.translatable(hand.result().equals("exhaustive")
                    ? s.exposed() ? "ui.mchjong.tenpai" : "ui.mchjong.noten" : "ui.mchjong.no_winner"), MahjongVariant.RIICHI);
        }).toList();
        var wins = hand.wins().stream().map(w -> {
            var rows = new java.util.ArrayList<Row>();
            for (var y : w.yaku()) rows.add(new Row(Component.translatable(YakuCatalog.translationKey(y.name())),
                y.yakuman() ? Component.empty() : Component.translatable("ui.mchjong.han", y.han()), null));
            int bonus = w.dora() + w.ura() + w.redDora() + w.nukiDora();
            if (bonus > 0) rows.add(new Row(Component.translatable("ui.mchjong.dora", bonus), Component.empty(), null));
            String limit = ScoreAnnouncements.limit(w.score(), w.seat() == hand.dealer());
            return new Win(w.seat(), w.tile(), w.from() < 0 ? Component.translatable("result.mchjong." + hand.result())
                : Component.translatable("ui.mchjong.ron_from", seats.get(w.from()).name()),
                w.score().yakuman() > 0 ? Component.translatable("ui.mchjong.yakuman", w.score().yakuman())
                    : Component.translatable("ui.mchjong.han_fu", w.score().han(), w.score().fu()), List.copyOf(rows),
                limit == null ? Component.empty() : Component.translatable(ScoreAnnouncements.SUBTITLES.get(limit)), null,
                hand.dora(), hand.finalSeats().get(w.seat()).riichi() ? hand.ura() : List.of());
        }).toList();
        return new TableResultState(Component.translatable("result.mchjong." + hand.result()), viewer, seats, wins,
            hand.deltas(), hand.finalRanks(), List.of(), hand.finalScores(), List.of());
    }
    private static List<Integer> indicators(RiichiView v, boolean ura) {
        var tiles = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 5; i++) { int index = v.wall().size() - (ura ? 6 : 5) - 2 * i;
            if (index >= 0 && v.wall().get(index) >= 0) tiles.add(v.wall().get(index)); }
        return List.copyOf(tiles);
    }
    private static Seat seat(TableBoardState.Seat seat, TableParticipant p, Component status) {
        return new Seat(seat.points(), seat.hand(), seat.melds(), true, Component.literal(p.name()), p.id() != null,
            p.bot(), p.entityBot(), status, seat.variant());
    }
    static TableResultState mcr(McrView game, List<TableParticipant> participants) { return mcr(game, participants, game.viewerSeat()); }
    static TableResultState mcr(McrView game, List<TableParticipant> participants, int viewer) {
        var board = TableBoardState.mcr(game, viewer);
        var seats = java.util.stream.IntStream.range(0, 4).mapToObj(i -> seat(board.seats().get(i), participants.get(i),
            Component.translatable("mcr.mchjong.flowers", game.seats().get(i).flowers().size()))).toList();
        var wins = new java.util.ArrayList<Win>();
        if (game.result() instanceof McrSettlement.Win w) {
            var rows = w.score().fans().stream().map(f -> new Row(Component.translatable("mcr.mchjong.fan." + f.id().toLowerCase(Locale.ROOT))
                .append(" ×" + f.count()), Component.literal(Integer.toString(f.points())), null)).toList();
            wins.add(new Win(w.winner(), w.tile(), w.fromSeat() < 0 ? Component.translatable("mcr.mchjong.action.self_drawn")
                : Component.translatable("ui.mchjong.ron_from", seats.get(w.fromSeat()).name()),
                Component.translatable("mcr.mchjong.total_fan", w.score().totalFan()), rows, Component.empty(), null, List.of(), List.of()));
        }
        var penalties = game.penalties().stream().filter(p -> p.handNumber() == game.handNumber()).<Component>map(p ->
            Component.translatable("mcr.mchjong.penalty", participants.get(p.offender()).name(), p.deltas().get(p.offender()))).toList();
        return new TableResultState(Component.translatable(wins.isEmpty() ? "mcr.mchjong.draw_result" : "mcr.mchjong.results"),
            viewer, seats, wins, game.result().deltas(), ranks(seats), List.of(), List.of(), penalties);
    }
    static TableResultState sichuan(SichuanRules rules, SichuanSettlement.Result result, List<SichuanView.Seat> players,
                                   List<Integer> scores, int viewer, List<TableParticipant> participants) {
        var seats = java.util.stream.IntStream.range(0, 4).mapToObj(i -> {
            var s = players.get(i); var p = participants.get(i);
            return new Seat(scores.get(i), s.hand(), s.melds(), true, Component.literal(p.name()), p.id() != null, p.bot(), p.entityBot(),
                result.exhaustive() ? Component.translatable("sichuan.mchjong.draw." + result.drawStatus().get(i).name().toLowerCase(Locale.ROOT))
                    : SichuanTableScene.status(s), MahjongVariant.SICHUAN);
        }).toList();
        var wins = result.wins().stream().map(w -> {
            var rows = new java.util.ArrayList<Row>();
            if (w.score().patterns().isEmpty()) rows.add(new Row(Component.translatable("sichuan.mchjong.fan.basic"), Component.empty(), null));
            for (var f : w.score().patterns().stream().distinct().toList()) {
                long count = w.score().patterns().stream().filter(other -> other == f).count();
                String key = f == SichuanSettlement.Fan.ROOT && !rules.separateKongFan() ? "root_with_kong" : f.name().toLowerCase(Locale.ROOT);
                rows.add(new Row(Component.translatable("sichuan.mchjong.fan." + key), Component.literal("×" + count), null));
            }
            return new Win(w.seat(), w.tile(), Component.translatable(w.selfDraw() ? "sichuan.mchjong.self_draw" : w.robbingKong()
                ? "sichuan.mchjong.robbing_kong" : "sichuan.mchjong.discard_win").append("  ").append(seats.get(w.supplier()).name()),
                Component.translatable("sichuan.mchjong.fan_value", w.score().fan(), w.score().value()), List.copyOf(rows), Component.empty(), null, List.of(), List.of());
        }).toList();
        var payments = result.ledger().stream().<Component>map(entry -> {
            Component text = Component.translatable("sichuan.mchjong.payment_row", entry.id() + 1,
                Component.translatable("sichuan.mchjong.payment." + entry.type().name().toLowerCase(Locale.ROOT)), participants.get(entry.payer()).name(),
                entry.recipient() < 0 ? Component.translatable("sichuan.mchjong.competition") : seats.get(entry.recipient()).name(), entry.amount());
            if (entry.relatedEntry() >= 0) text = Component.translatable("ui.mchjong.annotation", text,
                Component.translatable("sichuan.mchjong.related", entry.relatedEntry() + 1));
            return text;
        }).toList();
        return new TableResultState(Component.translatable(result.exhaustive() ? "sichuan.mchjong.exhaustive" : "sichuan.mchjong.three_winners"),
            viewer, seats, wins, result.deltas(), ranks(seats), List.of(), List.of(), payments);
    }
    static TableResultState taiwan(TaiwanSession.View view) {
        var game = view.game(); var result = game.result(); var board = TableBoardState.live(view);
        var seats = java.util.stream.IntStream.range(0, 4).mapToObj(i -> {
            var s = board.seats().get(i); var p = view.participants().get(i);
            return new Seat(s.points(), s.hand(), s.melds(), i == game.recipient(), Component.literal(p.name()), true, false, false,
                Component.translatable("taiwan.mchjong.flowers", game.seats().get(i).flowers().size()), MahjongVariant.TAIWAN);
        }).toList();
        var wins = new java.util.ArrayList<Win>();
        if (result.winner() != null) {
            var awards = new java.util.ArrayList<>(result.awards());
            if (result.flowerAward() != null) awards.add(result.flowerAward());
            var rows = awards.stream().map(a -> new Row(Component.translatable("taiwan.mchjong.pattern." + a.pattern().name().toLowerCase(Locale.ROOT)),
                Component.translatable("taiwan.mchjong.tai", a.tai()), null)).toList();
            wins.add(new Win(result.winner(), Tile.ABSENT, result.supplier() == null ? Component.translatable("taiwan.mchjong.action.self_drawn")
                : Component.translatable("ui.mchjong.ron_from", seats.get(result.supplier()).name()),
                Component.translatable("taiwan.mchjong.tai", result.tai()), rows, Component.empty(), null, List.of(), List.of()));
        }
        var payments = result.transfers().stream().<Component>map(t -> Component.translatable("taiwan.mchjong.payment",
            seats.get(t.from()).name(), seats.get(t.to()).name(), t.amount(), t.base(), t.handTai(), t.dealerTai())).toList();
        return new TableResultState(Component.translatable(result.winner() == null ? "taiwan.mchjong.draw_result" : "taiwan.mchjong.results"),
            game.recipient(), seats, wins, result.deltas(), ranks(seats), List.of(), List.of(), payments);
    }
    private static List<Integer> ranks(List<Seat> seats) {
        return seats.stream().map(s -> 1 + (int) seats.stream().filter(other -> other.points() > s.points()).count()).toList();
    }
}
