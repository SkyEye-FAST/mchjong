package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TableControlTest {
    private static UUID id(int seat) { return new UUID(42, seat); }

    private static Game game(int humans, RuleSet rules, boolean open) {
        Game game = new Game(UUID.randomUUID(), rules, 123);
        for (int seat = 0; seat < humans; seat++) assertTrue(game.join(id(seat), "Player " + seat, seat));
        if (open) assertTrue(game.configureOpenHands(id(0), game.decision, true));
        for (int seat = 1; seat < humans; seat++)
            assertTrue(game.act(id(seat), game.decision, GameLifecycleTest.index(game.view(id(seat)), Action.Type.READY)));
        assertTrue(game.act(id(0), game.decision, GameLifecycleTest.index(game.view(id(0)), Action.Type.PRACTICE)));
        assertEquals(Game.Phase.TURN, game.phase());
        return game;
    }

    @Test void oneHumanCanExitEveryMatchStageAndStartAgain() {
        for (RuleSet rules : RuleSet.values()) for (Game.Phase phase : Game.Phase.values()) {
            if (phase == Game.Phase.LOBBY) continue;
            Game game = game(1, rules, false);
            game.phase = phase;
            assertTrue(game.requestExit(id(0)));
            assertEquals(Game.Phase.LOBBY, game.phase());
            assertTrue(game.view(null).seats().stream().noneMatch(TableView.Seat::occupied));
            assertTrue(game.view(null).wall().isEmpty());
            assertNull(game.view(null).exitVote());
            game.validate();
            assertTrue(game.join(id(0), "Again", 0));
            assertTrue(game.act(id(0), game.decision, GameLifecycleTest.index(game.view(id(0)), Action.Type.PRACTICE)));
            game.validate();
        }
    }

    @Test void allHumansMustAgreeAndBotsSpectatorsDuplicatesCannotVote() {
        for (RuleSet rules : RuleSet.values()) {
            Game game = game(3, rules, false);
            long decision = game.decision;
            var legal = game.view(id(1)).actions();
            assertFalse(game.requestExit(UUID.randomUUID()));
            assertTrue(game.requestExit(id(0)));
            long vote = game.exitVote.id();
            assertEquals(3, game.exitVote.required());
            assertFalse(game.answerExit(UUID.randomUUID(), vote, true));
            if (rules.players() == 4) assertFalse(game.answerExit(game.players[3].id, vote, true));
            assertFalse(game.answerExit(id(0), vote, true));
            assertTrue(game.answerExit(id(1), vote, true));
            assertNotEquals(decision, game.decision);
            assertTrue(game.view(id(1)).actions().isEmpty(), "Tile actions are paused during a ballot");
            game.validate();
            assertTrue(game.answerExit(id(2), vote, true));
            assertEquals(Game.Phase.LOBBY, game.phase());
            assertEquals(-1, game.seatOf(id(0)));
            game.validate();
        }
    }

    @Test void rejectionTimeoutAndOldVoteCannotTerminateAnotherVote() {
        Game game = game(2, RuleSet.TENHOU_4, false);
        assertTrue(game.requestExit(id(0)));
        long old = game.exitVote.id();
        assertTrue(game.answerExit(id(1), old, false));
        assertNull(game.exitVote);
        assertFalse(game.requestExit(id(0)), "Rejected ballots have a cooldown");
        game.exitCooldown = 0;
        assertTrue(game.requestExit(id(0)));
        assertFalse(game.answerExit(id(1), old, true));
        for (int tick = 0; tick < ExitVote.DURATION_TICKS; tick++) game.tick();
        assertNull(game.exitVote);
        assertNotEquals(Game.Phase.LOBBY, game.phase());
        game.exitCooldown = 0;
        assertTrue(game.requestExit(id(1)));
        game.leave(id(0)); // A disconnected/reserved player is not silently counted as agreeing.
        assertEquals(2, game.exitVote.required());
        assertEquals(1, game.exitVote.agreed().size());
        game.validate();
    }

    @Test void openHandsIsHostControlledBeforePlayAndNeverRevealsToSpectators() {
        Game lobby = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 1);
        lobby.join(id(0), "Host", 0); lobby.join(id(1), "Guest", 1);
        lobby.players[1].ready = true;
        assertFalse(lobby.configureOpenHands(id(1), lobby.decision, true));
        assertFalse(lobby.configureOpenHands(id(0), lobby.decision - 1, true));
        assertTrue(lobby.configureOpenHands(id(0), lobby.decision, true));
        assertFalse(lobby.players[1].ready);
        for (boolean open : new boolean[]{false, true}) {
            Game game = game(2, RuleSet.MAHJONG_SOUL_3, open);
            assertFalse(game.configureOpenHands(id(0), game.decision, !open));
            for (int seat = 0; seat < game.rules.players(); seat++) {
                assertTrue(game.view(null).seats().get(seat).hand().stream().allMatch(tile -> tile == Tile.HIDDEN));
                assertEquals(open || seat == 0, game.view(id(0)).seats().get(seat).hand().stream().allMatch(tile -> tile >= 0));
            }
        }
    }

    @Test void reloadPreservesVoteAndExitDoesNotDiscardCompletedReplayQueue() {
        Game game = game(2, RuleSet.TENHOU_4, true);
        Settlement.abort(game, "nine_terminals");
        assertFalse(game.pendingReplays().isEmpty());
        var replays = game.pendingReplays();
        game.requestExit(id(0));
        game = new Gson().fromJson(new Gson().toJson(game), Game.class);
        game.validate();
        assertTrue(game.openHands);
        assertTrue(game.answerExit(id(1), game.exitVote.id(), true));
        assertEquals(replays, game.pendingReplays());
        game.validate();
    }
}
