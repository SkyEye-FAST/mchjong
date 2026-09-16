package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoringBridgeTest {
    @Test void pinfuRiichiRonAndTsumoHaveDifferentFuAndPayments() {
        var complete = TestHands.tiles("123456m456p22s789s");
        int tile = complete.getLast();
        var ron = HandAnalyzer.score(complete.subList(0,13), List.of(), tile, false, 1, 0, 0, List.of("Richi"), RuleSet.TENHOU_4);
        assertNotNull(ron);
        assertEquals(2, ron.han()); assertEquals(30, ron.fu()); assertEquals(2000, ron.ron());
        var tsumo = HandAnalyzer.score(complete, List.of(), tile, true, 1, 0, 0, List.of("Richi"), RuleSet.TENHOU_4);
        assertNotNull(tsumo);
        assertEquals(3, tsumo.han()); assertEquals(20, tsumo.fu());
        assertEquals(1300, tsumo.tsumoDealer()); assertEquals(700, tsumo.tsumoChild());
    }

    @Test void sevenPairsUsesTwentyFiveFu() {
        var hand = TestHands.tiles("1122m3344p5566s77z");
        var score = HandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0, 0, List.of(), RuleSet.TENHOU_4);
        assertNotNull(score); assertEquals(25, score.fu()); assertEquals(1600, score.ron());
        assertTrue(score.yaku().contains("Chitoi"));
    }

    @Test void bonusesAloneDoNotSatisfyTheOneYakuMinimum() {
        var meld = TestHands.meld(Meld.Type.CHI, "123m");
        var hand = TestHands.tiles("456p789s22z456m");
        assertNull(HandAnalyzer.score(hand.subList(0,10), List.of(meld), hand.getLast(), false, 2, 0, 8, List.of(), RuleSet.TENHOU_4));
    }

    @Test void doubleWindPairChangesFuOnlyForMLeague() {
        var melds = List.of(TestHands.meld(Meld.Type.PON, "555z"), TestHands.meld(Meld.Type.PON, "555p"));
        var hand = TestHands.tiles("123m46s11z5s");
        var tenhou = HandAnalyzer.score(hand.subList(0,7), melds, hand.getLast(), false, 0, 0, 0, List.of(), RuleSet.TENHOU_4);
        var league = HandAnalyzer.score(hand.subList(0,7), melds, hand.getLast(), false, 0, 0, 0, List.of(), RuleSet.M_LEAGUE);
        assertNotNull(tenhou); assertNotNull(league);
        assertEquals(40, tenhou.fu()); assertEquals(30, league.fu());
    }

    @Test void specialDoubleYakumanIsMahjongSoulOnly() {
        var hand = TestHands.tiles("19m19p19s1234567z1m");
        for (RuleSet rules : List.of(RuleSet.MAHJONG_SOUL_4, RuleSet.TENHOU_4, RuleSet.M_LEAGUE)) {
            var score = HandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0, 9, List.of(), rules);
            assertNotNull(score);
            assertEquals(rules.mahjongSoul() ? 2 : 1, score.yakuman(), rules.name());
            assertEquals(0, score.dora(), "Yakuman must not also charge dora");
        }
    }

    @Test void kiriageAndCountedYakumanAreSeparateOptions() {
        var hand = TestHands.tiles("123456m456p22s789s");
        for (int dora : new int[]{2,11}) {
            var tenhou = HandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0, dora, List.of("Richi"), RuleSet.TENHOU_4);
            var league = HandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0, dora, List.of("Richi"), RuleSet.M_LEAGUE);
            assertNotNull(tenhou); assertNotNull(league);
            assertEquals(dora == 2 ? 7700 : 32000, tenhou.ron());
            assertEquals(dora == 2 ? 8000 : 24000, league.ron());
        }
    }

    @Test void handAnalysisReportsWaitsAndReadyDiscards() {
        var hand = TestHands.tiles("123456m456p22s78s");
        assertEquals(Set.of(23,26), HandAnalyzer.waits(hand, List.of()));
        var drawn = TestHands.tiles("123456m456p22s78s5z");
        assertTrue(HandAnalyzer.tenpaiDiscards(drawn, List.of()).contains(Tile.WHITE));
        assertTrue(HandAnalyzer.bestDiscardKinds(drawn, List.of()).contains(Tile.WHITE));
    }

    @Test void stricterRiichiKanCheckDetectsAChangedSequenceInterpretation() {
        var hand = TestHands.tiles("111222333m444p5z");
        assertFalse(HandAnalyzer.riichiKanKeepsMelds(hand, List.of(), 0));
        assertTrue(HandAnalyzer.riichiKanKeepsMelds(hand, List.of(), 12));
    }
}
