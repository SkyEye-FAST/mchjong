package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class YakuSelectionTest {
    private static final YakuSet ANCIENT = BuiltinYakuSets.MAHJONG_SOUL_ANCIENT;
    private record Example(String id, String hand, boolean tsumo, List<String> extra, List<Meld> melds, int han) {}
    private static Example example(String id, String hand, boolean tsumo, int han, String... extra) {
        return new Example(id, hand, tsumo, List.of(extra), List.of(), han);
    }
    private static RiichiRules only(String id) {
        return RiichiPreset.MAHJONG_SOUL_4.config().withYakuSelection(new YakuSelection(MahjongVariant.RIICHI,
            List.of(ANCIENT), YakuSelection.nativeIds(MahjongVariant.RIICHI), Set.of("mahjongsoul:" + id)));
    }
    private static HandScore score(Example example, RiichiRules rules) {
        var hand = TestHands.tiles(example.hand());
        return RiichiHandAnalyzer.score(hand, example.melds(), hand.getLast(), example.tsumo(), 1, 0, 0, example.extra(), rules);
    }

    @Test void allFourteenAncientAwardsScoreThroughTheDeclarativeSchema() {
        var examples = List.of(
            example("tsubame_gaeshi", "123456m456p22s789s", false, 1, "RiichiDiscard"),
            example("kanburi", "123456m456p22s789s", false, 1, "AfterKongDiscard"),
            new Example("four_open_melds", "55p", false, List.of(), List.of(TestHands.meld(Meld.Type.SEQUENCE, "123m"),
                TestHands.meld(Meld.Type.SEQUENCE, "456m"), TestHands.meld(Meld.Type.SEQUENCE, "789s"), TestHands.meld(Meld.Type.TRIPLET, "111z")), 1),
            example("uumen_sai", "123m456p789s111z55z", false, 2),
            example("sanrenkou", "111222333m456p55s", false, 2),
            example("isshoku_sanjun", "111222333m456p55s", false, 3),
            example("ippin_moyue", "123456m123s55z23p1p", true, 5, "Haitei"),
            example("chuupin_raoyui", "123456m123s55z78p9p", false, 5, "Houtei"),
            example("renhou", "123456m456p22s789s", false, 13, "Renhou"),
            example("daisuurin", "22334455667788m", false, 13),
            example("daisharin", "22334455667788p", false, 13),
            example("daichikurin", "22334455667788s", false, 13),
            example("ishino_uenimo_sannen", "123456m456p22s789s", true, 13, "WRichi", "Haitei"),
            example("dai_chisei", "11223344556677z", false, 26));
        assertEquals(14, ANCIENT.awards().size());
        for (var example : examples) {
            var result = score(example, only(example.id()));
            assertNotNull(result, example.id());
            assertEquals(example.han(), result.han(), example.id());
            assertEquals(List.of("mahjongsoul:" + example.id()), result.yaku().stream().map(HandScore.Yaku::name).toList());
            assertNull(score(example, only(example.id()).withYakuSelection(new YakuSelection(MahjongVariant.RIICHI,
                List.of(), YakuSelection.nativeIds(MahjongVariant.RIICHI), Set.of()))), example.id());
        }
    }

    @Test void ancientConditionsRejectSimilarHandsAndWrongEvents() {
        for (String id : List.of("tsubame_gaeshi", "kanburi", "renhou", "ishino_uenimo_sannen"))
            assertNull(score(example(id, "123456m456p22s789s", false, 0), only(id)));
        assertNull(score(example("ippin_moyue", "123456m123s55z78p9p", true, 0, "Haitei"), only("ippin_moyue")));
        assertNull(score(example("chuupin_raoyui", "123456m123s55z23p1p", false, 0, "Houtei"), only("chuupin_raoyui")));
        assertNull(score(example("daisharin", "11223344556677p", false, 0), only("daisharin")));
        assertNull(score(example("dai_chisei", "112233445566z11m", false, 0), only("dai_chisei")));
        assertNull(score(example("uumen_sai", "123m456p789s111z22z", false, 0), only("uumen_sai")));
        assertThrows(IllegalArgumentException.class, () -> RiichiPreset.MAHJONG_SOUL_3.config().withYakuSelection(YakuSelection.of(ANCIENT)));
    }

    @Test void openValuesAndContainedAwardsFollowTheBundledRules() {
        var open = new Example("uumen_sai", "456p789s111z55z", false, List.of(), List.of(TestHands.meld(Meld.Type.SEQUENCE, "123m")), 2);
        assertEquals(2, score(open, only(open.id())).han());
        var sequences = new Example("isshoku_sanjun", "112233m456p55s", false, List.of(), List.of(TestHands.meld(Meld.Type.SEQUENCE, "123m")), 2);
        assertEquals(2, score(sequences, only(sequences.id())).han());
        var all = RiichiPreset.MAHJONG_SOUL_4.config().withYakuSelection(YakuSelection.of(ANCIENT));
        assertEquals(2, score(example("dai_chisei", "11223344556677z", false, 26), all).yakuman());
        var moon = score(example("ippin_moyue", "123456m123s55z23p1p", true, 5, "Haitei"), all);
        assertTrue(moon.hasYaku("mahjongsoul:ippin_moyue")); assertFalse(moon.hasYaku("Haitei"));
        var withoutMoon = all.withYakuSelection(all.yakuSelection().withCustom(ANCIENT, "mahjongsoul:ippin_moyue", false));
        assertTrue(score(example("ippin_moyue", "123456m123s55z23p1p", true, 1, "Haitei"), withoutMoon).hasYaku("Haitei"));
    }

    @Test void disabledYakumanLeavesOrdinaryCandidatesAndDoraCannotCreateAYaku() {
        var selection = new YakuSelection(MahjongVariant.RIICHI, List.of(), YakuSelection.nativeIds(MahjongVariant.RIICHI), Set.of())
            .withNative(YakuCatalog.awardId("Toitoi"), true).withNative(YakuCatalog.awardId("Sananko"), true);
        var rules = RiichiPreset.MAHJONG_SOUL_4.config().withYakuSelection(selection);
        var hand = TestHands.tiles("111m222p333s444z55z");
        var score = RiichiHandAnalyzer.score(hand, List.of(), hand.getLast(), true, 1, 0, 0, List.of(), rules);
        assertEquals(0, score.yakuman()); assertEquals(4, score.han());
        assertTrue(score.hasYaku("Toitoi")); assertTrue(score.hasYaku("Sananko"));
        var none = rules.withYakuSelection(selection.withNative(YakuCatalog.awardId("Toitoi"), false).withNative(YakuCatalog.awardId("Sananko"), false));
        assertNull(RiichiHandAnalyzer.score(hand, List.of(), hand.getLast(), true, 1, 0, 20, List.of(), none));
        assertFalse(rules.withYakuSelection(selection.withNative(YakuCatalog.awardId("Nagashi"), false)).nagashiMangan());
    }

    @Test void independentSetsAndSwitchesSurviveFrozenSessionSnapshots() {
        var first = new YakuSet("house:first", MahjongVariant.RIICHI, ANCIENT.name(), List.of(ANCIENT.find("mahjongsoul:tsubame_gaeshi")));
        var second = new YakuSet("house:second", MahjongVariant.RIICHI, ANCIENT.name(), List.of(ANCIENT.find("mahjongsoul:kanburi")));
        var selection = YakuSelection.of(first).withSet(second, true).withNative(YakuCatalog.awardId("Pinhu"), false);
        var session = new RiichiSession(UUID.randomUUID(), RiichiPreset.MAHJONG_SOUL_4, 12);
        var host = UUID.randomUUID(); session.join(host, "Host", 0);
        assertTrue(session.configureRules(host, session.decision(), session.rules().withYakuSelection(selection)));
        var restored = (RiichiSession) TableSessionCodec.restore(TableSessionCodec.save(session));
        assertEquals(selection, restored.rules().yakuSelection());
        assertEquals(2, score(example("both", "123456m456p22s789s", false, 2, "RiichiDiscard", "AfterKongDiscard"), restored.rules()).han());
        assertEquals(1, selection.withSet(first, false).enabledCustom().size());
    }

    @Test void liveGameCertifiesDeclarationKanAndHumanWinCircumstances() {
        var session = new RiichiSession(UUID.randomUUID(), RiichiPreset.MAHJONG_SOUL_4, 33);
        var host = new UUID(71, 0);
        for (int seat = 0; seat < 4; seat++) session.join(new UUID(71, seat), "Player " + seat, seat);
        assertTrue(session.configureRules(host, session.decision(), session.rules().withYakuSelection(new YakuSelection(MahjongVariant.RIICHI,
            List.of(ANCIENT), YakuSelection.nativeIds(MahjongVariant.RIICHI), Set.of("mahjongsoul:tsubame_gaeshi", "mahjongsoul:kanburi", "mahjongsoul:renhou")))));
        session.startMatch();
        var game = session.game(); game.turn = game.dealer;
        int winner = (game.dealer + 1) % 4;
        var player = game.players[winner]; player.resetHand();
        var hand = TestHands.tiles("123456m456p22s789s"); player.hand.addAll(hand.subList(0, 13));
        int winning = hand.getLast();
        player.firstTurn = false; game.uninterrupted = false;
        var discarder = game.players[game.turn]; discarder.pendingRiichi = true;
        assertTrue(LegalActions.score(game, winner, winning, false).hasYaku("mahjongsoul:tsubame_gaeshi"));
        discarder.pendingRiichi = false;
        assertNull(LegalActions.score(game, winner, winning, false));
        discarder.afterKan = true;
        assertTrue(LegalActions.score(game, winner, winning, false).hasYaku("mahjongsoul:kanburi"));
        discarder.afterKan = false;
        player.firstTurn = true; game.uninterrupted = true;
        assertTrue(LegalActions.score(game, winner, winning, false).hasYaku("mahjongsoul:renhou"));
        game.uninterrupted = false;
        assertNull(LegalActions.score(game, winner, winning, false));
    }

    @Test void disablingAnUpgradedNativeAwardRetainsItsBaseAward() {
        for (var pair : List.of(new String[]{"KokushiThirteenWaiting", "Kokushi", "19m19p19s1234567z1m"},
            new String[]{"SuankoTanki", "Suanko", "111m222p333s444z55z"},
            new String[]{"ChurenNineWaiting", "Churen", "1112345678999m5m"})) {
            var rules = RiichiPreset.MAHJONG_SOUL_4.config();
            rules = rules.withYakuSelection(rules.yakuSelection().withNative(YakuCatalog.awardId(pair[0]), false));
            var result = score(example("native", pair[2], true, 13), rules);
            assertFalse(result.hasYaku(pair[0])); assertTrue(result.hasYaku(pair[1])); assertEquals(1, result.yakuman());
        }
        var rules = RiichiPreset.MAHJONG_SOUL_4.config();
        rules = rules.withYakuSelection(rules.yakuSelection().withNative(YakuCatalog.awardId("WRichi"), false));
        var result = score(example("native", "123456m456p22s789s", false, 1, "WRichi"), rules);
        assertTrue(result.hasYaku("Richi")); assertFalse(result.hasYaku("WRichi"));
    }
}
