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
            assertEquals(1, rules.minHan());
            assertEquals(2, rules.matchLength());
            var variant = rules.with(RuleOption.KUITAN, 0).with(RuleOption.RED_FIVES, RedFives.FOUR.ordinal())
                .with(RuleOption.MIN_HAN, 4).with(RuleOption.MATCH_LENGTH, 1);
            assertEquals(!(preset.tenhou() || preset.mahjongSoul()), variant.custom());
            assertEquals(4, variant.withPreset(RuleSet.TENHOU_4).minHan());
            assertEquals(1, variant.withPreset(RuleSet.TENHOU_4).matchLength());
            assertEquals(1, variant.withPreset(RuleSet.WRC).minHan());
            assertEquals(2, variant.withPreset(RuleSet.WRC).matchLength());
            assertTrue(variant.with(RuleOption.STARTING_POINTS, rules.startingPoints() + 100).custom());
        }
        var custom = RuleSet.M_LEAGUE.config().with(RuleOption.STARTING_POINTS, 28000)
            .with(RuleOption.RETURN_POINTS, 35000).with(RuleOption.TARGET_POINTS, 40000)
            .with(RuleOption.UMA_1, 25600).with(RuleOption.IPPATSU, 0).with(RuleOption.RED_FIVES, RedFives.NONE.ordinal())
            .with(RuleOption.MIN_HAN, 2).with(RuleOption.MATCH_LENGTH, 1).with(RuleOption.BANKRUPTCY, 1);
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
        int leave = GameLifecycleTest.index(lobby.view(id(3)), Action.Type.LEAVE_ROOM);
        assertTrue(lobby.act(id(3), lobby.decision, leave));
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
