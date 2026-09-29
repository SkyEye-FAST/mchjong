package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.RiichiAction.Type.*;

class AutoPlayTest {
    private static void ticks(RiichiGame game, int count) { for (int i = 0; i < count; i++) game.tick(); }
    private static void riichi(RiichiGame game, int seat) {
        game.players[seat].riichi = true;
        game.players[seat].points -= 1000;
        game.riichiSticks++;
    }

    @Test void automaticDiscardUsesTheDrawnPhysicalTileAfterTheDealGracePeriod() {
        for (RiichiPreset rules : RiichiPreset.values()) {
            RiichiGame game = GameLifecycleTest.started(rules, 31);
            int seat = game.turn, drawn = game.players[seat].drawn;
            long decision = game.decision();
            assertTrue(game.configureAutoPlay(game.players[seat].member.id, decision, AutoPlay.Option.DISCARD, true));
            ticks(game, RiichiGame.DEAL_TICKS + RiichiGame.AUTO_ACTION_TICKS - 1);
            assertEquals(decision, game.decision());
            game.tick();
            assertEquals(drawn, game.players[seat].river.getLast().tile());
            assertTrue(game.players[seat].river.getLast().tsumogiri());
            assertFalse(game.act(game.players[seat].member.id, decision, 0));
            game.validate();
        }
    }

    @Test void riichiDiscardsAutomaticallyWithThePreferenceOffAndSurvivesReload() {
        RiichiGame game = GameLifecycleTest.started(RiichiPreset.TENHOU_4, 31);
        int seat = game.turn, drawn = game.players[seat].drawn;
        riichi(game, seat);
        game.options.set(seat, LegalActions.onTurn(game, seat));
        game = GameLifecycleTest.reloadMounted(game);
        assertFalse(game.players[seat].autoPlay.discard());
        ticks(game, RiichiGame.DEAL_TICKS + RiichiGame.AUTO_ACTION_TICKS);
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        game.validate();
    }

    @Test void manualRiichiTakesThePhysicalWallTileThenDiscardsWithoutTwoPlayerGestures() {
        RiichiSession session = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 8192);
        session.configureEquipment(true, Tile.set(false));
        for (int seat = 0; seat < 4; seat++) {
            session.join(new UUID(812, seat), "Player " + seat, seat);
        }
        GameLifecycleTest.startPositioned(session);
        RiichiGame game = session.game();
        while (game.phase != RiichiGame.Phase.DRAW) {
            boolean acted = false;
            for (int seat = 0; seat < 4; seat++) if (!game.actions(seat).isEmpty()) {
                assertTrue(game.act(game.players[seat].member.id, game.decision(), 0));
                acted = true;
                break;
            }
            assertTrue(acted);
        }
        int seat = game.turn, remaining = game.wall.remaining(), drawn = game.wall.tiles.get(game.wall.cursor);
        riichi(game, seat);
        ticks(game, RiichiGame.AUTO_ACTION_TICKS);
        assertEquals(RiichiGame.Phase.TURN, game.phase);
        assertEquals(drawn, game.players[seat].drawn);
        assertEquals(remaining - 1, game.wall.remaining());
        game.validate();
        ticks(game, RiichiGame.AUTO_ACTION_TICKS);
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        game.validate();
    }

    @Test void winAndKanOpportunitiesAreNotSilentlyThrownAway() {
        AutoPlay all = new AutoPlay(true, true, true, true, true);
        List<RiichiAction> tsumo = List.of(new RiichiAction(DISCARD, 7), new RiichiAction(TSUMO));
        List<RiichiAction> ron = List.of(new RiichiAction(PASS), new RiichiAction(PON, List.of(1, 2)), new RiichiAction(RON));
        assertEquals(1, all.action(RiichiGame.Phase.TURN, true, 7, tsumo));
        assertEquals(2, all.action(RiichiGame.Phase.REACTION, false, -1, ron));
        assertEquals(-1, all.with(AutoPlay.Option.WIN, false).action(RiichiGame.Phase.TURN, true, 7, tsumo));
        assertEquals(-1, all.with(AutoPlay.Option.WIN, false).action(RiichiGame.Phase.REACTION, false, -1, ron));
        assertEquals(0, all.action(RiichiGame.Phase.REACTION, false, -1, List.of(new RiichiAction(PASS), new RiichiAction(PON, List.of(1, 2)))));
        List<RiichiAction> kan = List.of(new RiichiAction(DISCARD, 7), new RiichiAction(CLOSED_KAN, List.of(4, 5, 6, 7)));
        assertEquals(-1, AutoPlay.DEFAULT.action(RiichiGame.Phase.TURN, true, 7, kan));
        assertEquals(0, all.action(RiichiGame.Phase.TURN, true, 7, kan));
        List<RiichiAction> north = List.of(new RiichiAction(DISCARD, 7), new RiichiAction(NUKI, 7));
        assertEquals(1, all.action(RiichiGame.Phase.TURN, true, 7, north));
        assertEquals(-1, all.with(AutoPlay.Option.KITA, false).action(RiichiGame.Phase.TURN, true, 7, north));
        var kitaOnly = AutoPlay.DEFAULT.with(AutoPlay.Option.KITA, true);
        assertEquals(1, kitaOnly.action(RiichiGame.Phase.TURN, false, 7, north));
        assertEquals(-1, kitaOnly.action(RiichiGame.Phase.TURN, false, 7, List.of(new RiichiAction(DISCARD, 7))));
        assertEquals(-1, kitaOnly.action(RiichiGame.Phase.REACTION, false, 7, north));
        assertEquals(-1, kitaOnly.action(RiichiGame.Phase.TURN, false, 7, List.of(new RiichiAction(NUKI, 7), new RiichiAction(TSUMO))));
        assertEquals(1, all.action(RiichiGame.Phase.TURN, false, 7, List.of(new RiichiAction(NUKI, 7), new RiichiAction(TSUMO))));
        assertEquals(-1, all.action(RiichiGame.Phase.TURN, false, Tile.ABSENT, List.of(new RiichiAction(DISCARD, 7))));
        assertEquals(-1, all.action(RiichiGame.Phase.HAND_END, false, -1, List.of(new RiichiAction(NEXT))));
    }

    @Test void disconnectedTrusteeUsesNetworkMahjongDefaultsWithoutChangingPreferencesOrClocks() {
        assertEquals(1, RiichiGame.disconnectedAction(RiichiGame.Phase.TURN, 7,
            List.of(new RiichiAction(CLOSED_KAN, List.of(4, 5, 6, 7)), new RiichiAction(TSUMO))));
        assertEquals(2, RiichiGame.disconnectedAction(RiichiGame.Phase.REACTION, Tile.ABSENT,
            List.of(new RiichiAction(PASS), new RiichiAction(PON, List.of(1, 2)), new RiichiAction(RON))));
        assertEquals(0, RiichiGame.disconnectedAction(RiichiGame.Phase.REACTION, Tile.ABSENT,
            List.of(new RiichiAction(PASS), new RiichiAction(PON, List.of(1, 2)))));
        assertEquals(1, RiichiGame.disconnectedAction(RiichiGame.Phase.TURN, 7,
            List.of(new RiichiAction(DISCARD, 3), new RiichiAction(DISCARD, 7), new RiichiAction(RIICHI, 3))));

        RiichiGame game = GameLifecycleTest.started(RiichiPreset.TENHOU_4, 37);
        int seat = game.turn, drawn = game.players[seat].drawn;
        UUID actor = game.players[seat].member.id;
        AutoPlay preference = game.players[seat].autoPlay;
        var mounted = new java.util.HashMap<UUID, Integer>();
        var connected = new java.util.HashSet<UUID>();
        for (int other = 0; other < game.rules().players(); other++) if (other != seat) {
            mounted.put(game.players[other].member.id, other);
            connected.add(game.players[other].member.id);
        }
        game.session.synchronizeSeats(mounted, connected);
        assertEquals(PlayerPresence.DISCONNECTED, game.session.roomView(null).seats().get(seat).presence());
        int move = game.moveTicks[seat], reserve = game.reserveTicks[seat];
        ticks(game, RiichiGame.DEAL_TICKS + RiichiGame.AUTO_ACTION_TICKS);
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        assertTrue(game.players[seat].river.getLast().tsumogiri());
        assertEquals(move, game.moveTicks[seat]);
        assertEquals(reserve, game.reserveTicks[seat]);
        assertEquals(preference, game.players[seat].autoPlay);

        game.session.join(actor, "Reconnected", seat);
        assertEquals(PlayerPresence.SEATED, game.session.roomView(null).seats().get(seat).presence());
        game.age = 1;
        if (!game.actions(seat).isEmpty()) assertTrue(game.view(actor).clocks().get(seat).active());
    }

    @Test void disconnectedManualPlayersHandleDealingAndResumeTheirClockOnlyAfterRemount() {
        RiichiSession session = new RiichiSession(new UUID(18, 19), RiichiPreset.TENHOU_3, 37);
        session.configureEquipment(true, Tile.set(true));
        for (int seat = 0; seat < 3; seat++) session.join(new UUID(81, seat), "Human " + seat, seat);
        GameLifecycleTest.startPositioned(session);
        RiichiGame game = session.game();
        UUID other = game.players[2].member.id;
        session.synchronizeSeats(java.util.Map.of(other, 2), java.util.Set.of(other));
        for (int tick = 0; game.phase != RiichiGame.Phase.TURN && tick < 500; tick++) {
            if (!game.actions(2).isEmpty()) game.act(other, game.decision(), 0);
            game.tick();
        }
        assertEquals(RiichiGame.Phase.TURN, game.phase, "Trustees must complete physical dealing");
        int seat = game.turn, drawn = game.players[seat].drawn;
        UUID actor = game.players[seat].member.id;
        int move = game.moveTicks[seat], reserve = game.reserveTicks[seat];
        ticks(game, RiichiGame.AUTO_ACTION_TICKS);
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        assertEquals(move, game.moveTicks[seat]);
        assertEquals(reserve, game.reserveTicks[seat]);
        for (int tick = 0; !(game.phase == RiichiGame.Phase.TURN && game.turn == seat) && tick < 500; tick++) {
            if (!game.actions(2).isEmpty()) game.act(other, game.decision(), 0);
            game.tick();
        }
        assertEquals(RiichiGame.Phase.TURN, game.phase);
        assertEquals(seat, game.turn);
        assertTrue(session.join(actor, "Human", seat));
        long decision = game.decision();
        int discards = game.players[seat].river.size();
        move = game.moveTicks[seat];
        ticks(game, RiichiGame.AUTO_ACTION_TICKS + 1);
        assertEquals(decision, game.decision(), "Returning stops temporary automation immediately");
        assertEquals(discards, game.players[seat].river.size());
        assertEquals(move - RiichiGame.AUTO_ACTION_TICKS - 1, game.moveTicks[seat]);
        assertEquals(AutoPlay.DEFAULT, game.players[seat].autoPlay);
        game.validate();
    }

    @Test void preferencesAreSeatPrivateAndDoNotResetClocksOrInvalidateOtherResponders() {
        RiichiGame game = GameLifecycleTest.started(RiichiPreset.TENHOU_4, 51);
        game.newDecision(RiichiGame.Phase.REACTION);
        game.options.set(1, List.of(new RiichiAction(PASS), new RiichiAction(PON, List.of(1, 2))));
        game.options.set(2, List.of(new RiichiAction(PASS)));
        game.options.set(3, List.of(new RiichiAction(PASS)));
        long decision = game.decision();
        ticks(game, 5);
        int[] move = game.moveTicks.clone(), reserve = game.reserveTicks.clone();
        assertTrue(game.configureAutoPlay(game.players[1].member.id, decision, AutoPlay.Option.NO_CALLS, true));
        assertEquals(decision, game.decision());
        assertArrayEquals(move, game.moveTicks);
        assertArrayEquals(reserve, game.reserveTicks);
        assertNull(game.view(null).autoPlay());
        assertTrue(game.view(game.players[1].member.id).autoPlay().noCalls());
        assertEquals(AutoPlay.DEFAULT, game.view(game.players[2].member.id).autoPlay());
        assertFalse(game.configureAutoPlay(UUID.randomUUID(), decision, AutoPlay.Option.WIN, true));
        assertFalse(game.configureAutoPlay(game.players[1].member.id, decision, AutoPlay.Option.KITA, true));
        assertFalse(game.configureAutoPlay(game.players[1].member.id, decision - 1, AutoPlay.Option.WIN, true));
        ticks(game, RiichiGame.AUTO_ACTION_TICKS - 5);
        assertEquals(0, game.replies[1]);
        assertEquals(decision, game.decision());
        assertTrue(game.act(game.players[2].member.id, decision, 0));
    }

    @Test void sortingCanBeDisabledWithoutMovingTheDrawnTileOrExposingAnotherHand() {
        RiichiGame game = GameLifecycleTest.started(RiichiPreset.TENHOU_4, 31);
        int seat = game.turn;
        var player = game.players[seat];
        var sorted = new ArrayList<>(player.hand);
        sorted.sort(Comparator.comparingInt(Tile::kind).thenComparingInt(Integer::intValue));
        sorted.remove(Integer.valueOf(player.drawn)); sorted.add(player.drawn);
        assertTrue(game.configureAutoPlay(player.member.id, game.decision(), AutoPlay.Option.SORT, false));
        assertEquals(sorted, player.hand, "Disabling sort must keep the order already shown to the player");
        assertEquals(sorted, game.view(player.member.id).seats().get(seat).hand());
        assertTrue(game.view(game.players[game.next(seat)].member.id).seats().get(seat).hand().stream().allMatch(t -> t == Tile.HIDDEN));
        int source = sorted.getFirst(), target = sorted.get(3);
        assertFalse(game.reorderHand(game.players[game.next(seat)].member.id, game.decision(), source, target, false));
        assertFalse(game.reorderHand(player.member.id, game.decision() - 1, source, target, false));
        assertTrue(game.reorderHand(player.member.id, game.decision(), source, target, false));
        sorted.remove(Integer.valueOf(source)); sorted.add(sorted.indexOf(target), source);
        assertEquals(sorted, game.view(player.member.id).seats().get(seat).hand());
        assertTrue(game.reorderHand(player.member.id, game.decision(), player.drawn, target, true));
        assertNotEquals(player.drawn, game.view(player.member.id).seats().get(seat).hand().getLast());
        assertTrue(game.configureAutoPlay(player.member.id, game.decision(), AutoPlay.Option.SORT, true));
        var resorted = new ArrayList<>(player.hand);
        resorted.sort(Comparator.comparingInt(Tile::kind).thenComparingInt(Integer::intValue));
        resorted.remove(Integer.valueOf(player.drawn)); resorted.add(player.drawn);
        assertEquals(resorted, game.view(player.member.id).seats().get(seat).hand());
        assertFalse(game.reorderHand(player.member.id, game.decision(), source, target, false));
        game.startHand();
        assertTrue(player.autoPlay.sort());
    }

    @Test void preferencesPersistAcrossReloadAndResetForEachHand() {
        RiichiGame game = GameLifecycleTest.started(RiichiPreset.TENHOU_3, 31);
        UUID actor = game.players[0].member.id;
        for (var option : AutoPlay.Option.values())
            assertTrue(game.configureAutoPlay(actor, game.decision(), option, option != AutoPlay.Option.SORT));
        AutoPlay expected = new AutoPlay(false, true, true, true, true);
        assertEquals(expected, game.view(actor).autoPlay());
        game = GameLifecycleTest.reloadMounted(game);
        game.validate();
        assertEquals(expected, game.view(actor).autoPlay());
        game.startHand();
        assertEquals(AutoPlay.DEFAULT, game.view(actor).autoPlay());
        RiichiSession session = game.session;
        assertTrue(session.requestExit(actor));
        long vote = session.exitVote.id();
        for (int seat = 1; seat < game.rules.players(); seat++)
            assertTrue(session.answerExit(game.players[seat].member.id, vote, true));
        UUID newcomer = UUID.randomUUID();
        assertTrue(session.join(newcomer, "New player", 0));
        GameLifecycleTest.startPositioned(session);
        assertEquals(AutoPlay.DEFAULT, session.game().view(newcomer).autoPlay());
        assertFalse(session.game().configureAutoPlay(actor, session.decision(), AutoPlay.Option.WIN, true));
    }

    @Test void ordinaryClockSpendsThirtySecondsThenReserveAndReloadKeepsHostValues() {
        RiichiSession session = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 8192);
        session.configureEquipment(true, Tile.set(false));
        for (int seat = 0; seat < 4; seat++) {
            session.join(new UUID(812, seat), "Player " + seat, seat);
        }
        GameLifecycleTest.startPositioned(session);
        RiichiGame game = session.game();
        while (game.phase != RiichiGame.Phase.TURN) {
            for (int seat = 0; seat < 4; seat++) if (!game.actions(seat).isEmpty()) {
                assertTrue(game.act(game.players[seat].member.id, game.decision(), 0));
                break;
            }
        }
        int seat = game.turn, drawn = game.players[seat].drawn;
        ticks(game, 600);
        assertEquals(0, game.moveTicks[seat]);
        assertEquals(2400, game.reserveTicks[seat]);
        ticks(game, 37);
        game = GameLifecycleTest.reloadMounted(game);
        assertEquals(TimeControl.MANUAL, game.timeControl);
        assertEquals(2363, game.reserveTicks[seat]);
        ticks(game, 2363);
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        game.validate();
    }

    @Test void ordinaryTablesHaveLongerDefaultsAndEquipmentEditsPreserveHostClockSettings() {
        RiichiSession game = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 1);
        UUID host = UUID.randomUUID();
        game.join(host, "Host", 0);
        game.configureEquipment(true, List.of());
        assertEquals(new TimeControl(120, 30), game.roomSettings().timeControl());
        assertNull(game.view(host));
        assertNull(game.game());
        var custom = new TimeControl(240, 60);
        assertTrue(game.configureClock(host, custom));
        game.configureEquipment(true, Tile.set(false));
        assertEquals(custom, game.roomSettings().timeControl());
        game.validate();
    }

    @Test void manualClockUsesThirtyThenOneHundredTwentySecondsAcrossReload() {
        RiichiSession session = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 31);
        session.configureEquipment(true, Tile.set(false));
        for (int seat = 0; seat < 4; seat++) session.join(new UUID(812, seat), "Player " + seat, seat);
        GameLifecycleTest.startPositioned(session);
        RiichiGame game = session.game();
        while (game.phase != RiichiGame.Phase.TURN) {
            for (int seat = 0; seat < 4; seat++) if (!game.actions(seat).isEmpty()) {
                assertTrue(game.act(game.players[seat].member.id, game.decision(), 0));
                break;
            }
        }
        java.util.Arrays.fill(game.reserveTicks, 120 * 20);
        int seat = game.turn, drawn = game.players[seat].drawn;
        game.newDecision(RiichiGame.Phase.TURN);
        game.options.set(seat, LegalActions.onTurn(game, seat));
        ticks(game, 30 * 20);
        assertEquals(0, game.moveTicks[seat]);
        assertEquals(120 * 20, game.reserveTicks[seat]);
        ticks(game, 119 * 20 + 19);
        game = GameLifecycleTest.reloadMounted(game);
        assertEquals(TimeControl.MANUAL, game.view(null).timeControl());
        assertEquals(1, game.reserveTicks[seat]);
        assertTrue(game.players[seat].river.isEmpty());
        game.tick();
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        game.validate();
    }
}
