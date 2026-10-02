package top.skyeyefast.mchjong.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** A sealed MCR hand with physical opening and server-issued decisions. */
public record McrReplayHand(int number, List<Integer> initialPoints, List<Integer> wall, McrOpening opening,
                            List<List<Integer>> initialHands, List<Event> events, List<Decision> decisions,
                            McrSettlement.Win win, boolean draw, List<McrSettlement.Penalty> penalties,
                            List<Integer> finalPoints) {
    public enum Kind { DRAW, FLOWER_REPLACEMENT, DISCARD, RESPONSE, CHOW, PUNG, KONG, WIN, PASS, WRONG_WIN, SETTLEMENT }

    public record Event(Kind kind, int actionCursor, int seat, int tile) {
        public Event {
            Objects.requireNonNull(kind);
            if (actionCursor < 1 || seat < 0 || seat > 3 || tile < Tile.ABSENT || tile >= 144)
                throw new IllegalArgumentException("Invalid MCR replay event");
        }
    }

    public record Decision(int seat, List<McrAction> options, int selected) {
        public Decision {
            options = List.copyOf(options);
            if (seat < 0 || seat > 3 || options.isEmpty() || options.size() > 32 || selected < 0 || selected >= options.size())
                throw new IllegalArgumentException("Invalid MCR replay decision");
        }
    }

    public McrReplayHand {
        initialPoints = List.copyOf(initialPoints);
        wall = List.copyOf(wall);
        Objects.requireNonNull(opening);
        initialHands = initialHands.stream().map(List::copyOf).toList();
        events = List.copyOf(events);
        decisions = List.copyOf(decisions);
        penalties = List.copyOf(penalties);
        finalPoints = List.copyOf(finalPoints);
        if (number < 1 || number > 16 || opening.dealer() != (number - 1) % 4
            || initialPoints.size() != 4 || initialPoints.stream().mapToLong(Integer::longValue).sum() != 0
            || !Tile.validMcrSet(wall) || initialHands.size() != 4 ||
            events.isEmpty() || events.size() > 8192 || decisions.isEmpty() || decisions.size() > 4096
            || events.get(events.size() - 1).kind() != Kind.SETTLEMENT || events.get(events.size() - 1).actionCursor() != decisions.size()
            || (win == null) != draw || finalPoints.size() != 4 || finalPoints.stream().mapToLong(Integer::longValue).sum() != 0)
            throw new IllegalArgumentException("Invalid MCR replay hand");
        var dealt = new HashSet<Integer>();
        for (int seat = 0; seat < 4; seat++) {
            if (initialHands.get(seat).size() != (seat == opening.dealer() ? 14 : 13))
                throw new IllegalArgumentException("Invalid MCR opening hand size");
            for (int tile : initialHands.get(seat)) if (tile < 0 || tile >= 144 || !dealt.add(tile))
                throw new IllegalArgumentException("Invalid MCR opening tile");
        }
        int decisionCount = decisions.size();
        if (events.stream().anyMatch(event -> event.actionCursor() > decisionCount)
            || penalties.stream().anyMatch(penalty -> penalty.handNumber() != number))
            throw new IllegalArgumentException("Invalid MCR replay timeline");
    }
}
