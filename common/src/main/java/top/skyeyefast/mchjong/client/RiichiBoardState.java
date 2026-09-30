package top.skyeyefast.mchjong.client;

import java.util.List;
import top.skyeyefast.mchjong.engine.ReplayHand;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.ReplayPlayback;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.Tile;

/** Immutable data needed by the screen-space table renderer. */
record RiichiBoardState(int viewerSeat, int players, int dealer, int round, int honba, int riichiSticks, int turn, int remaining,
                       List<RiichiView.Seat> seats, RiichiView.Focus focus,
                       boolean markTedashi, boolean dimTsumogiri, boolean layHandsOpen) {
    RiichiBoardState {
        seats = List.copyOf(seats);
        if (players < 3 || players > 4 || seats.size() != players) throw new IllegalArgumentException("Invalid table presentation");
        if (viewerSeat < 0 || viewerSeat >= players || dealer < 0 || dealer >= players) throw new IllegalArgumentException("Invalid table seat");
    }

    static RiichiBoardState live(RiichiView view) {
        return new RiichiBoardState(view.viewerSeat(), view.rules().players(), view.dealer(), view.round(), view.honba(),
            view.riichiSticks(), view.turn(), view.remaining(), view.seats(), view.focus(), false, false,
            view.openHands());
    }

    static RiichiBoardState replay(ReplayMatch match, ReplayHand hand, ReplayPlayback.Frame frame, int viewerSeat) {
        var event = frame.event();
        int turn = event != null && event.seat() >= 0 ? event.seat() : -1;
        RiichiView.Focus focus = event != null && event.seat() >= 0 && event.tile() != Tile.ABSENT
            ? new RiichiView.Focus(event.seat(), event.tile(), event.kind() == ReplayHand.Kind.MELD || event.kind() == ReplayHand.Kind.NUKI, -1)
            : null;
        return new RiichiBoardState(viewerSeat, match.rules().players(), hand.dealer(), hand.round(), hand.honba(), hand.sticks(),
            turn, -1, frame.seats(), focus, true, true, true);
    }
}
