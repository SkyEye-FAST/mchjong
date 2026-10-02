package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.engine.TimeControl;
import static org.junit.jupiter.api.Assertions.*;

class RiichiDepositsTest {
    private RiichiView view(RiichiPreset rules, int deposits, int declared, boolean manual) {
        var seats = new ArrayList<RiichiView.Seat>();
        for (int seat = 0; seat < rules.players(); seat++)
            seats.add(new RiichiView.Seat(false, "Player", true, false, false, 25000, List.of(), Tile.ABSENT,
                List.of(), List.of(), List.of(), (declared & 1 << seat) != 0, false, false));
        return new RiichiView(new UUID(0, 1), 1, 1, 1, rules.config(), RiichiView.Phase.TURN, 0, 0, 0, 0, deposits,
            0, 50, 0, List.of(), null, seats, List.of(), List.of(), "playing", List.of(), List.of(), List.of(),
            TimeControl.DEFAULT, List.of(), List.of(), top.skyeyefast.mchjong.engine.PlayerHandVisibility.SELF, false, null, manual ? new RiichiView.Handling(15, -1, 0, 1, 1, false) : null, null, false, 1, java.util.Map.of(), List.of(), 0, 0);
    }

    @Test void bothTablesDisplayTheEntirePotAndRetainCarriedDepositsForThreeAndFourPlayers() {
        for (var rules : List.of(RiichiPreset.MAHJONG_SOUL_3, RiichiPreset.MAHJONG_SOUL_4))
            for (boolean manual : new boolean[]{false, true}) for (int carried : new int[]{0, 1, 4, 12}) {
                int declared = (1 << rules.players()) - 1;
                var sticks = RiichiDeposits.sticks(view(rules, carried + rules.players(), declared, manual));
                assertEquals(carried + rules.players(), sticks.size());
                assertEquals(rules.players(), sticks.stream().filter(RiichiDeposits.Stick::declared).count());
                var positions = new java.util.HashSet<List<Integer>>();
                for (var stick : sticks) assertTrue(positions.add(List.of(stick.seat(), stick.layer())));
                var nextHand = RiichiDeposits.sticks(view(rules, carried + rules.players(), 0, manual));
                assertEquals(sticks.size(), nextHand.size());
                assertTrue(nextHand.stream().noneMatch(RiichiDeposits.Stick::declared));
            }
    }

    @Test void awardedPotIsEmptyEvenWhenTheWinningHandStillDisplaysRiichiFlags() {
        assertTrue(RiichiDeposits.sticks(view(RiichiPreset.MAHJONG_SOUL_4, 0, 15, false)).isEmpty());
    }

    @Test void lanesFitBetweenTheMachineCountersAndScoresWithoutIntersectingEachOther() {
        assertTrue(RiichiDeposits.LANE_Z - RiichiDeposits.HALF_WIDTH > RiichiDeposits.HALF_LENGTH);
        assertTrue(RiichiDeposits.LANE_Z + RiichiDeposits.HALF_WIDTH < .166);
        assertTrue(RiichiDeposits.LANE_Z - RiichiDeposits.HALF_WIDTH > .10);
    }
}
