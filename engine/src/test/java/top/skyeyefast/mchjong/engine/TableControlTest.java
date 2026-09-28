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

    private static Game game(int humans, RuleSet rules, boolean openHands) {
        Game game = new Game(UUID.randomUUID(), rules, 123);
        for (int seat = 0; seat < humans; seat++) assertTrue(game.join(id(seat), "Player " + seat, seat));
        if (openHands) assertTrue(game.configureOpenHands(id(0), game.decision, true));
        GameLifecycleTest.startPositioned(game);
        assertEquals(Game.Phase.TURN, game.phase());
        return game;
    }

    @Test void handVisibilityIsHostOnlyClearsReadinessAndPersistsPerRoom() {
        assertArrayEquals(new PlayerHandVisibility[]{PlayerHandVisibility.SELF, PlayerHandVisibility.RIICHI,
            PlayerHandVisibility.ALL}, PlayerHandVisibility.values());
        Game lobby = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 1);
        lobby.join(id(0), "Host", 0); lobby.join(id(1), "Guest", 1);
        lobby.players[1].ready = true;
        long token = lobby.decision;
        assertFalse(lobby.configureHandVisibility(id(1), token, PlayerHandVisibility.ALL));
        assertFalse(lobby.configureHandVisibility(null, token, PlayerHandVisibility.ALL));
        assertFalse(lobby.configureHandVisibility(id(0), token - 1, PlayerHandVisibility.ALL));
        assertTrue(lobby.players[1].ready);
        assertTrue(lobby.configureHandVisibility(id(0), token, PlayerHandVisibility.ALL));
        assertFalse(lobby.players[1].ready);
        assertNotEquals(token, lobby.decision);
        assertTrue(lobby.configureRules(id(0), lobby.decision, RuleSet.WRC.config()));
        assertEquals(PlayerHandVisibility.ALL, lobby.playerHandVisibility);
        assertTrue(lobby.configureOpenHands(id(0), lobby.decision, true));
        var saved = new Gson().fromJson(new Gson().toJson(lobby), Game.class);
        saved.validate();
        assertEquals(PlayerHandVisibility.ALL, saved.playerHandVisibility);
        assertTrue(saved.openHands);
        assertEquals(PlayerHandVisibility.SELF, new Game(UUID.randomUUID(), RuleSet.WRC, 2).playerHandVisibility);
        assertFalse(new Game(UUID.randomUUID(), RuleSet.WRC, 2).openHands);
    }

    @Test void convenienceHintsAreControlledByTheLobbyHostAndPersistWithTheRoom() {
        var lobby = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 1);
        lobby.join(id(0), "Host", 0);
        lobby.join(id(1), "Guest", 1);
        long token = lobby.decision;
        assertFalse(lobby.roomView().convenienceHints());
        assertFalse(lobby.configureConvenienceHints(id(1), token, true));
        assertFalse(lobby.configureConvenienceHints(id(0), token - 1, true));
        assertTrue(lobby.configureConvenienceHints(id(0), token, true));
        assertTrue(lobby.roomView().convenienceHints());
        assertFalse(lobby.configureConvenienceHints(id(0), token, true));
        var saved = new Gson().fromJson(new Gson().toJson(lobby), Game.class);
        saved.validate();
        assertTrue(saved.roomView().convenienceHints());
        GameLifecycleTest.startPositioned(lobby);
        assertFalse(lobby.configureConvenienceHints(id(0), lobby.decision, false));
    }

    @Test void worldPolicyInvalidatesLobbyActionsWhenTheirAvailabilityChanges() {
        var lobby = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 1);
        assertTrue(lobby.join(id(0), "Host", 0));
        var before = lobby.view(id(0));
        int fillBots = java.util.stream.IntStream.range(0, before.actions().size())
            .filter(index -> before.actions().get(index).type() == Action.Type.FILL_BOTS).findFirst().orElseThrow();

        lobby.configureWorld(new WorldPolicy(true, false, true, 5_000, true, false, true, true, null));

        assertNotEquals(before.decision(), lobby.decision);
        assertTrue(lobby.view(id(0)).actions().stream().noneMatch(action -> action.type() == Action.Type.FILL_BOTS
            || action.type() == Action.Type.SET_BOT));
        assertFalse(lobby.act(id(0), before.decision(), fillBots));
    }

    @Test void stockCompositionChangeClearsLobbyReadinessAndRespectsPreset() {
        var rules = RuleSet.MAHJONG_SOUL_4.config().with(RuleOption.RED_FIVES, RedFives.NONE.ordinal());
        var lobby = new Game(UUID.randomUUID(), rules, 1);
        lobby.join(id(0), "Host", 0);
        lobby.players[0].ready = true;
        long token = lobby.decision;
        assertTrue(lobby.configureStockRedFives(RedFives.THREE));
        assertEquals(RedFives.THREE, lobby.rules().redFives());
        assertFalse(lobby.players[0].ready);
        assertNotEquals(token, lobby.decision);
        assertFalse(lobby.configureStockRedFives(RedFives.THREE));

        var fixed = new Game(UUID.randomUUID(), RuleSet.WRC, 1);
        assertFalse(fixed.configureStockRedFives(RedFives.THREE));
        assertEquals(RedFives.NONE, fixed.rules().redFives());
    }

    @Test void participantAndSpectatorVisibilityAreRedactedIndependentlyBeforeSerialization() {
        for (var mode : PlayerHandVisibility.values()) {
            Game game = game(2, RuleSet.MAHJONG_SOUL_3, false);
            assertFalse(game.configureHandVisibility(id(0), game.decision, mode));
            game.playerHandVisibility = mode;
            game.players[1].riichi = true;
            for (int viewer : new int[]{0, 1}) {
                var view = game.view(id(viewer));
                for (int seat = 0; seat < game.rules.players(); seat++) {
                    boolean visible = seat == viewer || mode == PlayerHandVisibility.ALL
                        || mode == PlayerHandVisibility.RIICHI && viewer == 1;
                    var hand = view.seats().get(seat);
                    assertTrue(hand.hand().stream().allMatch(tile -> visible ? tile >= 0 : tile == Tile.HIDDEN));
                    if (game.players[seat].drawn >= 0) assertEquals(visible ? game.players[seat].drawn : Tile.HIDDEN, hand.drawn());
                }
            }
            for (var spectator : SpectatorHandVisibility.values()) {
                var view = game.spectatorView(spectator);
                boolean visible = spectator == SpectatorHandVisibility.ALL
                    || spectator == SpectatorHandVisibility.FOLLOW_PLAYERS && mode == PlayerHandVisibility.ALL;
                assertTrue(view.actions().isEmpty());
                for (var hand : view.seats())
                    assertTrue(hand.hand().stream().allMatch(tile -> visible ? tile >= 0 : tile == Tile.HIDDEN));
            }
            game.exposed[2] = true;
            assertTrue(game.spectatorView(SpectatorHandVisibility.HIDDEN).seats().get(2).hand().stream().allMatch(tile -> tile >= 0));
        }
        Game open = game(2, RuleSet.MAHJONG_SOUL_3, true);
        assertTrue(open.spectatorView(SpectatorHandVisibility.HIDDEN).seats().stream()
            .flatMap(seat -> seat.hand().stream()).allMatch(tile -> tile >= 0));
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
