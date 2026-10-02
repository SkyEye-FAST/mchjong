package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.*;

/** Presentation primitives copied from an already authorized live view or replay frame. */
record TableBoardState(int viewerSeat, int players, int dealer, int round, int honba, int riichiSticks,
                       int turn, int remaining, List<Seat> seats, Focus focus,
                       boolean markTedashi, boolean dimTsumogiri, boolean layHandsOpen, Component roundLabel, Indicator indicator) {
    record Discard(int tile, boolean called, boolean riichi, boolean tsumogiri) {}
    record Focus(int tile) {}
    record Indicator(Component title, List<Component> seats) {
        Indicator { seats = List.copyOf(seats); }
    }
    private static Component wind(int wind) {
        return Component.translatable("wind.mchjong." + new String[]{"east", "south", "west", "north"}[wind] + ".short");
    }
    private static Component voidLabel(SichuanView.Seat seat) {
        return seat.won() ? Component.translatable("sichuan.mchjong.won").withStyle(style -> style.withColor(MahjongUi.POSITIVE))
            : seat.voidSuit() < 0 ? Component.literal("—") : Component.translatable("sichuan.mchjong.suit." + seat.voidSuit());
    }
    record Seat(int points, List<Integer> hand, int drawn, List<Meld> melds,
                List<Discard> river, List<Integer> norths, boolean exposed, MahjongVariant variant) {
        MeldLayout layout(Meld meld, int owner) { return MeldLayout.of(meld, owner, variant, exposed); }
        Seat {
            hand = List.copyOf(hand);
            melds = List.copyOf(melds);
            river = List.copyOf(river);
            norths = List.copyOf(norths);
        }
    }
    TableBoardState {
        seats = List.copyOf(seats);
        if (players < 3 || players > 4 || seats.size() != players) throw new IllegalArgumentException("Invalid table presentation");
        if (viewerSeat < -1 || viewerSeat >= players || dealer < 0 || dealer >= players) throw new IllegalArgumentException("Invalid table seat");
    }
    static Seat seat(RiichiView.Seat seat) {
        return new Seat(seat.points(), seat.hand(), seat.drawn(), seat.melds(), seat.river().stream()
            .map(d -> new Discard(d.tile(), d.called(), d.riichi(), d.tsumogiri())).toList(), seat.norths(), seat.exposed(), MahjongVariant.RIICHI);
    }
    private static Component round(int round, int players) {
        return Component.translatable("ui.mchjong.round.short", Component.translatable("wind.mchjong."
            + new String[]{"east", "south", "west", "north"}[Math.min(3, round / players)]), round % players + 1);
    }
    static TableBoardState live(RiichiView view) {
        return new TableBoardState(view.viewerSeat(), view.rules().players(), view.dealer(), view.round(), view.honba(),
            view.riichiSticks(), view.turn(), view.remaining(), view.seats().stream().map(TableBoardState::seat).toList(),
            view.focus() == null ? null : new Focus(view.focus().tile()), false, false, view.openHands(), round(view.round(), view.rules().players()), null);
    }
    static TableBoardState live(McrView view) { return mcr(view, view.viewerSeat()); }
    static TableBoardState mcr(McrView view, int viewer) {
        var seats = view.seats().stream().map(s -> new Seat(s.points(), s.hand(), s.drawn(), s.melds(), s.river().stream()
            .map(d -> new Discard(d.tile(), d.called(), false, d.tsumogiri())).toList(), s.flowers(), view.result() != null, MahjongVariant.MCR)).toList();
        return new TableBoardState(viewer, 4, view.dealer(), view.handNumber() - 1, -1, -1, view.turn(), view.remaining(), seats,
            view.focus() == null ? null : new Focus(view.focus().tile()), false, false, view.result() != null,
            Component.translatable("mcr.mchjong.hand", view.handNumber(), view.remaining()),
            new Indicator(Component.translatable("mcr.mchjong.indicator_round", wind(view.roundWind() - 27), view.handNumber()),
                java.util.stream.IntStream.range(0, 4).mapToObj(i -> wind(Math.floorMod(i - view.dealer(), 4))).toList()));
    }
    static TableBoardState live(SichuanView view) {
        var seats = java.util.stream.IntStream.range(0, 4).mapToObj(i -> {
            var s = view.seats().get(i);
            return new Seat(view.scores().get(i), s.hand(), s.drawn(), s.melds(), s.river().stream()
                .map(d -> new Discard(d.tile(), d.claimed(), false, false)).toList(), List.of(), view.result() != null, MahjongVariant.SICHUAN);
        }).toList();
        return new TableBoardState(view.viewerSeat(), 4, view.dealer(), view.handNumber() - 1, -1, -1, view.turn(), view.wall().remaining(), seats,
            view.focus() < 0 ? null : new Focus(view.focus()), false, false, view.result() != null,
            Component.translatable("sichuan.mchjong.hand", view.handNumber(), view.rules().matchHands(), view.wall().remaining()),
            new Indicator(Component.translatable("sichuan.mchjong.indicator_round", view.handNumber(), view.rules().matchHands()),
                view.seats().stream().map(TableBoardState::voidLabel).toList()));
    }
    TableBoardState replay(int viewer) {
        return new TableBoardState(viewer, players, dealer, round, honba, riichiSticks, turn, remaining, seats, focus,
            true, true, true, roundLabel, indicator);
    }
    static TableBoardState replay(SichuanReplayPlayback.Frame frame, int viewer) {
        var state = frame.state();
        var seats = java.util.stream.IntStream.range(0, 4).mapToObj(i -> {
            var p = frame.seats().get(i);
            return new Seat(frame.scores().get(i), p.hand(), p.drawn(), p.melds(), p.river().stream()
                .map(d -> new Discard(d.tile(), d.claimed(), false, false)).toList(), List.of(), true, MahjongVariant.SICHUAN);
        }).toList();
        return new TableBoardState(viewer, 4, state.wall().dealer(), 0, -1, -1, state.turn(), (int) state.wall().slots().stream().filter(t -> t != Tile.ABSENT).count(), seats,
            state.focus() < 0 ? null : new Focus(state.focus()), true, true, true, Component.empty(),
            new Indicator(Component.translatable("sichuan.mchjong.indicator_round", state.handNumber(), state.rules().matchHands()),
                frame.seats().stream().map(TableBoardState::voidLabel).toList()));
    }
    static TableBoardState replay(ReplayMatch match, ReplayHand hand, ReplayPlayback.Frame frame, int viewerSeat) {
        var event = frame.event();
        return new TableBoardState(viewerSeat, match.riichi().rules().players(), hand.dealer(), hand.round(), hand.honba(), hand.sticks(),
            event != null ? event.seat() : -1, -1, frame.seats().stream().map(TableBoardState::seat).toList(),
            event != null && event.tile() >= 0 ? new Focus(event.tile()) : null, true, true, true, round(hand.round(), match.riichi().rules().players()), null);
    }
}
