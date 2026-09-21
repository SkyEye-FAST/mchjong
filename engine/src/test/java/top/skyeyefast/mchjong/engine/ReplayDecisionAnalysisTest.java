package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayDecisionAnalysisTest {
    @Test void hiddenOpponentTilesNeverChangeVisibleCandidateCounts() {
        ReplayMatch hiddenWaits = fixture(true);
        ReplayMatch otherTiles = fixture(false);
        var first = hiddenWaits.hands().getFirst().decisions().getFirst();
        var second = otherTiles.hands().getFirst().decisions().getFirst();
        var hidden = ReplayDecisionAnalysis.analyze(hiddenWaits, 0, first).getFirst();
        var clear = ReplayDecisionAnalysis.analyze(otherTiles, 0, second).getFirst();

        assertNotNull(hidden);
        assertEquals(hidden, clear, "Opponent concealed tiles must not affect replay shape analysis");
        assertEquals(0, hidden.shanten());
        assertEquals(3, hidden.live());
        assertEquals(List.of(new ReplayDecisionAnalysis.Improvement(Tile.SOUTH, 3)), hidden.improving());
    }

    private ReplayMatch fixture(boolean hideSouths) {
        RuleConfig rules = RuleSet.TENHOU_4.config();
        var wall = wall(rules);
        var self = List.of(
            tile(0,0), tile(1,0), tile(2,0),
            tile(9,0), tile(10,0), tile(11,0),
            tile(18,0), tile(19,0), tile(20,0),
            tile(Tile.EAST,0), tile(Tile.EAST,1), tile(Tile.EAST,2), tile(Tile.SOUTH,0));
        int draw = tile(26, 0);
        Set<Integer> reserved = new HashSet<>(self);
        reserved.add(draw);
        var pool = new ArrayList<>(Tile.set(false, rules.redFives()));
        pool.removeIf(reserved::contains);
        var hiddenSouths = List.of(tile(Tile.SOUTH,1), tile(Tile.SOUTH,2), tile(Tile.SOUTH,3));

        var hands = new ArrayList<List<Integer>>();
        hands.add(self);
        int cursor = 0;
        for (int seat = 1; seat < 4; seat++) {
            var hand = new ArrayList<Integer>();
            if (seat == 1 && hideSouths) hand.addAll(hiddenSouths);
            while (hand.size() < 13) {
                int candidate = pool.get(cursor++);
                if (hideSouths && hiddenSouths.contains(candidate) || !hideSouths && Tile.kind(candidate) == Tile.SOUTH) continue;
                hand.add(candidate);
            }
            hands.add(List.copyOf(hand));
        }
        var seats = new ArrayList<TableView.Seat>();
        var participants = new ArrayList<ReplayMatch.Participant>();
        for (int seat = 0; seat < 4; seat++) {
            participants.add(new ReplayMatch.Participant(new UUID(8, seat + 1), "Player " + seat, false));
            seats.add(new TableView.Seat("Player " + seat, true, false, false, 25000, hands.get(seat), Tile.ABSENT,
                List.of(), List.of(), List.of(), false, false));
        }
        var events = List.of(new ReplayHand.Event(ReplayHand.Kind.DRAW, 0, draw, null, false, false, true));
        var decisions = List.of(new ReplayHand.Decision(0, 1,
            List.of(new Action(Action.Type.DISCARD, draw), new Action(Action.Type.DISCARD, self.getFirst())), 0));
        var replayHand = new ReplayHand(1, 0, 0, 0, 0, List.of(25000,25000,25000,25000), hands,
            List.of(tile(Tile.WHITE, 0)), wall, events, decisions, seats, List.of(), "exhaustive",
            List.of(0,0,0,0), List.of(tile(Tile.WHITE,0)), List.of(), List.of(), List.of());
        return new ReplayMatch(UUID.randomUUID(), UUID.randomUUID(), 1, 2, rules, 0, participants,
            List.of(replayHand), false, rules.redFives());
    }

    private static ReplayWall wall(RuleConfig rules) {
        var tiles = Tile.set(false, rules.redFives());
        int end = tiles.size();
        var replacements = new ArrayList<Integer>();
        for (int i = 0; i < rules.replacementCapacity(); i++) replacements.add(end - 1 - i % 4);
        return new ReplayWall(tiles, 0, replacements,
            List.of(end - 5, end - 7, end - 9, end - 11, end - 13),
            List.of(end - 6, end - 8, end - 10, end - 12, end - 14));
    }

    private static int tile(int kind, int copy) { return Tile.id(kind, copy, false); }
}
