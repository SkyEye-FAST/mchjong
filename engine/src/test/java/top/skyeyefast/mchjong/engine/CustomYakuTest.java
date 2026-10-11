package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomYakuTest {
    private static final YakuSet.Text TEXT = new YakuSet.Text("House Award", "独自役", "自定义役", "自訂役");
    private static YakuCondition leaf(YakuCondition.Type type, int min, int max, String... values) {
        return new YakuCondition(type, List.of(), List.of(values), min, max);
    }
    private static YakuSet.Definition award(String id, YakuCondition condition, YakuSet.RewardType type, int closed, int open, String... excludes) {
        return new YakuSet.Definition(id, TEXT, TEXT, condition, new YakuSet.Reward(type, closed, open), List.of(excludes));
    }
    private static YakuSet set(YakuSet.Definition... awards) {
        return new YakuSet("house:rules", MahjongVariant.RIICHI, TEXT, List.of(awards));
    }
    private static final YakuCondition REGULAR = leaf(YakuCondition.Type.SHAPE, 0, 0, "regular");

    @Test void customAwardQualifiesAnOpenHandButBonusesCannotSatisfyMinimum() {
        var hand = TestHands.tiles("456p789s22z456m");
        var melds = List.of(TestHands.meld(Meld.Type.SEQUENCE, "123m"));
        var base = RiichiPreset.TENHOU_4.config();
        assertNull(RiichiHandAnalyzer.score(hand, melds, hand.getLast(), false, 2, 0, 8, List.of(), base));
        var rules = base.withYakuSet(set(award("house:regular", REGULAR, YakuSet.RewardType.HAN, 2, 1)));
        var score = RiichiHandAnalyzer.score(hand, melds, hand.getLast(), false, 2, 0, 8, List.of(), rules);
        assertNotNull(score);
        assertEquals(9, score.han());
        assertEquals(List.of(new HandScore.Yaku("house:regular", 1, false)), score.yaku());
        assertNull(RiichiHandAnalyzer.score(hand, melds, hand.getLast(), false, 2, 0, 8, List.of(), rules.with(RiichiRuleOption.MIN_HAN, 2)));
        assertNull(RiichiHandAnalyzer.score(hand, melds, hand.getLast(), false, 2, 0, 0, List.of(),
            base.withYakuSet(set(award("house:closed", REGULAR, YakuSet.RewardType.HAN, 2, 0)))));
    }

    @Test void customAwardsParticipateBeforeSelectingWinningInterpretation() {
        var hand = TestHands.tiles("111222333m456p55s");
        var base = RiichiPreset.TENHOU_4.config();
        var ordinary = RiichiHandAnalyzer.score(hand, List.of(), hand.getLast(), false, 1, 0, 0, List.of(), base);
        assertTrue(ordinary.hasYaku("Sananko"));
        var sequences = leaf(YakuCondition.Type.GROUPS, 4, 4, "sequence", "all", "*");
        var score = RiichiHandAnalyzer.score(hand, List.of(), hand.getLast(), false, 1, 0, 0, List.of(),
            base.withYakuSet(set(award("house:sequences", sequences, YakuSet.RewardType.HAN, 5, 5))));
        assertTrue(score.hasYaku("house:sequences"));
        assertFalse(score.hasYaku("Sananko"));
        assertTrue(score.ron() > ordinary.ron());
    }

    @Test void sequenceFactsUseTheLowestKindRegardlessOfMeldStorageOrder() {
        var hand = TestHands.tiles("456p789s22z456m");
        var tiles = TestHands.tiles("231m");
        var meld = new Meld(Meld.Type.SEQUENCE, tiles, 0, tiles.getLast());
        var condition = leaf(YakuCondition.Type.GROUPS, 1, 1, "sequence", "exposed", "1m");
        var rules = RiichiPreset.TENHOU_4.config().withYakuSet(set(award("house:sequence", condition, YakuSet.RewardType.HAN, 1, 1)));
        var score = RiichiHandAnalyzer.score(hand, List.of(meld), hand.getLast(), false, 2, 0, 0, List.of(), rules);
        assertNotNull(score);
        assertTrue(score.hasYaku("house:sequence"));
    }

    @Test void ronWinningTripletIsNotAConcealedTriplet() {
        var hand = TestHands.tiles("22m333p444s678m55p2m");
        var condition = leaf(YakuCondition.Type.GROUPS, 3, 3, "triplet", "concealed", "*");
        var rules = RiichiPreset.TENHOU_4.config().withYakuSet(set(award("house:three_concealed", condition, YakuSet.RewardType.HAN, 3, 3)));
        var ron = RiichiHandAnalyzer.score(hand, List.of(), hand.getLast(), false, 1, 0, 0, List.of(), rules);
        assertFalse(ron.hasYaku("house:three_concealed"));
        var tsumo = RiichiHandAnalyzer.score(hand, List.of(), hand.getLast(), true, 1, 0, 0, List.of(), rules);
        assertTrue(tsumo.hasYaku("house:three_concealed"));
    }

    @Test void exclusionsAreSimultaneousAndYakumanUseTheirOwnPaymentPath() {
        var hand = TestHands.tiles("123456m456p22s789s");
        var a = award("house:a", REGULAR, YakuSet.RewardType.HAN, 2, 1, "mchjong:riichi/pinhu");
        var b = award("house:b", REGULAR, YakuSet.RewardType.HAN, 3, 2, "house:a");
        var rules = RiichiPreset.TENHOU_4.config().withYakuSet(set(a, b));
        var score = RiichiHandAnalyzer.score(hand, List.of(), hand.getLast(), false, 1, 0, 0, List.of(), rules);
        assertEquals(List.of(new HandScore.Yaku("house:b", 3, false)), score.yaku());
        var y1 = award("house:yakuman", REGULAR, YakuSet.RewardType.YAKUMAN, 1, 1);
        var y2 = award("house:double", REGULAR, YakuSet.RewardType.YAKUMAN, 2, 2);
        for (int compound : List.of(0, 1)) {
            var config = rules.withYakuSet(set(a, y1, y2)).with(RiichiRuleOption.COMPOUND_YAKUMAN, compound);
            var limit = RiichiHandAnalyzer.score(hand, List.of(), hand.getLast(), false, 1, 0, 9, List.of(), config);
            assertEquals(compound == 0 ? 2 : 3, limit.yakuman());
            assertEquals(limit.yakuman() * 32000, limit.ron());
            assertEquals(0, limit.dora());
            assertTrue(limit.yaku().stream().allMatch(HandScore.Yaku::yakuman));
        }
    }

    @Test void definitionsValidateVariantExclusionsAndComplexity() {
        var a = award("house:a", REGULAR, YakuSet.RewardType.HAN, 1, 1, "house:b");
        var b = award("house:b", REGULAR, YakuSet.RewardType.HAN, 1, 1, "house:a");
        assertThrows(IllegalArgumentException.class, () -> set(a, b));
        assertThrows(IllegalArgumentException.class, () -> set(a));
        assertThrows(IllegalArgumentException.class, () -> set(award("house:a", REGULAR, YakuSet.RewardType.TAI, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> set(award("house:a", REGULAR, YakuSet.RewardType.HAN, 1, 1, "mchjong:riichi/daisangen")));
        assertThrows(IllegalArgumentException.class, () -> set(award("house:a", leaf(YakuCondition.Type.FLOWERS, 1, 8), YakuSet.RewardType.HAN, 1, 1)));
        YakuCondition tree = REGULAR;
        for (int i = 0; i < 4; i++) tree = new YakuCondition(YakuCondition.Type.NOT, List.of(tree), List.of(), 0, 0);
        var deep = tree;
        assertThrows(IllegalArgumentException.class, () -> set(award("house:a", deep, YakuSet.RewardType.HAN, 1, 1)));
    }

    @Test void dataPackParsingAndRulesSnapshotsPreserveDefinitions() {
        String text = "{\"en_us\":\"House\",\"ja_jp\":\"独自役\",\"zh_cn\":\"自定义役\",\"zh_tw\":\"自訂役\"}";
        String json = "{\"variant\":\"riichi\",\"name\":" + text + ",\"awards\":[{\"id\":\"house:a\",\"name\":" + text
            + ",\"description\":" + text + ",\"when\":{\"all\":[{\"type\":\"closed\",\"values\":[\"true\"]},{\"type\":\"reds\",\"min\":1,\"max\":4}]},"
            + "\"award\":{\"type\":\"han\",\"closed\":2,\"open\":0}}]}";
        var parsed = YakuSetCodec.parse("house:rules", json);
        assertEquals(parsed, YakuSetCodec.decode(YakuSetCodec.encode(parsed)));
        assertThrows(IllegalArgumentException.class, () -> YakuSetCodec.parse("house:rules", json.replace("\"variant\":", "\"variant\":\"riichi\",\"variant\":")));
        assertThrows(IllegalArgumentException.class, () -> YakuSetCodec.parse("house:rules", json.replace("\"min\":1,", "")));
        var lobby = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 1);
        var host = UUID.randomUUID();
        lobby.join(host, "Host", 0);
        assertTrue(lobby.configureRules(host, lobby.decision, lobby.rules.withYakuSet(parsed)));
        var restored = TableSessionCodec.restore(TableSessionCodec.save(lobby));
        assertEquals(parsed, ((RiichiSession) restored).rules.yakuSet());
        assertFalse(new ExternalBot("test", "Test", 4, List.of(RiichiPreset.TENHOU_4)).supports(lobby.rules));
    }
}
