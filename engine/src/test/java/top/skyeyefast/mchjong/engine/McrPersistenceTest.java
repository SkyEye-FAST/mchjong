package top.skyeyefast.mchjong.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.Action.Type.*;
import static top.skyeyefast.mchjong.engine.McrGameTest.*;

class McrPersistenceTest {
    @Test void partialResponsesSurviveAndOldActionTokensDoNot() {
        var game = new McrGame(4, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "123456789p11s46m").hand(2, "123456789s22p46m")
            .hand(3, "555m123789m123p1z").build());
        discardKind(game, 0, "5m");
        long token = game.decision(), revision = game.revision();
        play(game, 2, RON);
        assertEquals(token, game.decision());
        assertEquals(revision + 1, game.revision());
        var saved = game.save();
        var restored = roundTrip(game);
        assertEquals(token + 1, restored.decision());
        assertTrue(restored.actions(2).isEmpty());
        assertFalse(restored.act(1, token, index(restored, 1, RON)));
        assertEquals(game.revision() + 1, restored.revision());
        assertThrows(UnsupportedOperationException.class, () -> saved.players().get(0).hand().clear());
        assertFalse(McrCodec.save(game).contains("choices"));
        for (var match : new McrGame[]{game, restored}) {
            play(match, 3, OPEN_KAN);
            play(match, 1, RON);
            assertEquals(1, ((McrSettlement.Win) match.result()).winner());
        }
        assertSamePosition(game, restored);
        assertEquals(1, saved.replies().size(), "A saved state is detached from further play");
        roundTrip(restored); // A settled ron does not take or pay for the tile again.
    }

    @Test void pendingAddedKongCanResumeEitherPassingOrRobbingWithoutReapplyingActions() {
        var game = addedKongPosition();
        play(game, 1, ADDED_KAN);
        int remaining = game.remaining();
        var restored = roundTrip(game);
        var robbed = roundTrip(game);
        rejected(McrCodec.save(game), object -> object.getAsJsonObject("pendingKong").addProperty("type", "CLOSED_KAN"));
        assertEquals(Meld.Type.PON, restored.melds(1).get(0).type());
        assertEquals(McrWinContext.KongWin.ROBBED, restored.winningContext(2).kongWin());
        passAll(game);
        passAll(restored);
        assertSamePosition(game, restored);
        assertEquals(remaining - 1, restored.remaining());
        assertEquals(McrWinContext.KongWin.REPLACEMENT, restored.winningContext(1).kongWin());
        roundTrip(restored);
        play(robbed, 2, RON);
        passAll(robbed);
        assertEquals(remaining, robbed.remaining());
        assertEquals(Meld.Type.PON, robbed.melds(1).get(0).type());
        roundTrip(robbed);
    }

    @Test void wrongWinSurvivesWithoutRepaymentAndNextHandClearsOnlyTheRestriction() {
        var game = new McrGame(5, new Fixture().hand(0, "12345m567p789s11z6m").build());
        play(game, 0, TSUMO);
        var restored = roundTrip(game);
        assertTrue(restored.winForbidden(0));
        assertFalse(has(restored, 0, TSUMO));
        assertEquals(-30, restored.points(0));
        assertEquals(game.penalties(), restored.penalties());
        rejected(McrCodec.save(game), object -> player(object, 0).addProperty("winForbidden", false));
        rejected(McrCodec.save(game), object -> object.getAsJsonArray("penalties").remove(0));
        for (var match : new McrGame[]{game, restored}) {
            exhaust(match);
            assertTrue(match.nextHand());
            assertFalse(match.winForbidden(0));
            assertEquals(-30, match.points(0));
        }
        assertSamePosition(game, restored);
        roundTrip(restored);
    }

    @Test void drawAndFlowerReplacementRestoreTheSameTailAndWinningFacts() {
        var game = new McrGame(2, new Fixture().hand(1, "19m19p19s1234567z")
            .at(53, FlowerTile.SPRING.id()).at(143, FlowerTile.SUMMER.id()).at(142, 0).build());
        discard(game, 0, game.drawn(0));
        passAll(game);
        var restored = roundTrip(game);
        play(game, 1, DRAW);
        play(restored, 1, DRAW);
        assertSamePosition(game, restored);
        restored = roundTrip(restored);
        assertEquals(2, restored.winningContext(1).flowerCount());
        assertEquals(McrWinContext.KongWin.NONE, restored.winningContext(1).kongWin());
        play(restored, 1, TSUMO);
        roundTrip(restored);
    }

    @Test void completedSelfDrawRestoresWithoutRepayingAndRetainsTheNextWallSeed() {
        var game = new McrGame(8, new Fixture().hand(0, "19m19p19s1234567z1m").build());
        play(game, 0, TSUMO);
        var restored = roundTrip(game);
        String json = McrCodec.save(game);
        rejected(json, object -> object.getAsJsonObject("result").getAsJsonObject("win")
            .getAsJsonObject("score").addProperty("totalFan", 999));
        rejected(json, object -> object.getAsJsonObject("result").addProperty("type", "unknown"));
        assertTrue(game.nextHand());
        assertTrue(restored.nextHand());
        assertSamePosition(game, restored);
    }

    @Test void malformedOrInconsistentSavesAreRejectedWithoutChangingTheLiveGame() {
        var game = new McrGame(4, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "123456789p11s46m").hand(2, "123456789s22p46m").build());
        discardKind(game, 0, "5m");
        play(game, 2, RON);
        String json = McrCodec.save(game);
        rejected(json, object -> object.addProperty("format", 2));
        rejected(json, object -> object.remove("seed"));
        rejected(json, object -> object.addProperty("seed", "4"));
        rejected(json, object -> object.addProperty("decision", Long.MAX_VALUE));
        rejected(json, object -> object.addProperty("drawKong", "ROBBED"));
        rejected(json, object -> player(object, 1).addProperty("winForbidden", "false"));
        rejected(json, object -> object.addProperty("phase", "MATCH_END"));
        rejected(json, object -> object.getAsJsonObject("wall").addProperty("head", 52));
        rejected(json, object -> object.getAsJsonObject("wall").addProperty("head", 53.5));
        rejected(json, object -> object.getAsJsonObject("wall").addProperty("tail", 4_294_967_440L));
        rejected(json, object -> player(object, 3).add("drawn", player(object, 3).getAsJsonArray("hand").get(0)));
        rejected(json, object -> player(object, 1).getAsJsonArray("hand").set(0, player(object, 0).getAsJsonArray("hand").get(0)));
        rejected(json, object -> player(object, 0).addProperty("points", 100));
        rejected(json, object -> object.getAsJsonArray("replies").get(0).getAsJsonObject()
            .getAsJsonObject("action").addProperty("type", "NEXT"));
        rejected(json, object -> object.getAsJsonArray("replies").add(object.getAsJsonArray("replies").get(0).deepCopy()));
        for (String invalid : new String[]{"null", "{}", json + "{}", json.replace("\"format\"", "format"),
            json.replace("\"format\":1", "\"format\":1,\"format\":1"),
            "{\"nested\":" + "[".repeat(1000) + "0" + "]".repeat(1000) + "}", " ".repeat(65_537)})
            assertThrows(IllegalArgumentException.class, () -> McrCodec.restore(invalid));
        assertEquals(json, McrCodec.save(game));
    }

    static McrGame roundTrip(McrGame game) {
        var restored = McrCodec.restore(McrCodec.save(game));
        restored.validate();
        assertSamePosition(game, restored);
        return restored;
    }

    private static void assertSamePosition(McrGame first, McrGame second) {
        var left = JsonParser.parseString(McrCodec.save(first)).getAsJsonObject();
        var right = JsonParser.parseString(McrCodec.save(second)).getAsJsonObject();
        for (String key : new String[]{"revision", "decision"}) { left.remove(key); right.remove(key); }
        assertEquals(left, right);
        for (int seat = 0; seat < 4; seat++) assertEquals(first.actions(seat), second.actions(seat));
    }

    private static JsonObject player(JsonObject state, int seat) { return state.getAsJsonArray("players").get(seat).getAsJsonObject(); }

    private static void rejected(String json, Consumer<JsonObject> corrupt) {
        var object = JsonParser.parseString(json).getAsJsonObject();
        corrupt.accept(object);
        assertThrows(IllegalArgumentException.class, () -> McrCodec.restore(object.toString()));
    }
}
