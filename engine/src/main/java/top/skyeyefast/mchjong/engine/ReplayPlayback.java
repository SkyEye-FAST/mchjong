package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

/** A read-only timeline. It never sends game actions or exposes any still-playing hand. */
public final class ReplayPlayback {
    private ReplayPlayback() {}
    public record Frame(List<TableView.Seat> seats, List<Integer> dora, int cursor, int steps,
                        boolean settled, ReplayHand.Event event) {
        public Frame { seats = List.copyOf(seats); dora = List.copyOf(dora); }
    }

    private static final class Seat {
        final List<Integer> hand;
        final List<Meld> melds = new ArrayList<>();
        final List<Discard> river = new ArrayList<>();
        final List<Integer> norths = new ArrayList<>();
        int points, drawn = Tile.ABSENT;
        boolean riichi, nextSideways;
        Seat(List<Integer> hand, int points) { this.hand = new ArrayList<>(hand); this.points = points; }
        void remove(int tile) {
            if (!hand.remove(Integer.valueOf(tile))) throw new IllegalArgumentException("Replay uses an unowned tile");
        }
    }

    public static Frame at(ReplayMatch match, int handIndex, int requested) {
        ReplayHand hand = match.hands().get(handIndex);
        int steps = hand.events().size() + 1;
        int cursor = Math.clamp(requested, 0, steps);
        if (cursor == steps) return new Frame(hand.finalSeats(), hand.dora(), cursor, steps, true, null);
        var seats = new ArrayList<Seat>();
        for (int i = 0; i < match.rules().players(); i++) seats.add(new Seat(hand.initialHands().get(i), hand.initialPoints().get(i)));
        var dora = new ArrayList<>(hand.initialDora());
        for (int i = 0; i < cursor; i++) {
            ReplayHand.Event event = hand.events().get(i);
            if (event.kind() == ReplayHand.Kind.DORA) { dora.add(event.tile()); continue; }
            Seat seat = seats.get(event.seat());
            switch (event.kind()) {
                case DRAW -> {
                    if (seat.hand.contains(event.tile()) || seat.hand.size() >= 14)
                        throw new IllegalArgumentException("Invalid replay draw");
                    seat.hand.add(event.tile()); seat.drawn = event.tile();
                }
                case DISCARD -> {
                    seat.remove(event.tile());
                    seat.drawn = Tile.ABSENT;
                    seat.river.add(new Discard(event.tile(), event.riichi() || seat.nextSideways, false, event.tsumogiri()));
                    seat.nextSideways = false;
                }
                case RIICHI -> { seat.riichi = true; seat.points -= 1000; }
                case NUKI -> {
                    if (event.committed()) {
                        seat.remove(event.tile()); seat.norths.add(event.tile()); seat.drawn = Tile.ABSENT;
                    }
                }
                case MELD -> {
                    if (!event.committed()) continue;
                    Meld meld = event.meld();
                    if (meld.type() == Meld.Type.ADDED_KAN) {
                        seat.remove(event.tile());
                        int pon = -1;
                        for (int m = 0; m < seat.melds.size(); m++)
                            if (seat.melds.get(m).type() == Meld.Type.PON && seat.melds.get(m).kind() == meld.kind()) pon = m;
                        if (pon < 0) throw new IllegalArgumentException("Added kan has no pon");
                        seat.melds.set(pon, meld);
                    } else {
                        for (int tile : meld.tiles()) if (meld.closed() || tile != meld.calledTile()) seat.remove(tile);
                        seat.melds.add(meld);
                        if (!meld.closed()) {
                            Seat source = seats.get(meld.fromSeat());
                            if (source.river.isEmpty() || source.river.getLast().tile() != meld.calledTile())
                                throw new IllegalArgumentException("Meld does not claim the last discard");
                            Discard discard = source.river.getLast();
                            source.river.set(source.river.size() - 1, discard.markCalled());
                            source.nextSideways = discard.riichi();
                        }
                    }
                    seat.drawn = Tile.ABSENT;
                }
                default -> throw new IllegalStateException("Unexpected replay event");
            }
        }
        var view = new ArrayList<TableView.Seat>();
        for (int i = 0; i < seats.size(); i++) {
            Seat seat = seats.get(i);
            seat.hand.sort(Tile.ORDER);
            if (seat.drawn >= 0 && seat.hand.remove(Integer.valueOf(seat.drawn))) seat.hand.add(seat.drawn);
            var player = match.participants().get(i);
            view.add(new TableView.Seat(player.name(), true, player.bot(), false, seat.points, seat.hand, seat.drawn,
                seat.melds, seat.river, seat.norths, seat.riichi, true));
        }
        return new Frame(view, dora, cursor, steps, false, cursor == 0 ? null : hand.events().get(cursor - 1));
    }
}
