package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.Action.Type.*;

class AutoPlayTest {
    private static void ticks(Game game, int count) { for (int i = 0; i < count; i++) game.tick(); }
    private static void riichi(Game game, int seat) {
        game.players[seat].riichi = true;
        game.players[seat].points -= 1000;
        game.riichiSticks++;
    }

    @Test void automaticDiscardUsesTheDrawnPhysicalTileAfterTheDealGracePeriod() {
        for (RuleSet rules : RuleSet.values()) {
            Game game = GameLifecycleTest.started(rules, 31);
            int seat = game.turn, drawn = game.players[seat].drawn;
            long decision = game.decision;
            assertTrue(game.configureAutoPlay(game.players[seat].id, decision, AutoPlay.Option.DISCARD, true));
            ticks(game, Game.DEAL_TICKS + Game.AUTO_ACTION_TICKS - 1);
            assertEquals(decision, game.decision);
            game.tick();
            assertEquals(drawn, game.players[seat].river.getLast().tile());
            assertTrue(game.players[seat].river.getLast().tsumogiri());
            assertFalse(game.act(game.players[seat].id, decision, 0));
            game.validate();
        }
    }

    @Test void riichiDiscardsAutomaticallyWithThePreferenceOffAndSurvivesReload() {
        Game game = GameLifecycleTest.started(RuleSet.TENHOU_4, 31);
        int seat = game.turn, drawn = game.players[seat].drawn;
        riichi(game, seat);
        game.options.set(seat, LegalActions.onTurn(game, seat));
        Gson json = new Gson();
        game = json.fromJson(json.toJson(game), Game.class);
        assertFalse(game.players[seat].autoPlay.discard());
        ticks(game, Game.DEAL_TICKS + Game.AUTO_ACTION_TICKS);
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        game.validate();
    }

    @Test void manualRiichiTakesThePhysicalWallTileThenDiscardsWithoutTwoPlayerGestures() {
        Game game = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 8192);
        game.configureEquipment(true, Tile.set(false));
        for (int seat = 0; seat < 4; seat++) {
            game.join(new UUID(812, seat), "Player " + seat, seat);
        }
        GameLifecycleTest.startPositioned(game);
        while (game.phase != Game.Phase.DRAW) {
            boolean acted = false;
            for (int seat = 0; seat < 4; seat++) if (!game.actions(seat).isEmpty()) {
                assertTrue(game.act(game.players[seat].id, game.decision, 0));
                acted = true;
                break;
            }
            assertTrue(acted);
        }
        int seat = game.turn, remaining = game.wall.remaining(), drawn = game.wall.tiles.get(game.wall.cursor);
        riichi(game, seat);
        ticks(game, Game.AUTO_ACTION_TICKS);
        assertEquals(Game.Phase.TURN, game.phase);
        assertEquals(drawn, game.players[seat].drawn);
        assertEquals(remaining - 1, game.wall.remaining());
        game.validate();
        ticks(game, Game.AUTO_ACTION_TICKS);
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        game.validate();
    }

    @Test void winAndKanOpportunitiesAreNotSilentlyThrownAway() {
        AutoPlay all = new AutoPlay(true, true, true, true, true);
        List<Action> tsumo = List.of(new Action(DISCARD, 7), new Action(TSUMO));
        List<Action> ron = List.of(new Action(PASS), new Action(PON, List.of(1, 2)), new Action(RON));
        assertEquals(1, all.action(Game.Phase.TURN, true, 7, tsumo));
        assertEquals(2, all.action(Game.Phase.REACTION, false, -1, ron));
        assertEquals(-1, all.with(AutoPlay.Option.WIN, false).action(Game.Phase.TURN, true, 7, tsumo));
        assertEquals(-1, all.with(AutoPlay.Option.WIN, false).action(Game.Phase.REACTION, false, -1, ron));
        assertEquals(0, all.action(Game.Phase.REACTION, false, -1, List.of(new Action(PASS), new Action(PON, List.of(1, 2)))));
        List<Action> kan = List.of(new Action(DISCARD, 7), new Action(CLOSED_KAN, List.of(4, 5, 6, 7)));
        assertEquals(-1, AutoPlay.DEFAULT.action(Game.Phase.TURN, true, 7, kan));
        assertEquals(0, all.action(Game.Phase.TURN, true, 7, kan));
        List<Action> north = List.of(new Action(DISCARD, 7), new Action(NUKI, 7));
        assertEquals(1, all.action(Game.Phase.TURN, true, 7, north));
        assertEquals(-1, all.with(AutoPlay.Option.KITA, false).action(Game.Phase.TURN, true, 7, north));
        var kitaOnly = AutoPlay.DEFAULT.with(AutoPlay.Option.KITA, true);
        assertEquals(1, kitaOnly.action(Game.Phase.TURN, false, 7, north));
        assertEquals(-1, kitaOnly.action(Game.Phase.TURN, false, 7, List.of(new Action(DISCARD, 7))));
        assertEquals(-1, kitaOnly.action(Game.Phase.REACTION, false, 7, north));
        assertEquals(-1, kitaOnly.action(Game.Phase.TURN, false, 7, List.of(new Action(NUKI, 7), new Action(TSUMO))));
        assertEquals(1, all.action(Game.Phase.TURN, false, 7, List.of(new Action(NUKI, 7), new Action(TSUMO))));
        assertEquals(-1, all.action(Game.Phase.TURN, false, Tile.ABSENT, List.of(new Action(DISCARD, 7))));
        assertEquals(-1, all.action(Game.Phase.HAND_END, false, -1, List.of(new Action(NEXT))));
    }

    @Test void disconnectedTrusteeUsesNetworkMahjongDefaultsWithoutChangingPreferencesOrClocks() {
        assertEquals(1, Game.disconnectedAction(Game.Phase.TURN, 7,
            List.of(new Action(CLOSED_KAN, List.of(4, 5, 6, 7)), new Action(TSUMO))));
        assertEquals(2, Game.disconnectedAction(Game.Phase.REACTION, Tile.ABSENT,
            List.of(new Action(PASS), new Action(PON, List.of(1, 2)), new Action(RON))));
        assertEquals(0, Game.disconnectedAction(Game.Phase.REACTION, Tile.ABSENT,
            List.of(new Action(PASS), new Action(PON, List.of(1, 2)))));
        assertEquals(1, Game.disconnectedAction(Game.Phase.TURN, 7,
            List.of(new Action(DISCARD, 3), new Action(DISCARD, 7), new Action(RIICHI, 3))));

        Game game = GameLifecycleTest.started(RuleSet.TENHOU_4, 37);
        int seat = game.turn, drawn = game.players[seat].drawn;
        UUID actor = game.players[seat].id;
        AutoPlay preference = game.players[seat].autoPlay;
        var mounted = new java.util.HashMap<UUID, Integer>();
        var connected = new java.util.HashSet<UUID>();
        for (int other = 0; other < game.rules().players(); other++) if (other != seat) {
            mounted.put(game.players[other].id, other);
            connected.add(game.players[other].id);
        }
        game.synchronizeSeats(mounted, connected);
        assertEquals(PlayerPresence.DISCONNECTED, game.roomView().seats().get(seat).presence());
        int move = game.moveTicks[seat], reserve = game.reserveTicks[seat];
        ticks(game, Game.DEAL_TICKS + Game.AUTO_ACTION_TICKS);
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        assertTrue(game.players[seat].river.getLast().tsumogiri());
        assertEquals(move, game.moveTicks[seat]);
        assertEquals(reserve, game.reserveTicks[seat]);
        assertEquals(preference, game.players[seat].autoPlay);

        game.join(actor, "Reconnected", seat);
        assertEquals(PlayerPresence.SEATED, game.roomView().seats().get(seat).presence());
        game.age = 1;
        if (!game.actions(seat).isEmpty()) assertTrue(game.view(actor).clocks().get(seat).active());
    }

    @Test void disconnectedManualPlayersHandleDealingAndResumeTheirClockOnlyAfterRemount() {
        Game game = new Game(new UUID(18, 19), RuleSet.TENHOU_3, 37);
        game.configureEquipment(true, Tile.set(true));
        for (int seat = 0; seat < 3; seat++) game.join(new UUID(81, seat), "Human " + seat, seat);
        GameLifecycleTest.startPositioned(game);
        game.synchronizeSeats(java.util.Map.of(), java.util.Set.of());
        for (int tick = 0; game.phase != Game.Phase.TURN && tick < 500; tick++) game.tick();
        assertEquals(Game.Phase.TURN, game.phase, "Trustees must complete physical dealing");
        int seat = game.turn, drawn = game.players[seat].drawn;
        UUID actor = game.players[seat].id;
        int move = game.moveTicks[seat], reserve = game.reserveTicks[seat];
        ticks(game, Game.AUTO_ACTION_TICKS);
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        assertEquals(move, game.moveTicks[seat]);
        assertEquals(reserve, game.reserveTicks[seat]);
        for (int tick = 0; !(game.phase == Game.Phase.TURN && game.turn == seat) && tick < 500; tick++) game.tick();
        assertEquals(Game.Phase.TURN, game.phase);
        assertEquals(seat, game.turn);
        assertTrue(game.join(actor, "Human", seat));
        long decision = game.decision;
        int discards = game.players[seat].river.size();
        move = game.moveTicks[seat];
        ticks(game, Game.AUTO_ACTION_TICKS + 1);
        assertEquals(decision, game.decision, "Returning stops temporary automation immediately");
        assertEquals(discards, game.players[seat].river.size());
        assertEquals(move - Game.AUTO_ACTION_TICKS - 1, game.moveTicks[seat]);
        assertEquals(AutoPlay.DEFAULT, game.players[seat].autoPlay);
        game.validate();
    }

    @Test void preferencesAreSeatPrivateAndDoNotResetClocksOrInvalidateOtherResponders() {
        Game game = GameLifecycleTest.started(RuleSet.TENHOU_4, 51);
        game.newDecision(Game.Phase.REACTION);
        game.options.set(1, List.of(new Action(PASS), new Action(PON, List.of(1, 2))));
        game.options.set(2, List.of(new Action(PASS)));
        game.options.set(3, List.of(new Action(PASS)));
        long decision = game.decision;
        ticks(game, 5);
        int[] move = game.moveTicks.clone(), reserve = game.reserveTicks.clone();
        assertTrue(game.configureAutoPlay(game.players[1].id, decision, AutoPlay.Option.NO_CALLS, true));
        assertEquals(decision, game.decision);
        assertArrayEquals(move, game.moveTicks);
        assertArrayEquals(reserve, game.reserveTicks);
        assertNull(game.view(null).autoPlay());
        assertTrue(game.view(game.players[1].id).autoPlay().noCalls());
        assertEquals(AutoPlay.DEFAULT, game.view(game.players[2].id).autoPlay());
        assertFalse(game.configureAutoPlay(UUID.randomUUID(), decision, AutoPlay.Option.WIN, true));
        assertFalse(game.configureAutoPlay(game.players[1].id, decision, AutoPlay.Option.KITA, true));
        assertFalse(game.configureAutoPlay(game.players[1].id, decision - 1, AutoPlay.Option.WIN, true));
        ticks(game, Game.AUTO_ACTION_TICKS - 5);
        assertEquals(0, game.replies[1]);
        assertEquals(decision, game.decision);
        assertTrue(game.act(game.players[2].id, decision, 0));
    }

    @Test void sortingCanBeDisabledWithoutMovingTheDrawnTileOrExposingAnotherHand() {
        Game game = GameLifecycleTest.started(RuleSet.TENHOU_4, 31);
        int seat = game.turn;
        var player = game.players[seat];
        assertTrue(game.configureAutoPlay(player.id, game.decision, AutoPlay.Option.SORT, false));
        assertEquals(player.hand, game.view(player.id).seats().get(seat).hand());
        assertTrue(game.view(game.players[game.next(seat)].id).seats().get(seat).hand().stream().allMatch(t -> t == Tile.HIDDEN));
        assertTrue(game.configureAutoPlay(player.id, game.decision, AutoPlay.Option.SORT, true));
        var sorted = new ArrayList<>(player.hand);
        sorted.sort(Comparator.comparingInt(Tile::kind).thenComparingInt(Integer::intValue));
        sorted.remove(Integer.valueOf(player.drawn)); sorted.add(player.drawn);
        assertEquals(sorted, game.view(player.id).seats().get(seat).hand());
        game.startHand();
        assertTrue(player.autoPlay.sort());
    }

    @Test void preferencesPersistAcrossHandsAndReloadButAreClearedWhenTheSeatIsReleased() {
        Game game = GameLifecycleTest.started(RuleSet.TENHOU_3, 31);
        UUID actor = game.players[0].id;
        for (var option : AutoPlay.Option.values())
            assertTrue(game.configureAutoPlay(actor, game.decision, option, option != AutoPlay.Option.SORT));
        AutoPlay expected = new AutoPlay(false, true, true, true, true);
        game.startHand();
        assertEquals(expected, game.view(actor).autoPlay());
        Gson json = new Gson();
        game = json.fromJson(json.toJson(game), Game.class);
        game.validate();
        assertEquals(expected, game.view(actor).autoPlay());
        game.wall = null;
        game.newDecision(Game.Phase.LOBBY);
        assertTrue(game.act(actor, game.decision, Game.indexOf(game.actions(game.seatOf(actor)), LEAVE_ROOM)));
        UUID newcomer = UUID.randomUUID();
        assertTrue(game.join(newcomer, "New player", 0));
        assertEquals(AutoPlay.DEFAULT, game.view(newcomer).autoPlay());
        assertFalse(game.configureAutoPlay(actor, game.decision, AutoPlay.Option.WIN, true));
    }

    @Test void ordinaryClockSpendsThirtySecondsThenReserveAndReloadKeepsHostValues() {
        Game game = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 8192);
        game.configureEquipment(true, Tile.set(false));
        for (int seat = 0; seat < 4; seat++) {
            game.join(new UUID(812, seat), "Player " + seat, seat);
        }
        GameLifecycleTest.startPositioned(game);
        while (game.phase != Game.Phase.TURN) {
            for (int seat = 0; seat < 4; seat++) if (!game.actions(seat).isEmpty()) {
                assertTrue(game.act(game.players[seat].id, game.decision, 0));
                break;
            }
        }
        int seat = game.turn, drawn = game.players[seat].drawn;
        ticks(game, 600);
        assertEquals(0, game.moveTicks[seat]);
        assertEquals(2400, game.reserveTicks[seat]);
        ticks(game, 37);
        Gson json = new Gson();
        game = json.fromJson(json.toJson(game), Game.class);
        game.validate();
        assertEquals(TimeControl.MANUAL, game.timeControl);
        assertEquals(2363, game.reserveTicks[seat]);
        ticks(game, 2363);
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        game.validate();
    }

    @Test void ordinaryTablesHaveLongerDefaultsAndEquipmentEditsPreserveHostClockSettings() {
        Game game = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 1);
        UUID host = UUID.randomUUID();
        game.join(host, "Host", 0);
        game.configureEquipment(true, List.of());
        assertEquals(new TimeControl(120, 30), game.view(host).timeControl());
        assertNull(game.view(host).autoPlay());
        assertFalse(game.configureAutoPlay(host, game.decision, AutoPlay.Option.WIN, true));
        var custom = new TimeControl(240, 60);
        assertTrue(game.configureClock(host, custom));
        game.configureEquipment(true, Tile.set(false));
        assertEquals(custom, game.view(host).timeControl());
        game.validate();
    }

    @Test void preferencesSurviveHandsAndSavesButNotSeatRelease() {
        Game game = new Game(UUID.randomUUID(), RuleSet.TENHOU_3, 1);
        UUID host = UUID.randomUUID();
        game.join(host, "Host", 0);
        for (var option : AutoPlay.Option.values())
            assertTrue(game.configureAutoPlay(host, game.decision, option, option != AutoPlay.Option.SORT));
        var expected = new AutoPlay(false, true, true, true, true);
        Gson json = new Gson();
        game = json.fromJson(json.toJson(game), Game.class);
        game.validate();
        assertEquals(expected, game.view(host).autoPlay());
        game.wall = null;
        game.newDecision(Game.Phase.LOBBY);
        assertTrue(game.act(host, game.decision, Game.indexOf(game.actions(game.seatOf(host)), LEAVE_ROOM)));
        UUID replacement = UUID.randomUUID();
        assertTrue(game.join(replacement, "Replacement", 0));
        assertEquals(AutoPlay.DEFAULT, game.view(replacement).autoPlay());

        game = GameLifecycleTest.started(RuleSet.TENHOU_4, 31);
        host = game.players[0].id;
        assertTrue(game.configureAutoPlay(host, game.decision, AutoPlay.Option.SORT, false));
        assertTrue(game.configureAutoPlay(host, game.decision, AutoPlay.Option.WIN, true));
        expected = game.view(host).autoPlay();
        game.startHand();
        assertEquals(expected, game.view(host).autoPlay());
        game = json.fromJson(json.toJson(game), Game.class);
        game.validate();
        assertEquals(expected, game.view(host).autoPlay());
    }

    @Test void manualClockUsesThirtyThenOneHundredTwentySecondsAcrossReload() {
        Game game = GameLifecycleTest.started(RuleSet.TENHOU_4, 31);
        game.manual = true;
        game.timeControl = TimeControl.MANUAL;
        java.util.Arrays.fill(game.reserveTicks, 120 * 20);
        int seat = game.turn, drawn = game.players[seat].drawn;
        game.newDecision(Game.Phase.TURN);
        game.options.set(seat, LegalActions.onTurn(game, seat));
        ticks(game, 30 * 20);
        assertEquals(0, game.moveTicks[seat]);
        assertEquals(120 * 20, game.reserveTicks[seat]);
        ticks(game, 119 * 20 + 19);
        var json = new Gson();
        game = json.fromJson(json.toJson(game), Game.class);
        assertEquals(TimeControl.MANUAL, game.view(null).timeControl());
        assertEquals(1, game.reserveTicks[seat]);
        assertTrue(game.players[seat].river.isEmpty());
        game.tick();
        assertEquals(drawn, game.players[seat].river.getLast().tile());
        game.validate();
    }
}
