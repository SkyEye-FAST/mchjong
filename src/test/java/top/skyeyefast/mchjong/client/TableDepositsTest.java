package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.engine.TimeControl;
import static org.junit.jupiter.api.Assertions.*;

class TableDepositsTest {
    private TableView view(RuleSet rules, int deposits, int declared, boolean manual) {
        var seats = new ArrayList<TableView.Seat>();
        for (int seat = 0; seat < rules.players(); seat++)
            seats.add(new TableView.Seat("Player", true, false, false, 25000, List.of(), Tile.ABSENT,
                List.of(), List.of(), List.of(), (declared & 1 << seat) != 0, false));
        return new TableView(new UUID(0, 1), 1, 1, 1, rules.config(), Game.Phase.TURN, 0, 0, 0, 0, deposits,
            0, 50, 0, List.of(), null, seats, List.of(), List.of(), "playing", List.of(), List.of(),
            TimeControl.DEFAULT, List.of(), List.of(), false, null, manual ? new TableView.Handling(15, -1, 0) : null, null);
    }

    @Test void bothTablesDisplayTheEntirePotAndRetainCarriedDepositsForThreeAndFourPlayers() {
        for (var rules : List.of(RuleSet.MAHJONG_SOUL_3, RuleSet.MAHJONG_SOUL_4))
            for (boolean manual : new boolean[]{false, true}) for (int carried : new int[]{0, 1, 4, 12}) {
                int declared = (1 << rules.players()) - 1;
                var sticks = TableDeposits.sticks(view(rules, carried + rules.players(), declared, manual));
                assertEquals(carried + rules.players(), sticks.size());
                assertEquals(rules.players(), sticks.stream().filter(TableDeposits.Stick::declared).count());
                var positions = new java.util.HashSet<List<Integer>>();
                for (var stick : sticks) assertTrue(positions.add(List.of(stick.seat(), stick.layer())));
                var nextHand = TableDeposits.sticks(view(rules, carried + rules.players(), 0, manual));
                assertEquals(sticks.size(), nextHand.size());
                assertTrue(nextHand.stream().noneMatch(TableDeposits.Stick::declared));
            }
    }

    @Test void awardedPotIsEmptyEvenWhenTheWinningHandStillDisplaysRiichiFlags() {
        assertTrue(TableDeposits.sticks(view(RuleSet.MAHJONG_SOUL_4, 0, 15, false)).isEmpty());
    }

    @Test void lanesFitBetweenTheMachineCountersAndScoresWithoutIntersectingEachOther() {
        assertTrue(TableDeposits.LANE_Z - TableDeposits.HALF_WIDTH > TableDeposits.HALF_LENGTH);
        assertTrue(TableDeposits.LANE_Z + TableDeposits.HALF_WIDTH < .166);
        assertTrue(TableDeposits.LANE_Z - TableDeposits.HALF_WIDTH > .10);
    }
}
