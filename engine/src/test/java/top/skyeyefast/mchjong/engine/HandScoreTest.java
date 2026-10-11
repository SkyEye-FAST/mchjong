package top.skyeyefast.mchjong.engine;

import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HandScoreTest {
    @Test void scoreOwnsImmutableValuesWithoutLookingUpYakuNames() {
        var awards = new ArrayList<>(List.of(new HandScore.Yaku("house:five_gates", 2, false)));
        var score = new HandScore(2, 30, 0, 2000, 0, 0, awards, 0);
        awards.clear();
        assertTrue(score.hasYaku("house:five_gates"));
        assertThrows(UnsupportedOperationException.class, () -> score.yaku().clear());
        var json = new GsonBuilder().disableJdkUnsafe().create();
        assertEquals(score, json.fromJson(json.toJson(score), HandScore.class));
        assertThrows(RuntimeException.class, () -> json.fromJson(
            "{\"han\":2,\"fu\":30,\"yakuman\":0,\"ron\":2000,\"tsumoDealer\":0,\"tsumoChild\":0,\"yaku\":[\"Richi\"],\"dora\":0}", HandScore.class));
    }

    @Test void rejectsMalformedAndDuplicateAwardEntries() {
        assertThrows(IllegalArgumentException.class, () -> new HandScore.Yaku("", 1, false));
        assertThrows(IllegalArgumentException.class, () -> new HandScore.Yaku("Richi", 0, false));
        assertThrows(IllegalArgumentException.class, () -> new HandScore.Yaku("Yakuman", 14, true));
        var yaku = new HandScore.Yaku("Richi", 1, false);
        assertThrows(IllegalArgumentException.class, () -> new HandScore(2, 30, 0, 2000, 0, 0, List.of(yaku, yaku), 0));
    }

    @Test void nativeReplayRetainsAwardValuesAndTenhouExportUsesRecordedHan() {
        var score = new HandScore(3, 30, 0, 3900, 0, 0, List.of(new HandScore.Yaku("Richi", 3, false)), 0);
        var match = replay(score);
        var restored = ReplayCodec.decode(ReplayCodec.encode(match), ReplayMatch.class);
        assertEquals(score, restored.riichi().hands().getFirst().wins().getFirst().score());
        assertTrue(TenhouReplay.export(restored).toString().contains("立直(3飜)"));

        var custom = new HandScore(2, 30, 0, 2000, 0, 0, List.of(new HandScore.Yaku("house:five_gates", 2, false)), 0);
        var nativeMatch = ReplayCodec.decode(ReplayCodec.encode(replay(custom)), ReplayMatch.class);
        assertEquals(custom, nativeMatch.riichi().hands().getFirst().wins().getFirst().score());
        assertThrows(IllegalArgumentException.class, () -> TenhouReplay.export(nativeMatch),
            "Tenhou export must reject unsupported awards rather than invent a built-in equivalent");

        var invalid = com.google.gson.JsonParser.parseString(ReplayCodec.encode(match)).getAsJsonObject();
        invalid.getAsJsonObject("riichi").getAsJsonArray("hands").get(0).getAsJsonObject()
            .getAsJsonArray("wins").get(0).getAsJsonObject().getAsJsonObject("score")
            .getAsJsonArray("yaku").get(0).getAsJsonObject().remove("han");
        assertThrows(IllegalArgumentException.class, () -> ReplayCodec.decode(invalid.toString(), ReplayMatch.class));
    }

    private static ReplayMatch replay(HandScore score) {
        var source = TenhouFormatTest.fixture(RiichiPreset.TENHOU_4, 0, 0, List.of(), "ron");
        var hand = source.riichi().hands().getFirst();
        var deltas = List.of(-score.ron(), score.ron(), 0, 0);
        var win = new ReplayHand.Win(1, 0, 4, score, deltas, -1, 0, 0, 0, 0, 0, 0);
        var recorded = new ReplayHand(hand.number(), hand.round(), hand.dealer(), hand.honba(), hand.sticks(),
            hand.initialPoints(), hand.initialHands(), hand.initialDora(), hand.wall(), hand.events(), hand.decisions(),
            hand.finalSeats(), List.of(win), hand.result(), deltas, hand.dora(), hand.ura(), hand.finalScores(), hand.finalRanks());
        return new ReplayMatch(source.id(), source.tableId(), source.startedAt(), source.updatedAt(), source.participants(),
            source.variant(), false, new RiichiReplay(source.riichi().rules(), 0, source.riichi().redFives(), List.of(recorded)),
            null, null, null);
    }
}
