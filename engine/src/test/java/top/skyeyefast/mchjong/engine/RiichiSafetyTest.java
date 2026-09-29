package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiichiSafetyTest {
    private static ReplayHand.Event event(ReplayHand.Kind kind, int seat, int tile) {
        return new ReplayHand.Event(kind, seat, tile,
            kind == ReplayHand.Kind.MELD ? TestHands.meld(Meld.Type.CONCEALED_QUAD, "5555m") : null, false, false, true);
    }

    @Test void onlyCompletedDiscardsAfterEachAcceptedRiichiAreSafe() {
        var events = new ArrayList<ReplayHand.Event>();
        events.add(event(ReplayHand.Kind.DISCARD, 2, 0));
        events.add(event(ReplayHand.Kind.RIICHI, 1, Tile.ABSENT));
        events.add(event(ReplayHand.Kind.DRAW, 2, 124));
        events.add(event(ReplayHand.Kind.DISCARD, 2, 4));
        assertEquals(0L, RiichiSafety.from(events).get(1));
        events.add(event(ReplayHand.Kind.DORA, -1, 8));
        assertEquals(0L, RiichiSafety.from(events).get(1), "Dora reveal does not resolve ron");
        events.add(event(ReplayHand.Kind.RIICHI, 2, Tile.ABSENT));
        assertEquals(2L, RiichiSafety.from(events).get(1));
        assertEquals(0L, RiichiSafety.from(events).get(2));
        events.add(event(ReplayHand.Kind.DRAW, 3, 128));
        events.add(event(ReplayHand.Kind.DISCARD, 3, 12));
        events.add(event(ReplayHand.Kind.DRAW, 0, 132));
        assertEquals(10L, RiichiSafety.from(events).get(1));
        assertEquals(8L, RiichiSafety.from(events).get(2), "Draw identities never become safe tiles");
        for (var kind : List.of(ReplayHand.Kind.MELD, ReplayHand.Kind.NUKI)) {
            var changed = new ArrayList<>(events);
            changed.add(event(kind, 1, 16));
            assertEquals(0L, RiichiSafety.from(changed).get(1), "Wait-changing declarations invalidate prior evidence");
            assertEquals(8L, RiichiSafety.from(changed).get(2));
            var declaration = changed.removeLast();
            changed.add(new ReplayHand.Event(kind, 1, declaration.tile(), declaration.meld(), false, false, false));
            assertEquals(10L, RiichiSafety.from(changed).get(1), "Robbed declarations do not change waits");
        }
    }

    @Test void redactedViewsAndSavedGamesPreserveOpponentSpecificEvidence() {
        var game = GameLifecycleTest.started(RiichiPreset.TENHOU_4, 91);
        game.recorder = new ReplayRecorder(game);
        game.players[1].riichi = true;
        game.players[2].riichi = true;
        game.recorder.riichi(1);
        game.recorder.discard(3, 4, false, false);
        game.recorder.draw(0, 128);
        game.recorder.riichi(2);
        var view = game.view(game.players[0].member.id);
        var defence = new BotAnalysis(view, BotDifficulty.HARD).defence;
        assertEquals(0, defence.riskAgainst(1, 1));
        assertTrue(defence.riskAgainst(2, 1) > 0);
        assertEquals(view.riichiSafeTiles(), game.view(null).riichiSafeTiles());
        var json = new Gson();
        var restored = GameLifecycleTest.reloadMounted(game);
        assertEquals(view.riichiSafeTiles(), restored.view(game.players[0].member.id).riichiSafeTiles());
        assertEquals(view.riichiSafeTiles(), json.fromJson(json.toJson(view), RiichiView.class).riichiSafeTiles());
        game.startHand();
        assertTrue(game.view(game.players[0].member.id).riichiSafeTiles().isEmpty());
    }
}
