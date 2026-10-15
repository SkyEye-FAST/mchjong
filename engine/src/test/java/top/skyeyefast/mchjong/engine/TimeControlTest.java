package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeControlTest {
    private static RiichiGame game(int reserve, int move) {
        return game(reserve, move, false);
    }
    private static RiichiGame game(int reserve, int move, boolean manual) {
        RiichiSession session = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 31);
        if (manual) session.configureEquipment(true, Tile.set(false));
        for (int seat = 0; seat < 4; seat++) session.join(new UUID(1, seat), "Player " + seat, seat);
        assertTrue(session.configureClock(session.participants[0].id, new TimeControl(reserve, move)));
        GameLifecycleTest.startPositioned(session);
        return session.game();
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
        long decision = game.decision();
        tick(game, RiichiGame.DEAL_TICKS + 39);
        assertEquals(decision, game.decision());
        game.tick();
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        assertTrue(game.players[seat].river.getLast().tsumogiri());
        assertTrue(game.wins.isEmpty());
    }

    @Test void chineseVariantsDiscardTheDrawnTileOnTimeoutAndSichuanPausesForExitVotes() {
        for (TableSession session : List.of(new McrSession(UUID.randomUUID(), 31),
            new SichuanSession(UUID.randomUUID(), 31), new TaiwanSession(UUID.randomUUID(), 31))) {
            for (int seat = 0; seat < 4; seat++) assertTrue(session.join(new UUID(2, seat), "Player " + seat, seat));
            UUID host = session.participants[0].id;
            assertTrue(session.configureClock(host, new TimeControl(0, 1)));
            assertTrue(session.configureEquipment(false,
                session instanceof SichuanSession ? Tile.sichuanSet() : Tile.standard144Set()));
            if (session instanceof SichuanSession sichuan)
                assertTrue(sichuan.configureRules(host, session.decision(), SichuanPreset.TFMJ_2024.config()));
            session.seating.positioned(4);
            session.startMatch();
            int drawn;
            if (session instanceof McrSession mcr) {
                for (int ticks = 0; ticks < 200 && mcr.save().game().phase() != McrGame.Phase.TURN; ticks++) mcr.tick();
                assertEquals(McrGame.Phase.TURN, mcr.save().game().phase());
                drawn = mcr.view(host).game().seats().get(0).drawn();
            } else if (session instanceof SichuanSession sichuan) {
                drawn = sichuan.game().view(0).seats().get(0).drawn();
                int suit = Tile.kind(drawn) / 9;
                for (int seat = 0; seat < 4; seat++) {
                    var actions = sichuan.game().actions(seat);
                    int choice = actions.indexOf(new SichuanAction(SichuanAction.Type.VOID_SUIT, List.of(), suit));
                    assertTrue(sichuan.act(session.participants[seat].id, session.tableId(), session.incarnation(),
                        sichuan.game().decision(), choice));
                }
                var discards = sichuan.game().actions(0).stream()
                    .filter(action -> action.type() == SichuanAction.Type.DISCARD).toList();
                assertNotEquals(drawn, discards.getFirst().tiles().getFirst());
                sichuan.tick();
                var clocks = sichuan.save().clocks();
                long token = sichuan.game().decision();
                assertTrue(sichuan.requestExit(host));
                for (int ticks = 0; ticks < 25; ticks++) sichuan.tick();
                assertEquals(clocks, sichuan.save().clocks());
                assertEquals(token, sichuan.game().decision());
                assertTrue(sichuan.view(host).clocks().stream().noneMatch(TimeControl.Clock::active));
                assertTrue(sichuan.answerExit(session.participants[1].id, sichuan.roomView(host).exitVote().id(), false));
                // Complete the same 20-tick decision allowance as the other variants.
                for (int ticks = 0; ticks < 18; ticks++) sichuan.tick();
            } else {
                drawn = ((TaiwanSession) session).view(host).game().seats().get(0).drawn();
            }
            if (!(session instanceof SichuanSession)) for (int ticks = 0; ticks < 19; ticks++) session.tick();
            session.tick();
            int discarded = switch (session) {
                case McrSession mcr -> mcr.save().game().players().get(0).river().getLast().tile();
                case SichuanSession sichuan -> sichuan.game().view(0).seats().get(0).river().getLast().tile();
                case TaiwanSession taiwan -> taiwan.view(host).game().seats().get(0).river().getLast();
                default -> throw new AssertionError(session.variant());
            };
            assertEquals(drawn, discarded, session.variant().name());
        }
    }

    @Test void respondersUseIndependentClocksAndAnAnswerStopsChargingReserve() {
        RiichiGame game = game(2, 1);
        game.newDecision(RiichiGame.Phase.REACTION);
        game.options.set(1, List.of(new RiichiAction(RiichiAction.Type.PASS)));
        game.options.set(2, List.of(new RiichiAction(RiichiAction.Type.PASS)));
        tick(game, 30);
        assertEquals(30, game.reserveTicks[1]);
        assertEquals(30, game.reserveTicks[2]);
        assertTrue(game.act(game.players[1].member.id, game.decision(), 0));
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
        RiichiSettlement.abort(restored, "nine_terminals");
        int[] reserve = restored.reserveTicks.clone();
        tick(restored, 99);
        restored = GameLifecycleTest.reloadMounted(restored);
        assertEquals(101, restored.view(null).settlementTicks());
        tick(restored, 100);
        assertArrayEquals(reserve, restored.reserveTicks);
        assertEquals(RiichiGame.Phase.HAND_END, restored.phase());
        assertTrue(restored.view(null).clocks().stream().noneMatch(TimeControl.Clock::active));
        restored.tick();
        assertEquals(RiichiGame.Phase.TURN, restored.phase());
        assertEquals(0, restored.view(null).settlementTicks());
    }

    @Test void winningReceiptsKeepAFiniteServerDeadlineWithoutAcknowledgements() {
        RiichiGame game = game(2, 1);
        RiichiSettlement.abort(game, "nine_terminals");
        game.wins = List.of(new RiichiView.Win(0, 1, 0,
            new HandScore(5, 40, 0, 12000, 0, 0, List.of(new HandScore.Yaku("Richi", 1, false), new HandScore.Yaku("Chanta", 2, false)), 2)));
        int limit = ScoreAnnouncements.maximumTicks(game.wins);
        int[] reserve = game.reserveTicks.clone();
        assertEquals(limit, game.view(null).settlementTicks());
        tick(game, limit - 2);
        game = GameLifecycleTest.reloadMounted(game);
        game.tick();
        assertEquals(RiichiGame.Phase.HAND_END, game.phase());
        assertArrayEquals(reserve, game.reserveTicks);
        game.tick();
        assertEquals(RiichiGame.Phase.TURN, game.phase());
    }

    @Test void manualCollectionCannotSkipSettlementAndUncollectedHandsStillAdvance() {
        RiichiGame game = game(2, 1, true);
        RiichiSettlement.abort(game, "nine_terminals");
        for (var player : game.players) assertTrue(game.act(player.member.id, game.decision(), 0));
        assertEquals(RiichiGame.Phase.HAND_END, game.phase());
        tick(game, RiichiGame.SETTLEMENT_TICKS - 1);
        assertEquals(RiichiGame.Phase.HAND_END, game.phase());
        game.tick();
        assertEquals(RiichiGame.Phase.SHUFFLE, game.phase());
        RiichiSettlement.abort(game, "nine_terminals");
        tick(game, RiichiGame.SETTLEMENT_TICKS);
        assertEquals(RiichiGame.Phase.SHUFFLE, game.phase());
    }

    @Test void onlyLobbyHostCanConfigureAndChangingSettingsClearsReadiness() {
        RiichiSession game = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 1);
        UUID host = UUID.randomUUID(), guest = UUID.randomUUID();
        game.join(host, "Host", 0); game.join(guest, "Guest", 1);
        game.participants[1].ready = true;
        assertFalse(game.configureClock(guest, new TimeControl(0, 1)));
        assertFalse(game.configureClock(UUID.randomUUID(), new TimeControl(0, 1)));
        assertTrue(game.configureClock(host, new TimeControl(0, 1)));
        assertFalse(game.participants[1].ready);
        RiichiGame started = game(2, 1);
        assertFalse(started.session.configureClock(started.players[0].member.id, new TimeControl(20, 5)));
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
