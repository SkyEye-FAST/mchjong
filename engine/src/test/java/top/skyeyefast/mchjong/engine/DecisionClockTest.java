package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DecisionClockTest {
    @Test void onlyHostCanConfigureInLobbyAndReadyVotesAreCleared() {
        Game game = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 9);
        UUID host = new UUID(1, 1), guest = new UUID(1, 2);
        game.join(host, "Host", 0);
        game.join(guest, "Guest", 1);
        assertFalse(game.configureClock(guest, new TimeControl(30, 10)));
        assertFalse(game.configureClock(null, TimeControl.DEFAULT));
        game.players[1].ready = true;
        assertTrue(game.configureClock(host, new TimeControl(30, 10)));
        assertFalse(game.players[1].ready);
        assertEquals(new TimeControl(30, 10), game.view(host).timeControl());
        assertThrows(IllegalArgumentException.class, () -> new TimeControl(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> new TimeControl(20, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimeControl(601, 5));
        Game started = GameLifecycleTest.started(RuleSet.TENHOU_4, 9);
        assertFalse(started.configureClock(started.players[0].id, new TimeControl(30, 10)));
    }

    @Test void dealingIsFreeAndReserveStartsOnlyAfterMoveAllowance() {
        Game game = GameLifecycleTest.started(RuleSet.TENHOU_4, 21);
        int seat = game.turn;
        for (int i = 0; i < Game.DEAL_TICKS; i++) game.tick();
        assertEquals(100, game.moveTicks[seat]);
        assertEquals(400, game.reserveTicks[seat]);
        for (int i = 0; i < 100; i++) game.tick();
        assertEquals(0, game.moveTicks[seat]);
        assertEquals(400, game.reserveTicks[seat]);
        for (int i = 0; i < 35; i++) game.tick();
        assertEquals(365, game.reserveTicks[seat]);
        for (int other = 0; other < 4; other++) if (other != seat) assertEquals(400, game.reserveTicks[other]);
        Game restored = new Gson().fromJson(new Gson().toJson(game), Game.class);
        restored.validate();
        assertArrayEquals(game.reserveTicks, restored.reserveTicks);
        assertArrayEquals(game.moveTicks, restored.moveTicks);
    }

    @Test void timeoutDiscardsTheDrawnTileAndNeverClaimsTsumo() {
        Game game = GameLifecycleTest.started(RuleSet.TENHOU_4, 74);
        int seat = game.turn, drawn = game.players[seat].drawn;
        game.age = 0;
        game.moveTicks[seat] = 1;
        game.reserveTicks[seat] = 0;
        game.tick();
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        assertTrue(game.players[seat].river.getLast().tsumogiri());
        assertTrue(game.wins.isEmpty());
    }

    @Test void simultaneousRepliesHaveIndependentClocksAndStopOnSubmission() {
        Game game = GameLifecycleTest.started(RuleSet.TENHOU_4, 65);
        game.newDecision(Game.Phase.REACTION);
        game.lastFrom = 3;
        for (int seat = 0; seat < 3; seat++) game.options.set(seat, List.of(new Action(Action.Type.PASS)));
        for (int i = 0; i < 110; i++) game.tick();
        assertEquals(390, game.reserveTicks[0]);
        assertTrue(game.act(game.players[0].id, game.decision, 0));
        for (int i = 0; i < 20; i++) game.tick();
        assertEquals(390, game.reserveTicks[0]);
        assertEquals(370, game.reserveTicks[1]);
        assertEquals(370, game.reserveTicks[2]);
        assertFalse(game.view(game.players[0].id).clocks().getFirst().active());
    }

    @Test void nextDecisionRefreshesOnlyMoveTimeAndNextHandRefreshesReserve() {
        Game game = GameLifecycleTest.started(RuleSet.MAHJONG_SOUL_3, 93);
        game.reserveTicks[0] = 7;
        game.newDecision(Game.Phase.TURN);
        assertEquals(7, game.reserveTicks[0]);
        assertEquals(100, game.moveTicks[0]);
        game.startHand();
        assertEquals(400, game.reserveTicks[0]);
    }

    @Test void interpolationClampsAtZeroAndDoesNotRunInactiveClocks() {
        var clock = new TimeControl.Clock(20, 40, true);
        assertEquals(new TimeControl.Clock(0, 30, true), clock.after(1500));
        assertEquals(new TimeControl.Clock(0, 0, true), clock.after(Long.MAX_VALUE));
        var stopped = new TimeControl.Clock(20, 40, false);
        assertEquals(stopped, stopped.after(10000));
    }
}
