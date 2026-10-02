package top.skyeyefast.mchjong.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record SichuanReplayHand(int number, List<Integer> initialPoints, SichuanWall.State opening,
                                List<List<Integer>> initialHands, List<Integer> voidSuits,
                                List<Decision> decisions, List<Event> events,
                                SichuanSettlement.Result result, List<Integer> finalPoints) {
    public enum Kind { VOID_SUIT, VOID_SUITS, DRAW, DISCARD, RESPONSE, PASS, ADDED_KONG, PUNG, KONG, WIN, PAYMENT, SETTLEMENT }

    public record Event(Kind kind, int actionCursor, int seat, int tile, int ledgerId) {
        public Event {
            Objects.requireNonNull(kind);
            if (actionCursor < 1 || seat < 0 || seat > 3 || tile < Tile.ABSENT || tile >= 108
                || (kind == Kind.PAYMENT ? ledgerId < 0 : ledgerId != -1))
                throw new IllegalArgumentException("Invalid Sichuan replay event");
        }
    }

    public record Decision(int seat, List<SichuanAction> options, int selected, boolean adjudication) {
        public Decision {
            options = List.copyOf(options);
            if (seat < 0 || seat > 3 || (adjudication ? !options.isEmpty() || selected != -1
                : options.isEmpty() || options.size() > 32 || selected < 0 || selected >= options.size()))
                throw new IllegalArgumentException("Invalid Sichuan replay decision");
        }
    }

    public SichuanReplayHand {
        initialPoints = List.copyOf(initialPoints); finalPoints = List.copyOf(finalPoints);
        Objects.requireNonNull(opening); Objects.requireNonNull(result);
        initialHands = initialHands.stream().map(List::copyOf).toList();
        voidSuits = List.copyOf(voidSuits); decisions = List.copyOf(decisions); events = List.copyOf(events);
        if (number < 1 || number > 64 || initialPoints.size() != 4 || finalPoints.size() != 4
            || opening.cursor() != 0 || !Tile.validSichuanSet(opening.slots()) || initialHands.size() != 4
            || voidSuits.size() != 4 || voidSuits.stream().anyMatch(suit -> suit < 0 || suit > 2)
            || decisions.isEmpty() || decisions.size() > 4096 || events.isEmpty() || events.size() > 8192
            || events.get(events.size() - 1).kind() != Kind.SETTLEMENT
            || events.get(events.size() - 1).actionCursor() != decisions.size())
            throw new IllegalArgumentException("Invalid sealed Sichuan hand");
        SichuanWall.restore(opening);
        var dealt = new HashSet<Integer>();
        for (int seat = 0; seat < 4; seat++) {
            if (initialHands.get(seat).size() != (seat == opening.dealer() ? 14 : 13)
                || finalPoints.get(seat) != Math.addExact(initialPoints.get(seat), result.deltas().get(seat)))
                throw new IllegalArgumentException("Invalid Sichuan replay points or deal");
            for (int tile : initialHands.get(seat)) if (tile < 0 || tile >= 108 || !dealt.add(tile))
                throw new IllegalArgumentException("Invalid Sichuan opening tile");
        }
        int cursor = 0;
        for (var event : events) {
            if (event.actionCursor() < cursor || event.actionCursor() > decisions.size()
                || event.kind() == Kind.PAYMENT && event.ledgerId() >= result.ledger().size())
                throw new IllegalArgumentException("Invalid Sichuan replay event order");
            cursor = event.actionCursor();
        }
    }
}
