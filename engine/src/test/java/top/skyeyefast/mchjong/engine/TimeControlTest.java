package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeControlTest {
    private static RiichiGame game(int reserve, int move) {
        RiichiGame game = new RiichiGame(UUID.randomUUID(), RiichiPreset.TENHOU_4, 31);
        for (int seat = 0; seat < 4; seat++) game.join(new UUID(1, seat), "Player " + seat, seat);
        assertTrue(game.configureClock(game.players[0].member.id, new TimeControl(reserve, move)));
        GameLifecycleTest.startPositioned(game);
        return game;
    }
    private static void tick(RiichiGame game, int ticks) { for (int i = 0; i < ticks; i++) game.tick(); }

    @Test void dealDoesNotUseAllowanceAndMoveTimeIsSpentBeforeReserve() {
        RiichiGame game = game(2, 1);
        int seat = game.turn;
        assertFalse(game.view(game.players[seat].member.id).clocks().get(seat).active());
        tick(game, RiichiGame.DEAL_TICKS);
        assertEquals(20, game.moveTicks[seat]);
        assertEquals(40, game.reserveTicks[seat]);
        tick(game, 20);
        assertEquals(0, game.moveTicks[seat]);
        assertEquals(40, game.reserveTicks[seat]);
        tick(game, 10);
        assertEquals(30, game.reserveTicks[seat]);
        game.validate();
    }

    @Test void timeoutDiscardsTheDrawnTileWithoutClaimingAWin() {
        RiichiGame game = game(1, 1);
        int seat = game.turn, drawn = game.players[seat].drawn;
        long decision = game.decision;
        tick(game, RiichiGame.DEAL_TICKS + 39);
        assertEquals(decision, game.decision);
        game.tick();
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        assertTrue(game.players[seat].river.getLast().tsumogiri());
        assertTrue(game.wins.isEmpty());
    }

    @Test void respondersUseIndependentClocksAndAnAnswerStopsChargingReserve() {
        RiichiGame game = game(2, 1);
        game.newDecision(RiichiGame.Phase.REACTION);
        game.options.set(1, List.of(new Action(Action.Type.PASS)));
        game.options.set(2, List.of(new Action(Action.Type.PASS)));
        tick(game, 30);
        assertEquals(30, game.reserveTicks[1]);
        assertEquals(30, game.reserveTicks[2]);
        assertTrue(game.act(game.players[1].member.id, game.decision, 0));
        tick(game, 10);
        assertEquals(30, game.reserveTicks[1]);
        assertEquals(20, game.reserveTicks[2]);
        assertFalse(game.view(game.players[1].member.id).clocks().get(1).active());
    }

    @Test void newDecisionRefreshesOnlyMoveAllowanceAndNewHandRefreshesReserve() {
        RiichiGame game = game(2, 1);
        game.reserveTicks[0] = 7;
        game.moveTicks[0] = 0;
        game.newDecision(RiichiGame.Phase.TURN);
        assertEquals(7, game.reserveTicks[0]);
        assertEquals(20, game.moveTicks[0]);
        game.startHand();
        assertEquals(40, game.reserveTicks[0]);
    }

    @Test void settlementCountsDownWithoutChargingClocksAndReloadPreservesTime() {
        RiichiGame game = game(2, 1);
        tick(game, RiichiGame.DEAL_TICKS + 27);
        RiichiGame restored = GameLifecycleTest.reloadMounted(game);
        assertArrayEquals(game.reserveTicks, restored.reserveTicks);
        assertArrayEquals(game.moveTicks, restored.moveTicks);
        restored.validate();
        Settlement.abort(restored, "nine_terminals");
        int[] reserve = restored.reserveTicks.clone();
        tick(restored, 99);
        restored = GameLifecycleTest.reloadMounted(restored);
        assertEquals(101, restored.roomView().settlementTicks());
        tick(restored, 100);
        assertArrayEquals(reserve, restored.reserveTicks);
        assertEquals(RiichiGame.Phase.HAND_END, restored.phase());
        assertTrue(restored.view(null).clocks().stream().noneMatch(TimeControl.Clock::active));
        restored.tick();
        assertEquals(RiichiGame.Phase.TURN, restored.phase());
        assertEquals(0, restored.roomView().settlementTicks());
    }

    @Test void winningReceiptsKeepAFiniteServerDeadlineWithoutAcknowledgements() {
        RiichiGame game = game(2, 1);
        Settlement.abort(game, "nine_terminals");
        game.wins = List.of(new TableView.Win(0, 1, 0,
            new HandScore(5, 40, 0, 12000, 0, 0, List.of("Richi", "Chanta"), 2)));
        int limit = ScoreAnnouncements.maximumTicks(game.wins);
        int[] reserve = game.reserveTicks.clone();
        assertEquals(limit, game.roomView().settlementTicks());
        tick(game, limit - 2);
        game = GameLifecycleTest.reloadMounted(game);
        game.tick();
        assertEquals(RiichiGame.Phase.HAND_END, game.phase());
        assertArrayEquals(reserve, game.reserveTicks);
        game.tick();
        assertEquals(RiichiGame.Phase.TURN, game.phase());
    }

    @Test void manualCollectionCannotSkipSettlementAndUncollectedHandsStillAdvance() {
        RiichiGame game = game(2, 1);
        game.manual = true;
        Settlement.abort(game, "nine_terminals");
        for (var player : game.players) assertTrue(game.act(player.member.id, game.decision, 0));
        assertEquals(RiichiGame.Phase.HAND_END, game.phase());
        tick(game, RiichiGame.SETTLEMENT_TICKS - 1);
        assertEquals(RiichiGame.Phase.HAND_END, game.phase());
        game.tick();
        assertEquals(RiichiGame.Phase.SHUFFLE, game.phase());
        Settlement.abort(game, "nine_terminals");
        tick(game, RiichiGame.SETTLEMENT_TICKS);
        assertEquals(RiichiGame.Phase.SHUFFLE, game.phase());
    }

    @Test void onlyLobbyHostCanConfigureAndChangingSettingsClearsReadiness() {
        RiichiGame game = new RiichiGame(UUID.randomUUID(), RiichiPreset.TENHOU_4, 1);
        UUID host = UUID.randomUUID(), guest = UUID.randomUUID();
        game.join(host, "Host", 0); game.join(guest, "Guest", 1);
        game.players[1].member.ready = true;
        assertFalse(game.configureClock(guest, new TimeControl(0, 1)));
        assertFalse(game.configureClock(UUID.randomUUID(), new TimeControl(0, 1)));
        assertTrue(game.configureClock(host, new TimeControl(0, 1)));
        assertFalse(game.players[1].member.ready);
        RiichiGame started = game(2, 1);
        assertFalse(started.configureClock(started.players[0].member.id, new TimeControl(20, 5)));
    }

    @Test void invalidSettingsAreRejectedAndInterpolationNeverBecomesNegative() {
        assertThrows(IllegalArgumentException.class, () -> new TimeControl(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> new TimeControl(20, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimeControl(601, 5));
        assertThrows(IllegalArgumentException.class, () -> new TimeControl(20, 121));
        var clock = new TimeControl.Clock(20, 40, true);
        assertEquals(new TimeControl.Clock(0, 30, true), clock.after(1500));
        assertEquals(new TimeControl.Clock(0, 0, true), clock.after(Long.MAX_VALUE));
        var frozen = new TimeControl.Clock(20, 40, false);
        assertEquals(frozen, frozen.after(5000));
    }
}
