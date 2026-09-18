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

    @Test void competitivePresetsUseTwoFuForTheDoubleWindPair() {
        var melds = List.of(TestHands.meld(Meld.Type.PON, "555z"), TestHands.meld(Meld.Type.PON, "555p"));
        var hand = TestHands.tiles("123m46s11z5s");
        var tenhou = HandAnalyzer.score(hand.subList(0,7), melds, hand.getLast(), false, 0, 0, 0, List.of(), RuleSet.TENHOU_4);
        assertNotNull(tenhou); assertEquals(40, tenhou.fu());
        for (var rules : List.of(RuleSet.M_LEAGUE, RuleSet.JPML_A, RuleSet.WRC)) {
            var score = HandAnalyzer.score(hand.subList(0,7), melds, hand.getLast(), false, 0, 0, 0, List.of(), rules);
            assertNotNull(score); assertEquals(30, score.fu());
        }
    }

    @Test void specialDoubleYakumanIsMahjongSoulOnly() {
        var hand = TestHands.tiles("19m19p19s1234567z1m");
        for (RuleSet rules : RuleSet.values()) {
            var score = HandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0, 9, List.of(), rules);
            assertNotNull(score);
            assertEquals(rules.mahjongSoul() ? 2 : 1, score.yakuman(), rules.name());
            assertEquals(score.yakuman() * 32000, score.ron(), rules.name());
            assertEquals(0, score.dora(), "Yakuman must not also charge dora");
        }
    }

    @Test void kiriageAndCountedYakumanAreSeparateOptions() {
        var hand = TestHands.tiles("123456m456p22s789s");
        for (int dora : new int[]{2,11}) {
            for (var rules : RuleSet.values()) {
                var score = HandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0, dora, List.of("Richi"), rules);
                assertNotNull(score);
                int expected = dora == 2 ? rules.kiriageMangan() ? 8000 : 7700 : rules.mLeague() ? 24000 : 32000;
                assertEquals(expected, score.ron(), rules.name());
                assertEquals(0, score.yakuman(), "Counted limits do not invoke natural-yakuman responsibility");
            }
        }
        var sixtyFu = TestHands.tiles("111m999p234s77z11s1s");
        for (var rules : RuleSet.values()) {
            var score = HandAnalyzer.score(sixtyFu.subList(0,13), List.of(), sixtyFu.getLast(), false, 1, 0,
                2, List.of("Richi"), rules);
            assertNotNull(score);
            assertEquals(3, score.han());
            assertEquals(60, score.fu());
            assertEquals(rules.kiriageMangan() ? 8000 : 7700, score.ron(), rules.name());
            var ippatsu = HandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0,
                0, List.of("Richi", "Ippatsu"), rules);
            assertEquals(rules.ippatsu(), ippatsu.yaku().contains("Ippatsu"));
        }
    }

    @Test void wrcRenhouIsAnAlternativeManganNotAnAdditiveYaku() {
        var hand = TestHands.tiles("123456m456p22s789s");
        for (int dora : new int[]{0, 3, 7}) {
            var score = HandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0,
                dora, List.of("Renhou"), RuleSet.WRC);
            assertNotNull(score);
            assertEquals(dora == 7 ? 16000 : 8000, score.ron());
            assertEquals(dora != 7, score.yaku().contains("Renhou"));
            if (dora != 7) assertEquals(0, score.dora());
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
        var quad = new Meld(Meld.Type.CLOSED_KAN, TestHands.tiles("2222s"), 0, Tile.ABSENT);
        assertFalse(HandAnalyzer.riichiKanKeepsMelds(TestHands.tiles("123456p1113s"), List.of(quad), 18),
            "WRC's fifth-copy example still has a pair and middle-wait interpretation");
    }
}
