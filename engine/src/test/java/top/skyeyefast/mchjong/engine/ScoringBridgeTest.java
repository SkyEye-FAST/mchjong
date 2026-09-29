package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoringBridgeTest {
    @Test void minimumHanExcludesBonusesAndUsesEachWinningInterpretation() {
        var hand = TestHands.tiles("123456m456p22s789s");
        var yakuman = TestHands.tiles("19m19p19s1234567z1m");
        for (var preset : List.of(RiichiPreset.MAHJONG_SOUL_4, RiichiPreset.TENHOU_4)) {
            for (int minimum : List.of(1, 2, 4)) {
                var rules = preset.config().with(RiichiRuleOption.MIN_HAN, minimum);
                var ron = RiichiHandAnalyzer.score(hand.subList(0, 13), List.of(), hand.getLast(), false, 1, 0,
                    8, List.of("Richi"), rules);
                assertEquals(minimum <= 2, ron != null, "Eight bonus han cannot satisfy the threshold");
                var tsumo = RiichiHandAnalyzer.score(hand, List.of(), hand.getLast(), true, 1, 0,
                    0, List.of("Richi", "Ippatsu"), rules);
                assertEquals(minimum <= 2 || preset.mahjongSoul(), tsumo != null, "Tenhou excludes ippatsu from the minimum");
                assertNotNull(RiichiHandAnalyzer.score(yakuman.subList(0, 13), List.of(), yakuman.getLast(), false, 1, 0,
                    0, List.of(), rules));
            }
        }
        assertThrows(IllegalArgumentException.class, () -> RiichiPreset.TENHOU_4.config().with(RiichiRuleOption.MIN_HAN, 3));
    }

    @Test void customScoringOptionsOverridePresetsIndependently() {
        var hand = TestHands.tiles("123456m456p22s789s");
        var base = RiichiPreset.M_LEAGUE.config();
        var rules = base.with(RiichiRuleOption.KIRIAGE_MANGAN, 0).with(RiichiRuleOption.KAZOE_YAKUMAN, 1).with(RiichiRuleOption.IPPATSU, 0);
        for (int dora : new int[]{2, 11}) {
            var score = RiichiHandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0,
                dora, List.of("Richi", "Ippatsu"), rules);
            assertNotNull(score);
            assertEquals(dora == 2 ? 7700 : 32000, score.ron());
            assertFalse(score.yaku().contains("Ippatsu"));
        }
        assertTrue(base.ippatsu());
        assertTrue(base.kiriageMangan());
        assertFalse(base.kazoeYakuman());
        var open = TestHands.tiles("456m345p55s678s");
        var meld = TestHands.meld(Meld.Type.SEQUENCE, "234m");
        assertNotNull(RiichiHandAnalyzer.score(open.subList(0,10), List.of(meld), open.getLast(), false, 1, 0, 0, List.of(), rules));
        assertNull(RiichiHandAnalyzer.score(open.subList(0,10), List.of(meld), open.getLast(), false, 1, 0, 0, List.of(), rules.with(RiichiRuleOption.KUITAN, 0)));
        var honors = TestHands.tiles("11122233344455z");
        for (int compound : new int[]{0, 1}) {
            var custom = rules.with(RiichiRuleOption.COMPOUND_YAKUMAN, compound);
            var score = RiichiHandAnalyzer.score(honors.subList(0,13), List.of(), honors.getLast(), false, 1, 0, 0, List.of(), custom);
            assertEquals(compound == 0 ? 1 : 3, score.yakuman());
        }
    }

    @Test void pinfuRiichiRonAndTsumoHaveDifferentFuAndPayments() {
        var complete = TestHands.tiles("123456m456p22s789s");
        int tile = complete.getLast();
        var ron = RiichiHandAnalyzer.score(complete.subList(0,13), List.of(), tile, false, 1, 0, 0, List.of("Richi"), RiichiPreset.TENHOU_4.config());
        assertNotNull(ron);
        assertEquals(2, ron.han()); assertEquals(30, ron.fu()); assertEquals(2000, ron.ron());
        var tsumo = RiichiHandAnalyzer.score(complete, List.of(), tile, true, 1, 0, 0, List.of("Richi"), RiichiPreset.TENHOU_4.config());
        assertNotNull(tsumo);
        assertEquals(3, tsumo.han()); assertEquals(20, tsumo.fu());
        assertEquals(1300, tsumo.tsumoDealer()); assertEquals(700, tsumo.tsumoChild());
    }

    @Test void sevenPairsUsesTwentyFiveFu() {
        var hand = TestHands.tiles("1122m3344p5566s77z");
        var score = RiichiHandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0, 0, List.of(), RiichiPreset.TENHOU_4.config());
        assertNotNull(score); assertEquals(25, score.fu()); assertEquals(1600, score.ron());
        assertTrue(score.yaku().contains("Chitoi"));
    }

    @Test void bonusesAloneDoNotSatisfyTheOneYakuMinimum() {
        var meld = TestHands.meld(Meld.Type.SEQUENCE, "123m");
        var hand = TestHands.tiles("456p789s22z456m");
        assertNull(RiichiHandAnalyzer.score(hand.subList(0,10), List.of(meld), hand.getLast(), false, 2, 0, 8, List.of(), RiichiPreset.TENHOU_4.config()));
    }

    @Test void competitivePresetsUseTwoFuForTheDoubleWindPair() {
        var melds = List.of(TestHands.meld(Meld.Type.TRIPLET, "555z"), TestHands.meld(Meld.Type.TRIPLET, "555p"));
        var hand = TestHands.tiles("123m46s11z5s");
        var tenhou = RiichiHandAnalyzer.score(hand.subList(0,7), melds, hand.getLast(), false, 0, 0, 0, List.of(), RiichiPreset.TENHOU_4.config());
        assertNotNull(tenhou); assertEquals(40, tenhou.fu());
        for (var rules : List.of(RiichiPreset.M_LEAGUE, RiichiPreset.JPML_A, RiichiPreset.WRC)) {
            var score = RiichiHandAnalyzer.score(hand.subList(0,7), melds, hand.getLast(), false, 0, 0, 0, List.of(), rules.config());
            assertNotNull(score); assertEquals(30, score.fu());
        }
    }

    @Test void specialDoubleYakumanIsMahjongSoulOnly() {
        var hand = TestHands.tiles("19m19p19s1234567z1m");
        for (RiichiPreset rules : RiichiPreset.values()) {
            var score = RiichiHandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0, 9, List.of(), rules.config());
            assertNotNull(score);
            assertEquals(rules.mahjongSoul() ? 2 : 1, score.yakuman(), rules.name());
            assertEquals(score.yakuman() * 32000, score.ron(), rules.name());
            assertEquals(0, score.dora(), "Yakuman must not also charge dora");
        }
    }

    @Test void kiriageAndCountedYakumanAreSeparateOptions() {
        var hand = TestHands.tiles("123456m456p22s789s");
        for (int dora : new int[]{2,11}) {
            for (var rules : RiichiPreset.values()) {
                var score = RiichiHandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0, dora, List.of("Richi"), rules.config());
                assertNotNull(score);
                int expected = dora == 2 ? rules.kiriageMangan() ? 8000 : 7700 : rules.mLeague() ? 24000 : 32000;
                assertEquals(expected, score.ron(), rules.name());
                assertEquals(0, score.yakuman(), "Counted limits do not invoke natural-yakuman responsibility");
            }
        }
        var sixtyFu = TestHands.tiles("111m999p234s77z11s1s");
        for (var rules : RiichiPreset.values()) {
            var score = RiichiHandAnalyzer.score(sixtyFu.subList(0,13), List.of(), sixtyFu.getLast(), false, 1, 0,
                2, List.of("Richi"), rules.config());
            assertNotNull(score);
            assertEquals(3, score.han());
            assertEquals(60, score.fu());
            assertEquals(rules.kiriageMangan() ? 8000 : 7700, score.ron(), rules.name());
            var ippatsu = RiichiHandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0,
                0, List.of("Richi", "Ippatsu"), rules.config());
            assertEquals(rules.ippatsu(), ippatsu.yaku().contains("Ippatsu"));
        }
    }

    @Test void wrcRenhouIsAnAlternativeManganNotAnAdditiveYaku() {
        var hand = TestHands.tiles("123456m456p22s789s");
        for (int dora : new int[]{0, 3, 7}) {
            var score = RiichiHandAnalyzer.score(hand.subList(0,13), List.of(), hand.getLast(), false, 1, 0,
                dora, List.of("Renhou"), RiichiPreset.WRC.config());
            assertNotNull(score);
            assertEquals(dora == 7 ? 16000 : 8000, score.ron());
            assertEquals(dora != 7, score.yaku().contains("Renhou"));
            if (dora != 7) assertEquals(0, score.dora());
        }
    }

    @Test void handAnalysisReportsWaitsAndReadyDiscards() {
        var hand = TestHands.tiles("123456m456p22s78s");
        assertEquals(Set.of(23,26), RiichiHandAnalyzer.waits(hand, List.of()));
        var drawn = TestHands.tiles("123456m456p22s78s5z");
        assertTrue(RiichiHandAnalyzer.tenpaiDiscards(drawn, List.of()).contains(Tile.WHITE));
        assertTrue(RiichiHandAnalyzer.bestDiscardKinds(drawn, List.of()).contains(Tile.WHITE));
    }

    @Test void stricterRiichiKanCheckDetectsAChangedSequenceInterpretation() {
        var hand = TestHands.tiles("111222333m444p5z");
        assertFalse(RiichiHandAnalyzer.riichiKanKeepsMelds(hand, List.of(), 0));
        assertTrue(RiichiHandAnalyzer.riichiKanKeepsMelds(hand, List.of(), 12));
        var quad = new Meld(Meld.Type.CONCEALED_QUAD, TestHands.tiles("2222s"), 0, Tile.ABSENT);
        assertFalse(RiichiHandAnalyzer.riichiKanKeepsMelds(TestHands.tiles("123456p1113s"), List.of(quad), 18),
            "WRC's fifth-copy example still has a pair and middle-wait interpretation");
    }
}
