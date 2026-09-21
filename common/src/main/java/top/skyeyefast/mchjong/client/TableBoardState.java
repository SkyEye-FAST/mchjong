package top.skyeyefast.mchjong.client;

import java.util.List;
import top.skyeyefast.mchjong.engine.ReplayHand;
import top.skyeyefast.mchjong.engine.ReplayMatch;
import top.skyeyefast.mchjong.engine.ReplayPlayback;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;

/** Immutable data needed by the screen-space table renderer. */
record TableBoardState(int viewerSeat, int players, int dealer, int round, int turn, int remaining,
                       int wallBreak, List<Integer> wall, List<TableView.Seat> seats, TableView.Focus focus,
                       boolean markTedashi, boolean dimTsumogiri) {
    TableBoardState {
        wall = List.copyOf(wall);
        seats = List.copyOf(seats);
        if (players < 3 || players > 4 || seats.size() != players) throw new IllegalArgumentException("Invalid table presentation");
        if (viewerSeat < 0 || viewerSeat >= players || dealer < 0 || dealer >= players) throw new IllegalArgumentException("Invalid table seat");
    }

    static TableBoardState live(TableView view) {
        return new TableBoardState(view.viewerSeat(), view.rules().players(), view.dealer(), view.round(), view.turn(),
            view.remaining(), view.wallBreak(), view.wall(), view.seats(), view.focus(), false, true);
    }

    static TableBoardState replay(ReplayMatch match, ReplayHand hand, ReplayPlayback.Frame frame, int viewerSeat) {
        var event = frame.event();
        int turn = event != null && event.seat() >= 0 ? event.seat() : -1;
        TableView.Focus focus = event != null && event.seat() >= 0 && event.tile() != Tile.ABSENT
            ? new TableView.Focus(event.seat(), event.tile(), event.kind() == ReplayHand.Kind.MELD || event.kind() == ReplayHand.Kind.NUKI, -1)
            : null;
        return new TableBoardState(viewerSeat, match.rules().players(), hand.dealer(), hand.round(), turn, -1,
            0, List.of(), frame.seats(), focus, true, true);
    }
}
