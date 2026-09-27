package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotRiskReportTest {
    @Test void onlyTheTerminalDiscardReceivesRonLabelsAndRobbedDeclarationsDoNot() {
        var discard = new ReplayHand.Event(ReplayHand.Kind.DISCARD, 0, 0, null, false, true, true);
        var reach = new ReplayHand.Event(ReplayHand.Kind.RIICHI, 0, Tile.ABSENT, null, false, false, true);
        assertEquals(1, BotRiskReport.terminalDiscard(List.of(discard, discard, reach), Set.of(0)));
        assertEquals(-1, BotRiskReport.terminalDiscard(List.of(discard), Set.of()));
        assertEquals(-1, BotRiskReport.terminalDiscard(List.of(discard), Set.of(1)));
        var draw = new ReplayHand.Event(ReplayHand.Kind.DRAW, 0, 4, null, false, false, true);
        assertEquals(-1, BotRiskReport.terminalDiscard(List.of(discard, draw), Set.of(0)));
        var north = new ReplayHand.Event(ReplayHand.Kind.NUKI, 0, Tile.id(Tile.NORTH, 0, false), null, false, false, false);
        assertEquals(-1, BotRiskReport.terminalDiscard(List.of(discard, north), Set.of(0)));
        var kan = new ReplayHand.Event(ReplayHand.Kind.MELD, 0, 0, TestHands.meld(Meld.Type.ADDED_KAN, "1111m"), false, false, false);
        assertEquals(-1, BotRiskReport.terminalDiscard(List.of(discard, kan), Set.of(0)));
    }
}
