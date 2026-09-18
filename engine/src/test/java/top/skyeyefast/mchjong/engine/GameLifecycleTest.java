package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class GameLifecycleTest {
    private static final Gson JSON = new Gson();
    private static final List<Action.Type> PRIORITY = List.of(Action.Type.TSUMO, Action.Type.RON, Action.Type.RIICHI,
        Action.Type.NUKI, Action.Type.CLOSED_KAN, Action.Type.ADDED_KAN, Action.Type.NEXT);

    static Game started(RuleSet rules, long seed) {
        Game game = new Game(new UUID(seed, seed + 1), rules, seed);
        for (int seat = 0; seat < rules.players(); seat++) {
            UUID id = new UUID(1, seat + 1);
            assertTrue(game.join(id, "Player " + seat, seat));
        }
        startPositioned(game);
        assertEquals(Game.Phase.TURN, game.phase());
        game.validate();
        return game;
    }

    /** Match-rule fixtures start with an assigned roster; RoomSeatingTest exercises the lottery. */
    static void startPositioned(Game game) {
        UUID host = game.hostId;
        int fill = index(game.view(host), Action.Type.FILL_BOTS);
        if (fill >= 0) assertTrue(game.act(host, game.decision, fill));
        game.seating.positioned(game.rules.players());
        for (int seat = 0; seat < game.rules.players(); seat++) {
            var player = game.players[seat];
            assertTrue(game.join(player.id, player.name, seat));
            if (!player.bot) {
                var view = game.view(player.id);
                assertTrue(game.act(player.id, view.decision(), index(view, Action.Type.READY)));
            }
        }
    }

    static int index(TableView view, Action.Type type) {
        for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == type) return i;
        return -1;
    }

    private static int choose(TableView view) {
        for (Action.Type type : PRIORITY) {
            int index = index(view, type);
            if (index >= 0) return index;
        }
        TableView.Seat player = view.seats().get(view.viewerSeat());
        if (view.phase() == Game.Phase.TURN) {
            Set<Integer> best = HandAnalyzer.bestDiscardKinds(player.hand(), player.melds());
            for (int i = view.actions().size() - 1; i >= 0; i--) {
                Action action = view.actions().get(i);
                if (action.type() == Action.Type.DISCARD && best.contains(Tile.kind(action.tiles().getFirst()))) return i;
            }
            int discard = index(view, Action.Type.DISCARD);
            if (discard >= 0) return discard;
        }
        int pass = index(view, Action.Type.PASS);
        if (pass >= 0) return pass;
        throw new AssertionError("No action for phase " + view.phase() + ": " + view.actions());
    }

    static void assertPrivateViews(Game game) {
        TableView spectator = game.view(null);
        assertEquals(-1, spectator.viewerSeat());
        assertTrue(spectator.actions().isEmpty());
        if (game.phase() != Game.Phase.TURN && game.phase() != Game.Phase.REACTION) return;
        for (int seat = 0; seat < game.rules().players(); seat++) {
            assertTrue(spectator.seats().get(seat).hand().stream().allMatch(t -> t == Tile.HIDDEN));
            TableView own = game.view(new UUID(1, seat + 1));
            assertTrue(own.seats().get(seat).hand().stream().allMatch(t -> t >= 0));
            for (int other = 0; other < game.rules().players(); other++) if (other != seat)
                assertTrue(own.seats().get(other).hand().stream().allMatch(t -> t == Tile.HIDDEN));
        }
        String publicJson = JSON.toJson(spectator);
        assertFalse(publicJson.contains("\"seed\""));
        assertFalse(publicJson.contains("\"options\""));
        assertFalse(publicJson.contains("\"replacementIndices\""));
        assertFalse(publicJson.contains("\"initialHands\""));
        assertFalse(publicJson.contains("\"recorder\""));
    }

    @ParameterizedTest @EnumSource(RuleSet.class) @Timeout(120)
    void completeHanchanAndReloadsPreserveTilesPointsAndPrivacy(RuleSet rules) {
        Game game = started(rules, 1234567 + rules.ordinal());
        for (int step = 0; step < 20000; step++) {
            game.validate();
            assertPrivateViews(game);
            if (game.phase() == Game.Phase.MATCH_END) {
                TableView result = game.view(null);
                assertEquals(rules.players(), result.finalScores().size());
                assertEquals(rules.players(), result.finalRanks().size());
                assertTrue(result.finalRanks().stream().allMatch(rank -> rank >= 1 && rank <= rules.players()));
                assertEquals(-game.riichiSticks, result.finalScores().stream().mapToDouble(Double::doubleValue).sum(), 0.00001);
                assertTrue(game.handNumber >= 1);
                assertTrue(game.replay.complete());
                assertEquals(game.handNumber, game.replay.hands().size());
                ReplayMatch restored = JSON.fromJson(JSON.toJson(game.replay), ReplayMatch.class);
                assertEquals(game.replay, restored);
                assertEquals(TenhouReplay.export(game.replay), TenhouReplay.export(restored));
                for (int hand = 0; hand < restored.hands().size(); hand++) {
                    ReplayHand recorded = restored.hands().get(hand);
                    var replayed = ReplayPlayback.at(restored, hand, recorded.events().size());
                    for (int seat = 0; seat < rules.players(); seat++) {
                        var expected = recorded.finalSeats().get(seat);
                        var actual = replayed.seats().get(seat);
                        assertEquals(new TreeSet<>(expected.hand()), new TreeSet<>(actual.hand()), "Replay concealed hand");
                        assertEquals(expected.river(), actual.river(), "Replay discards");
                        assertEquals(expected.melds(), actual.melds(), "Replay melds");
                        assertEquals(expected.norths(), actual.norths(), "Replay extracted norths");
                        assertEquals(expected.points() - recorded.deltas().get(seat), actual.points(), "Replay points before settlement");
                    }
                    assertEquals(recorded.finalSeats(), ReplayPlayback.at(restored, hand, Integer.MAX_VALUE).seats());
                }
                return;
            }
            if (step % 71 == 0) {
                String before = JSON.toJson(game.view(null));
                game = JSON.fromJson(JSON.toJson(game), Game.class);
                game.validate();
                assertEquals(before, JSON.toJson(game.view(null)));
            }
            boolean acted = false;
            for (int seat = 0; seat < rules.players(); seat++) {
                UUID id = new UUID(1, seat + 1);
                TableView view = game.view(id);
                if (view.actions().isEmpty()) continue;
                assertTrue(game.act(id, view.decision(), choose(view)), "Legal action rejected");
                acted = true;
                break;
            }
            assertTrue(acted, "Deadlock at " + game.phase() + " round=" + game.round);
        }
        fail("Match did not finish within 20,000 actions: " + rules);
    }

    @Test void invalidRequestsAndStaleDecisionsCannotMutateTheGame() {
        Game game = started(RuleSet.TENHOU_4, 19);
        UUID id = game.players[game.turn].id;
        TableView view = game.view(id);
        String before = JSON.toJson(game);
        assertFalse(game.act(UUID.randomUUID(), view.decision(), 0));
        assertFalse(game.act(id, view.decision() - 1, 0));
        assertFalse(game.act(id, view.decision(), -1));
        assertFalse(game.act(id, view.decision(), Integer.MAX_VALUE));
        assertFalse(game.act(game.players[game.next(game.turn)].id, view.decision(), 0));
        assertEquals(before, JSON.toJson(game));
        assertTrue(game.act(id, view.decision(), index(view, Action.Type.DISCARD)));
        String after = JSON.toJson(game);
        assertFalse(game.act(id, view.decision(), 0));
        assertEquals(after, JSON.toJson(game));
    }

    @Test void emptySeatReconnectionDoesNotTransferAnotherPlayersHand() {
        Game game = started(RuleSet.MAHJONG_SOUL_3, 45);
        UUID owner = game.players[0].id;
        var before = game.view(owner).seats().getFirst().hand();
        game.leave(owner);
        assertTrue(game.join(owner, "Reconnected", 0));
        assertFalse(game.join(UUID.randomUUID(), "Intruder", 0));
        assertFalse(game.join(owner, "Other seat", 1));
        assertEquals(before, game.view(owner).seats().getFirst().hand());
    }

    @Test void rankedReturnPointsAreNotTheSuddenDeathTarget() {
        assertEquals(25000, RuleSet.MAHJONG_SOUL_4.returnPoints());
        assertEquals(35000, RuleSet.MAHJONG_SOUL_3.returnPoints());
        assertEquals(30000, RuleSet.MAHJONG_SOUL_4.targetPoints());
        assertEquals(40000, RuleSet.MAHJONG_SOUL_3.targetPoints());
        assertEquals(30000, RuleSet.TENHOU_4.returnPoints());
        assertEquals(40000, RuleSet.TENHOU_3.returnPoints());
        assertEquals(3, RuleSet.MAHJONG_SOUL_3.minRiichiWall());
    }

    @Test void matchLengthAndExtensionRespectPlayerCountAndDealerRepeats() {
        for (var preset : List.of(RuleSet.TENHOU_4, RuleSet.TENHOU_3, RuleSet.MAHJONG_SOUL_4, RuleSet.MAHJONG_SOUL_3)) {
            for (int length : List.of(1, 2)) {
                var rules = preset.config().with(RuleOption.MATCH_LENGTH, length);
                int last = rules.scheduledRounds() - 1;
                assertEquals(Game.Phase.HAND_END, endFixture(rules, last - 1, 0, true, false).phase());
                assertEquals(Game.Phase.MATCH_END, endFixture(rules, last, 0, true, false).phase());
                assertEquals(Game.Phase.HAND_END, endFixture(rules, last, 0, false, false).phase());
                assertEquals(Game.Phase.MATCH_END, endFixture(rules.with(RuleOption.EXTENSION, 0), last, 0, false, false).phase());
                assertEquals(Game.Phase.MATCH_END, endFixture(rules, last + preset.players(), 0, false, false).phase());
                assertEquals(Game.Phase.HAND_END, endFixture(rules, last + 1, 0, true, true).phase(),
                    "A non-leading dealer still repeats in an extension");
            }
        }
    }

    @Test void bankruptcyDefaultsAndCustomSwitchUseStrictlyNegativePoints() {
        for (var preset : RuleSet.values()) {
            assertEquals(preset.mahjongSoul() || preset.tenhou(), preset.config().bankruptcy(), preset.name());
            assertEquals(Game.Phase.HAND_END, endFixture(preset.config(), 0, 0, false, false).phase());
            for (int enabled : List.of(0, 1)) {
                var rules = preset.config().with(RuleOption.BANKRUPTCY, enabled);
                assertEquals(enabled == 1 ? Game.Phase.MATCH_END : Game.Phase.HAND_END,
                    endFixture(rules, 0, -100, false, false).phase());
            }
        }
    }

    private static Game endFixture(RuleConfig rules, int round, int firstPoints, boolean target, boolean repeats) {
        var game = new Game(new UUID(0, 1), rules.preset(), 1);
        game.rules = rules;
        game.round = round;
        game.dealer = 0;
        for (var player : game.players) player.points = rules.targetPoints() - 10000;
        game.players[0].points = firstPoints;
        if (target) game.players[1].points = rules.targetPoints() + 10000;
        if (repeats) game.players[0].hand.addAll(TestHands.tiles("123456m456p22s78s"));
        Settlement.exhaustive(game);
        return game;
    }

    @Test void leagueAFloatingBonusesAndCompetitiveTieSettlement() {
        int[][] scores = {{30000,30000,30000,30000}, {60000,25000,20000,15000},
            {40000,35000,25000,20000}, {40000,30000,30000,20000}, {29900,29900,29900,29300}};
        double[][] expected = {{0,0,0,0}, {42,-6,-13,-23}, {18,9,-9,-18}, {18,2,2,-22}, {-.1,-.1,-.1,-.7}};
        for (int i = 0; i < scores.length; i++) {
            Game game = finish(RuleSet.JPML_A.config(), scores[i], i == 4 ? 1 : 0);
            assertArrayEquals(expected[i], game.finalScores.stream().mapToDouble(Double::doubleValue).toArray(), .00001);
            assertEquals(i == 4 ? 1 : 0, game.riichiSticks);
        }
        Game league = finish(RuleSet.M_LEAGUE.config(), new int[]{30000,30000,30000,8000}, 2);
        assertEquals(List.of(30800,30600,30600,8000), Arrays.stream(league.players).map(p -> p.points).toList());
        assertArrayEquals(new double[]{17.5,17.3,17.2,-52.0}, league.finalScores.stream().mapToDouble(Double::doubleValue).toArray(), .00001);
        Game wrc = finish(RuleSet.WRC.config(), new int[]{35000,35000,35000,14000}, 1);
        assertEquals(1, wrc.riichiSticks);
        assertEquals(List.of(10.0,10.0,10.0,-31.0), wrc.finalScores);
        var custom = RuleSet.WRC.config().with(RuleOption.STARTING_POINTS, 25100).with(RuleOption.RETURN_POINTS, 30200)
            .with(RuleOption.UMA_1, 15300).with(RuleOption.UMA_4, -15300);
        Game fractional = finish(custom, new int[]{40100,30100,20100,10100}, 0);
        assertArrayEquals(new double[]{45.6,4.9,-15.1,-35.4},
            fractional.finalScores.stream().mapToDouble(Double::doubleValue).toArray(), .00001);
    }

    private static Game finish(RuleConfig rules, int[] scores, int deposits) {
        Game game = new Game(UUID.randomUUID(), rules.preset(), 1);
        game.rules = rules;
        game.round = 7;
        game.riichiSticks = deposits;
        for (int seat = 0; seat < 4; seat++) game.players[seat].points = scores[seat];
        Settlement.exhaustive(game);
        assertEquals(Game.Phase.MATCH_END, game.phase());
        return game;
    }
}
