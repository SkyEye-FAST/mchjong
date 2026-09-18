package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TableControlTest {
    private static UUID id(int seat) { return new UUID(42, seat); }

    @Test void customRulesAreAtomicHostOnlyAndPersistWithCompletedHands() {
        var json = new Gson();
        for (var preset : RuleSet.values()) {
            var rules = preset.config();
            assertFalse(rules.custom());
            assertEquals(rules, json.fromJson(json.toJson(rules), RuleConfig.class));
            var variant = rules.with(RuleOption.KUITAN, 0).with(RuleOption.RED_FIVES, RedFives.FOUR.ordinal());
            assertEquals(!(preset.tenhou() || preset.mahjongSoul()), variant.custom());
            assertTrue(variant.with(RuleOption.STARTING_POINTS, rules.startingPoints() + 100).custom());
        }
        var custom = RuleSet.M_LEAGUE.config().with(RuleOption.STARTING_POINTS, 28000)
            .with(RuleOption.RETURN_POINTS, 35000).with(RuleOption.TARGET_POINTS, 40000)
            .with(RuleOption.UMA_1, 25600).with(RuleOption.IPPATSU, 0).with(RuleOption.RED_FIVES, RedFives.NONE.ordinal());
        assertTrue(custom.custom());
        assertThrows(IllegalArgumentException.class, () -> custom.with(RuleOption.IPPATSU, 2));
        assertThrows(IllegalArgumentException.class, () -> custom.with(RuleOption.STARTING_POINTS, 28001));
        assertThrows(IllegalArgumentException.class, () -> new RuleConfig(RuleSet.WRC, java.util.Map.of()));
        assertThrows(UnsupportedOperationException.class, () -> custom.settings().put(RuleOption.IPPATSU, 1));
        Game lobby = new Game(UUID.randomUUID(), RuleSet.M_LEAGUE, 1);
        lobby.join(id(0), "Host", 0); lobby.join(id(1), "Guest", 1);
        lobby.players[1].ready = true;
        long token = lobby.decision;
        var before = json.toJson(lobby);
        assertFalse(lobby.configureRules(id(1), token, custom));
        assertFalse(lobby.configureRules(id(3), token, custom));
        assertFalse(lobby.configureRules(id(0), token - 1, custom));
        assertEquals(before, json.toJson(lobby));
        assertTrue(lobby.configureRules(id(0), token, custom));
        assertFalse(lobby.players[1].ready);
        assertEquals(28000, lobby.points(0));
        assertEquals(28000, lobby.points(1));
        assertFalse(lobby.equipped(), "Changing rules cannot recolor the physical tiles");
        assertFalse(lobby.configureRules(id(0), token, RuleSet.M_LEAGUE.config()));
        lobby = json.fromJson(json.toJson(lobby), Game.class);
        lobby.validate();
        assertEquals(custom, lobby.rules());
        assertTrue(lobby.join(id(3), "Fourth seat", 3));
        assertFalse(lobby.configureRules(id(0), lobby.decision, RuleSet.TENHOU_3.config()));
        lobby.leave(id(3));
        assertTrue(lobby.configureEquipment(false, Tile.set(false, RedFives.NONE)));
        GameLifecycleTest.startPositioned(lobby);
        assertFalse(lobby.configureRules(id(0), lobby.decision, RuleSet.M_LEAGUE.config()));
        assertEquals(custom, lobby.replay.rules());
        Settlement.abort(lobby, "nine_terminals");
        var replay = lobby.pendingReplays().getFirst();
        assertEquals(custom, json.fromJson(json.toJson(replay), ReplayMatch.class).rules());
        lobby.validate();
    }

    private static Game game(int humans, RuleSet rules, boolean open) {
        Game game = new Game(UUID.randomUUID(), rules, 123);
        for (int seat = 0; seat < humans; seat++) assertTrue(game.join(id(seat), "Player " + seat, seat));
        game.configureWorld(open, false);
        GameLifecycleTest.startPositioned(game);
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
            GameLifecycleTest.startPositioned(game);
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

    @Test void worldPolicyDoesNotChangeReadinessOrRoomRulesAndNeverRevealsToSpectators() {
        Game lobby = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 1);
        lobby.join(id(0), "Host", 0); lobby.join(id(1), "Guest", 1);
        lobby.players[1].ready = true;
        long token = lobby.decision;
        lobby.configureWorld(true, true);
        assertTrue(lobby.players[1].ready);
        assertEquals(token, lobby.decision);
        assertTrue(lobby.configureRules(id(0), token, RuleSet.WRC.config()));
        assertTrue(lobby.openHands);
        assertTrue(lobby.roomView().invitationTeleport());
        for (boolean open : new boolean[]{false, true}) {
            Game game = game(2, RuleSet.MAHJONG_SOUL_3, open);
            game.configureWorld(!open, true);
            game.configureWorld(open, false);
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
        assertFalse(game.openHands, "Table saves cannot override world policy");
        assertTrue(game.answerExit(id(1), game.exitVote.id(), true));
        assertEquals(replays, game.pendingReplays());
        game.validate();
    }
}
