package top.skyeyefast.mchjong.engine;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McrSettlementTest {
    @Test void selfDrawAndDiscardUseTotalFanWithoutDealerMultipliers() {
        var score = new McrHandScore(11, 8, true, List.of());
        for (int winner : new int[]{0, 2}) {
            var self = new McrWinContext(McrWinContext.Method.SELF_DRAW, Tile.EAST, Tile.EAST,
                false, McrWinContext.KongWin.NONE, false, 3);
            var tsumo = McrSettlement.win(winner, -1, 0, self, score);
            assertEquals(57, tsumo.deltas().get(winner));
            assertEquals(0, tsumo.deltas().stream().mapToInt(Integer::intValue).sum());
            assertEquals(3, tsumo.deltas().stream().filter(delta -> delta == -19).count());
            var discard = new McrWinContext(McrWinContext.Method.DISCARD, Tile.EAST, Tile.EAST,
                false, McrWinContext.KongWin.NONE, false, 3);
            var ron = McrSettlement.win(winner, 1, 0, discard, score);
            assertEquals(35, ron.deltas().get(winner));
            assertEquals(-19, ron.deltas().get(1));
            assertEquals(2, ron.deltas().stream().filter(delta -> delta == -8).count());
            assertEquals(0, ron.deltas().stream().mapToInt(Integer::intValue).sum());
        }
    }

    @Test void belowMinimumUsesASeparatePenaltyAndExhaustionHasNoPayment() {
        var context = new McrWinContext(McrWinContext.Method.DISCARD, Tile.SOUTH, Tile.EAST,
            false, McrWinContext.KongWin.NONE, false, 8);
        assertThrows(IllegalArgumentException.class, () -> McrSettlement.win(1, 0, 0, context,
            new McrHandScore(15, 7, false, List.of())));
        var penalty = McrSettlement.wrongWin(1, 1, 0);
        assertEquals(List.of(10, -30, 10, 10), penalty.deltas());
        assertEquals(List.of(0, 0, 0, 0), new McrSettlement.Draw().deltas());
    }
}
