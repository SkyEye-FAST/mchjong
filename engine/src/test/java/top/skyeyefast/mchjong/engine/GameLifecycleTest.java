package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class GameLifecycleTest {
    private static final Gson JSON = new Gson();
    private static final List<RiichiAction.Type> PRIORITY = List.of(RiichiAction.Type.TSUMO, RiichiAction.Type.RON, RiichiAction.Type.RIICHI,
        RiichiAction.Type.NUKI, RiichiAction.Type.CLOSED_KAN, RiichiAction.Type.ADDED_KAN, RiichiAction.Type.NEXT);

    static RiichiGame started(RiichiPreset rules, long seed) {
        RiichiSession session = new RiichiSession(new UUID(seed, seed + 1), rules, seed);
        for (int seat = 0; seat < rules.players(); seat++) {
            UUID id = new UUID(1, seat + 1);
            assertTrue(session.join(id, "Player " + seat, seat));
        }
        startPositioned(session);
        RiichiGame game = session.game();
        assertEquals(RiichiGame.Phase.TURN, game.phase());
        game.validate();
        return game;
    }

    /** Simulate the server reobserving physical mounts after a private save is restored. */
    static RiichiGame reloadMounted(RiichiGame game) {
        return reloadMounted(game.session).game();
    }

    static RiichiSession reloadMounted(RiichiSession session) {
        var mounts = new HashMap<UUID, Integer>();
        var connected = new HashSet<UUID>();
        for (int seat = 0; seat < session.capacity(); seat++) {
            var member = session.participants[seat];
            if (member.id == null || member.bot && !member.entityBot) continue;
            if (!member.entityBot && member.presence != PlayerPresence.DISCONNECTED) connected.add(member.id);
            if (member.presence == PlayerPresence.SEATED) mounts.put(member.id, seat);
        }
        var restored = (RiichiSession) TableSessionCodec.restore(TableSessionCodec.save(session));
        restored.synchronizeSeats(mounts, connected);
        return restored;
    }

    @Test void riichiSaveContainsOnlyDetachedStateAndRejectsRuntimeFields() {
        var session = started(RiichiPreset.TENHOU_4, 206).session;
        String encoded = TableSessionCodec.save(session);
        var envelope = JsonParser.parseString(encoded).getAsJsonObject();
        var state = envelope.getAsJsonObject("state");
        assertTrue(state.has("room"));
        assertTrue(state.has("game"));
        assertFalse(state.has("worldPolicy"));
        assertFalse(state.has("lobbyTicks"));
        assertFalse(state.getAsJsonObject("game").has("session"));
        var restored = (RiichiSession) TableSessionCodec.restore(encoded);
        assertEquals(session.rules(), restored.rules());
        assertEquals(session.game().phase(), restored.game().phase());
        assertEquals(session.game().view(null).wall(), restored.game().view(null).wall());
        assertNotEquals(session.incarnation(), restored.incarnation());

        state.addProperty("lobbyTicks", 0);
        assertThrows(IllegalArgumentException.class, () -> TableSessionCodec.restore(envelope.toString()));
        state.remove("lobbyTicks");
        state.getAsJsonObject("game").addProperty("session", "runtime");
        assertThrows(IllegalArgumentException.class, () -> TableSessionCodec.restore(envelope.toString()));
    }

    @Test void emptyActiveTablePausesAndLastDismounterChoosesItsFate() {
        var game = started(RiichiPreset.TENHOU_4, 201);
        UUID last = game.players[3].member.id;
        for (int seat = 0; seat < 3; seat++) game.session.unseat(game.players[seat].member.id);
        assertFalse(game.session.leaveDecision(game.players[0].member.id));
        game.session.unseat(last);
        assertTrue(game.session.leaveDecision(last));
        assertFalse(game.session.resolveLeave(game.players[0].member.id, false));
        int age = game.age;
        long decision = game.session.decision();
        int[] move = game.moveTicks.clone(), reserve = game.reserveTicks.clone();
        for (int tick = 0; tick < TableSession.AWAY_GRACE_TICKS + 30; tick++) game.tick();
        assertEquals(age, game.age);
        assertEquals(decision, game.session.decision());
        assertArrayEquals(move, game.moveTicks);
        assertArrayEquals(reserve, game.reserveTicks);
        assertTrue(game.session.resolveLeave(last, true));
        assertFalse(game.session.leaveDecision(last));
        game.session.synchronizeSeats(Map.of(last, 3), Set.of(last));
        game.tick();
        assertEquals(age + 1, game.age);
        game.validate();

        game.session.unseat(last);
        assertTrue(game.session.resolveLeave(last, false));
        assertEquals(TableSession.Lifecycle.LOBBY, game.session.lifecycle());
        assertEquals(-1, game.session.seatOf(last));
        game.session.validateRoom();
    }

    @Test void lostConnectionsKeepTheEmptyMatchPausedWithoutALeaveDecision() {
        var game = started(RiichiPreset.TENHOU_3, 202);
        int age = game.age;
        game.session.synchronizeSeats(Map.of(), Set.of());
        for (int tick = 0; tick < 40; tick++) game.tick();
        assertEquals(age, game.age);
        assertEquals(RiichiGame.Phase.TURN, game.phase());
        for (int seat = 0; seat < 3; seat++) assertFalse(game.session.leaveDecision(game.players[seat].member.id));
        game.validate();
        game = ((RiichiSession) TableSessionCodec.restore(TableSessionCodec.save(game.session))).game();
        game.session.synchronizeSeats(Map.of(), Set.of());
        game.tick();
        assertEquals(age, game.age);

        var displaced = started(RiichiPreset.TENHOU_3, 203);
        for (int seat = 0; seat < 3; seat++) displaced.session.unseat(displaced.players[seat].member.id, false);
        assertFalse(displaced.session.leaveDecision(displaced.players[2].member.id));
        int displacedAge = displaced.age;
        displaced.tick();
        assertEquals(displacedAge, displaced.age);
    }

    /** Match-rule fixtures start with an assigned roster; RoomSeatingTest exercises the lottery. */
    static void startPositioned(RiichiSession game) {
        UUID host = game.hostId;
        int fill = game.roomView(host).actions().indexOf(new RoomAction(RoomAction.Type.FILL_BOTS));
        if (fill >= 0) assertTrue(game.actRoom(host, game.tableId(), game.incarnation(), game.decision(), fill));
        game.seating.positioned(game.rules.players());
        for (int seat = 0; seat < game.rules.players(); seat++) {
            var player = game.participants[seat];
            assertTrue(game.join(player.id, player.name, seat));
            if (!player.bot) {
                var room = game.roomView(player.id);
                int ready = room.actions().indexOf(new RoomAction(RoomAction.Type.READY));
                assertTrue(game.actRoom(player.id, room.tableId(), room.incarnation(), room.decision(), ready));
            }
        }
    }

    static int index(RiichiView view, RiichiAction.Type type) {
        for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == type) return i;
        return -1;
    }

    private static int choose(RiichiView view) {
        for (RiichiAction.Type type : PRIORITY) {
            int index = index(view, type);
            if (index >= 0) return index;
        }
        RiichiView.Seat player = view.seats().get(view.viewerSeat());
        if (view.phase() == RiichiView.Phase.TURN) {
            Set<Integer> best = RiichiHandAnalyzer.bestDiscardKinds(player.hand(), player.melds());
            for (int i = view.actions().size() - 1; i >= 0; i--) {
                RiichiAction action = view.actions().get(i);
                if (action.type() == RiichiAction.Type.DISCARD && best.contains(Tile.kind(action.tiles().getFirst()))) return i;
            }
            int discard = index(view, RiichiAction.Type.DISCARD);
            if (discard >= 0) return discard;
        }
        int pass = index(view, RiichiAction.Type.PASS);
        if (pass >= 0) return pass;
        throw new AssertionError("No action for phase " + view.phase() + ": " + view.actions());
    }

    static void assertPrivateViews(RiichiGame game) {
        RiichiView spectator = game.view(null);
        assertEquals(-1, spectator.viewerSeat());
        assertTrue(spectator.actions().isEmpty());
        if (game.phase() != RiichiGame.Phase.TURN && game.phase() != RiichiGame.Phase.REACTION) return;
        for (int seat = 0; seat < game.rules().players(); seat++) {
            assertTrue(spectator.seats().get(seat).hand().stream().allMatch(t -> t == Tile.HIDDEN));
            RiichiView own = game.view(new UUID(1, seat + 1));
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

    @Test void readoutAcknowledgementsRetainAReadingTailAndCannotChangeTheScore() {
        RiichiGame game = started(RiichiPreset.TENHOU_4, 205);
        game.players[1].points = -100;
        game.players[0].points += 25100;
        RiichiSettlement.abort(game, "nine_terminals");
        game.wins = List.of(new RiichiView.Win(0, 1, 4,
            new HandScore(5, 30, 0, 12000, 0, 0, List.of("Richi"), 4)));
        int maximum = ScoreAnnouncements.maximumTicks(game.wins);
        assertEquals(maximum + RiichiGame.SETTLEMENT_TICKS, game.view(null).settlementTicks());
        var deltas = List.copyOf(game.deltas);
        for (int seat = 0; seat < 4; seat++) {
            UUID id = game.players[seat].member.id;
            var view = game.view(id);
            int done = index(view, RiichiAction.Type.SETTLEMENT_DONE);
            assertFalse(game.act(id, view.decision() - 1, done));
            assertTrue(game.act(id, view.decision(), done));
            game.tick();
            if (seat < 3) assertTrue(game.view(null).settlementTicks() > RiichiGame.SETTLEMENT_TICKS * 2);
        }
        assertEquals(RiichiGame.SETTLEMENT_TICKS * 2, game.view(null).settlementTicks());
        game = reloadMounted(game);
        for (int i = 0; i < RiichiGame.SETTLEMENT_TICKS; i++) game.tick();
        assertEquals(RiichiGame.Phase.MATCH_END, game.phase());
        assertEquals(RiichiGame.SETTLEMENT_TICKS, game.view(null).settlementTicks());
        assertEquals(deltas, game.deltas);
        assertFalse(game.view(game.players[0].member.id).actions().stream().anyMatch(a -> a.type() == RiichiAction.Type.SETTLEMENT_DONE));
        assertEquals(RiichiGame.SETTLEMENT_TICKS, ScoreAnnouncements.maximumTicks(List.of()));
    }

    @Test void settlementWaitCanBeSkippedWithServerIssuedActions() {
        RiichiGame game = started(RiichiPreset.TENHOU_4, 204);
        UUID player = game.players[0].member.id;
        RiichiSettlement.abort(game, "nine_terminals");
        game.wins = List.of(new RiichiView.Win(0, 1, 4,
            new HandScore(5, 30, 0, 12000, 0, 0, List.of("Richi"), 4)));
        assertEquals(RiichiGame.Phase.HAND_END, game.phase());
        RiichiView hand = game.view(player);
        int skip = index(hand, RiichiAction.Type.SKIP_SETTLEMENT);
        int done = index(hand, RiichiAction.Type.SETTLEMENT_DONE);
        assertTrue(skip >= 0);
        assertTrue(done > skip);
        assertFalse(game.act(player, hand.decision() - 1, skip));
        assertTrue(game.act(player, hand.decision(), skip));
        assertEquals(RiichiGame.Phase.HAND_END, game.phase());
        assertEquals(skip, index(game.view(player), RiichiAction.Type.SKIP_SETTLEMENT));
        assertEquals(done, index(game.view(player), RiichiAction.Type.SETTLEMENT_DONE));
        assertEquals(1, game.view(null).settlementSkippedSeats());
        assertFalse(game.act(player, hand.decision(), skip));
        game = reloadMounted(game);
        assertEquals(1, game.view(null).settlementSkippedSeats());
        for (int seat = 1; seat < 4; seat++) {
            UUID id = game.players[seat].member.id;
            RiichiView view = game.view(id);
            assertTrue(game.act(id, view.decision(), index(view, RiichiAction.Type.SKIP_SETTLEMENT)));
            if (seat < 3) assertEquals(RiichiGame.Phase.HAND_END, game.phase());
        }
        assertEquals(RiichiGame.Phase.TURN, game.phase());
        assertFalse(game.act(player, hand.decision(), skip));

        game.players[1].points = -100;
        RiichiSettlement.abort(game, "nine_terminals");
        assertEquals(RiichiGame.Phase.MATCH_END, game.phase());
        RiichiView results = game.view(player);
        assertEquals(RiichiGame.SETTLEMENT_TICKS * 2, game.view(null).settlementTicks());
        for (int seat = 0; seat < 4; seat++) {
            UUID id = game.players[seat].member.id;
            RiichiView view = game.view(id);
            assertTrue(game.act(id, view.decision(), index(view, RiichiAction.Type.SKIP_SETTLEMENT)));
            if (seat < 3) assertEquals(RiichiGame.SETTLEMENT_TICKS * 2, game.view(null).settlementTicks());
        }
        assertEquals(RiichiGame.Phase.MATCH_END, game.phase());
        assertEquals(RiichiGame.SETTLEMENT_TICKS, game.view(null).settlementTicks());
        assertFalse(game.act(player, results.decision(), index(results, RiichiAction.Type.SKIP_SETTLEMENT)));
        for (int seat = 0; seat < 4; seat++) {
            UUID id = game.players[seat].member.id;
            RiichiView view = game.view(id);
            assertTrue(game.act(id, view.decision(), index(view, RiichiAction.Type.SKIP_SETTLEMENT)));
            if (seat < 3) assertEquals(RiichiGame.Phase.MATCH_END, game.phase());
        }
        assertEquals(TableSession.Lifecycle.LOBBY, game.session.lifecycle());

        RiichiSession practiceRoom = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 205);
        UUID solo = UUID.randomUUID();
        assertTrue(practiceRoom.join(solo, "Solo", 0));
        startPositioned(practiceRoom);
        RiichiGame practice = practiceRoom.game();
        RiichiSettlement.abort(practice, "nine_terminals");
        RiichiView practiceHand = practice.view(solo);
        assertTrue(practice.act(solo, practiceHand.decision(), index(practiceHand, RiichiAction.Type.SKIP_SETTLEMENT)));
        assertEquals(RiichiGame.Phase.TURN, practice.phase());
    }

    // Match progression has two player-count paths; preset differences have focused rule tests.
    @ParameterizedTest @EnumSource(value = RiichiPreset.class, names = {"TENHOU_4", "MAHJONG_SOUL_3"}) @Timeout(30)
    void completeHanchanAndReloadsPreserveTilesPointsAndPrivacy(RiichiPreset rules) {
        RiichiGame game = started(rules, 1234567 + rules.ordinal());
        Set<RiichiGame.Phase> reloadedPhases = EnumSet.noneOf(RiichiGame.Phase.class);
        var chosenActions = new ArrayList<RiichiAction>();
        for (int step = 0; step < 20000; step++) {
            game.validate();
            if (game.phase() == RiichiGame.Phase.MATCH_END) {
                RiichiView result = game.view(null);
                assertEquals(rules.players(), result.finalScores().size());
                assertEquals(rules.players(), result.finalRanks().size());
                assertTrue(result.finalRanks().stream().allMatch(rank -> rank >= 1 && rank <= rules.players()));
                assertEquals(-game.riichiSticks, result.finalScores().stream().mapToDouble(Double::doubleValue).sum(), 0.00001);
                assertTrue(game.handNumber >= 1);
                assertTrue(game.replay.complete());
                assertEquals(game.handNumber, game.replay.handCount());
                ReplayMatch restored = JSON.fromJson(JSON.toJson(game.replay), ReplayMatch.class);
                assertEquals(game.replay, restored);
                assertEquals(TenhouReplay.export(game.replay), TenhouReplay.export(restored));
                assertFalse(JSON.toJson(restored).contains("\"seed\""), "Sealed replays must not retain RNG seeds");
                var replayChoices = restored.riichi().hands().stream().flatMap(recorded -> recorded.decisions().stream())
                    .map(decision -> decision.options().get(decision.selected())).toList();
                assertEquals(chosenActions, replayChoices, "Replay decisions must preserve the server-issued choice");
                for (int hand = 0; hand < restored.handCount(); hand++) {
                    ReplayHand recorded = restored.riichi().hands().get(hand);
                    assertTrue(Tile.validSet(recorded.wall().tiles()), "Replay wall must retain one complete physical set");
                    assertTrue(recorded.initialHands().stream().flatMap(List::stream).allMatch(recorded.wall().tiles()::contains));
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
            if (reloadedPhases.add(game.phase())) {
                var before = game.view(null);
                game = reloadMounted(game);
                assertEquals(before.seats(), game.view(null).seats());
                assertEquals(before.wall(), game.view(null).wall());
                assertPrivateViews(game);
            }
            if (game.phase() == RiichiGame.Phase.HAND_END) {
                int remaining = game.view(null).settlementTicks();
                for (int tick = 0; tick < remaining; tick++) game.tick();
                continue;
            }
            boolean acted = false;
            for (int seat = 0; seat < rules.players(); seat++) {
                UUID id = new UUID(1, seat + 1);
                RiichiView view = game.view(id);
                if (view.actions().isEmpty()) continue;
                int choice = choose(view);
                if (game.phase() == RiichiGame.Phase.TURN || game.phase() == RiichiGame.Phase.REACTION) chosenActions.add(view.actions().get(choice));
                assertTrue(game.act(id, view.decision(), choice), "Legal action rejected");
                acted = true;
                break;
            }
            assertTrue(acted, "Deadlock at " + game.phase() + " round=" + game.round);
        }
        fail("Match did not finish within 20,000 actions: " + rules);
    }

    @Test void invalidRequestsAndStaleDecisionsCannotMutateTheGame() {
        RiichiGame game = started(RiichiPreset.TENHOU_4, 19);
        UUID id = game.players[game.turn].member.id;
        RiichiView view = game.view(id);
        String before = TableSessionCodec.save(game.session);
        assertFalse(game.act(UUID.randomUUID(), view.decision(), 0));
        assertFalse(game.act(id, view.decision() - 1, 0));
        assertFalse(game.act(id, view.decision(), -1));
        assertFalse(game.act(id, view.decision(), Integer.MAX_VALUE));
        assertFalse(game.act(game.players[game.next(game.turn)].member.id, view.decision(), 0));
        assertEquals(before, TableSessionCodec.save(game.session));
        assertTrue(game.act(id, view.decision(), index(view, RiichiAction.Type.DISCARD)));
        String after = TableSessionCodec.save(game.session);
        assertFalse(game.act(id, view.decision(), 0));
        assertEquals(after, TableSessionCodec.save(game.session));
    }

    @Test void emptySeatReconnectionDoesNotTransferAnotherPlayersHand() {
        RiichiGame game = started(RiichiPreset.MAHJONG_SOUL_3, 45);
        UUID owner = game.players[0].member.id;
        var before = game.view(owner).seats().getFirst().hand();
        game.session.unseat(owner);
        assertTrue(game.session.join(owner, "Reconnected", 0));
        assertFalse(game.session.join(UUID.randomUUID(), "Intruder", 0));
        assertFalse(game.session.join(owner, "Other seat", 1));
        assertEquals(before, game.view(owner).seats().getFirst().hand());
    }

    @Test void rankedReturnPointsAreNotTheSuddenDeathTarget() {
        assertEquals(25000, RiichiPreset.MAHJONG_SOUL_4.returnPoints());
        assertEquals(35000, RiichiPreset.MAHJONG_SOUL_3.returnPoints());
        assertEquals(30000, RiichiPreset.MAHJONG_SOUL_4.targetPoints());
        assertEquals(40000, RiichiPreset.MAHJONG_SOUL_3.targetPoints());
        assertEquals(30000, RiichiPreset.TENHOU_4.returnPoints());
        assertEquals(40000, RiichiPreset.TENHOU_3.returnPoints());
        assertEquals(3, RiichiPreset.MAHJONG_SOUL_3.minRiichiWall());
    }

    @Test void matchLengthAndExtensionRespectPlayerCountAndDealerRepeats() {
        for (var preset : List.of(RiichiPreset.TENHOU_4, RiichiPreset.TENHOU_3, RiichiPreset.MAHJONG_SOUL_4, RiichiPreset.MAHJONG_SOUL_3)) {
            for (int length : List.of(1, 2)) {
                var rules = preset.config().with(RiichiRuleOption.MATCH_LENGTH, length);
                int last = rules.scheduledRounds() - 1;
                assertEquals(RiichiGame.Phase.HAND_END, endFixture(rules, last - 1, 0, true, false).phase());
                assertEquals(RiichiGame.Phase.MATCH_END, endFixture(rules, last, 0, true, false).phase());
                assertEquals(RiichiGame.Phase.HAND_END, endFixture(rules, last, 0, false, false).phase());
                assertEquals(RiichiGame.Phase.MATCH_END, endFixture(rules.with(RiichiRuleOption.EXTENSION, 0), last, 0, false, false).phase());
                assertEquals(RiichiGame.Phase.MATCH_END, endFixture(rules, last + preset.players(), 0, false, false).phase());
                assertEquals(RiichiGame.Phase.HAND_END, endFixture(rules, last + 1, 0, true, true).phase(),
                    "A non-leading dealer still repeats in an extension");
            }
        }
    }

    @Test void bankruptcyDefaultsAndCustomSwitchUseStrictlyNegativePoints() {
        for (var preset : RiichiPreset.values()) {
            assertEquals(preset.mahjongSoul() || preset.tenhou(), preset.config().bankruptcy(), preset.name());
            assertEquals(RiichiGame.Phase.HAND_END, endFixture(preset.config(), 0, 0, false, false).phase());
            for (int enabled : List.of(0, 1)) {
                var rules = preset.config().with(RiichiRuleOption.BANKRUPTCY, enabled);
                assertEquals(enabled == 1 ? RiichiGame.Phase.MATCH_END : RiichiGame.Phase.HAND_END,
                    endFixture(rules, 0, -100, false, false).phase());
            }
        }
    }

    private static RiichiGame endFixture(RiichiRules rules, int round, int firstPoints, boolean target, boolean repeats) {
        var session = new RiichiSession(new UUID(0, 1), rules, 1);
        for (int seat = 0; seat < rules.players(); seat++) session.join(new UUID(40, seat), "Player " + seat, seat);
        session.configureWorld(new WorldPolicy(true, false, true, 5_000, false, true, true, true, null));
        session.startMatch();
        var game = session.game();
        for (var player : game.players) player.resetHand();
        game.round = round;
        game.dealer = 0;
        for (var player : game.players) player.points = rules.targetPoints() - 10000;
        game.players[0].points = firstPoints;
        if (target) game.players[1].points = rules.targetPoints() + 10000;
        if (repeats) game.players[0].hand.addAll(TestHands.tiles("123456m456p22s78s"));
        RiichiSettlement.exhaustive(game);
        return game;
    }

    @Test void jpmlAFloatingBonusesAndCompetitiveTieSettlement() {
        int[][] scores = {{30000,30000,30000,30000}, {60000,25000,20000,15000},
            {40000,35000,25000,20000}, {40000,30000,30000,20000}, {29900,29900,29900,29300}};
        double[][] expected = {{0,0,0,0}, {42,-6,-13,-23}, {18,9,-9,-18}, {18,2,2,-22}, {-.1,-.1,-.1,-.7}};
        for (int i = 0; i < scores.length; i++) {
            RiichiGame game = finish(RiichiPreset.JPML_A.config(), scores[i], i == 4 ? 1 : 0);
            assertArrayEquals(expected[i], game.finalScores.stream().mapToDouble(Double::doubleValue).toArray(), .00001);
            assertEquals(i == 4 ? 1 : 0, game.riichiSticks);
        }
        RiichiGame league = finish(RiichiPreset.M_LEAGUE.config(), new int[]{30000,30000,30000,8000}, 2);
        assertEquals(List.of(30800,30600,30600,8000), Arrays.stream(league.players).map(p -> p.points).toList());
        assertArrayEquals(new double[]{17.5,17.3,17.2,-52.0}, league.finalScores.stream().mapToDouble(Double::doubleValue).toArray(), .00001);
        RiichiGame wrc = finish(RiichiPreset.WRC.config(), new int[]{35000,35000,35000,14000}, 1);
        assertEquals(1, wrc.riichiSticks);
        assertEquals(List.of(10.0,10.0,10.0,-31.0), wrc.finalScores);
        var custom = RiichiPreset.WRC.config().with(RiichiRuleOption.STARTING_POINTS, 25100).with(RiichiRuleOption.RETURN_POINTS, 30200)
            .with(RiichiRuleOption.UMA_1, 15300).with(RiichiRuleOption.UMA_4, -15300);
        RiichiGame fractional = finish(custom, new int[]{40100,30100,20100,10100}, 0);
        assertArrayEquals(new double[]{45.6,4.9,-15.1,-35.4},
            fractional.finalScores.stream().mapToDouble(Double::doubleValue).toArray(), .00001);
        assertArrayEquals(new double[]{15.3,5,-5,-15.3},
            fractional.finalUma.stream().mapToDouble(Double::doubleValue).toArray(), .00001);
    }

    @Test void matchUmaRewardsRespectWorldExperiencePolicyAndPayOnce() {
        var rules = RiichiPreset.WRC.config();
        var session = new RiichiSession(UUID.randomUUID(), rules, 1);
        UUID[] ids = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};
        for (int seat = 0; seat < rules.players(); seat++) session.join(ids[seat], "Player " + seat, seat);
        session.configureWorld(new WorldPolicy(true, false, true, 5_000, false, true, true, true, null));
        session.startMatch();
        RiichiGame game = session.game();
        session.configureWorld(new WorldPolicy(true, true, true, 5_000, true, true, true, true, null));
        game.round = 7;
        int[] scores = {50000, 40000, 20000, 10000};
        for (int seat = 0; seat < 4; seat++) {
            game.players[seat].points = scores[seat];
        }
        game.players[2].member.bot = true;
        RiichiSettlement.exhaustive(game);
        assertEquals(List.of(15.0, 5.0, -5.0, -15.0), game.finalUma);
        assertEquals(Map.of(ids[0], 1500, ids[1], 500, ids[3], -1500), session.pendingExperience());
        RiichiSession restored = (RiichiSession) TableSessionCodec.restore(TableSessionCodec.save(session));
        assertEquals(session.pendingExperience(), restored.pendingExperience());
        restored.configureWorld(new WorldPolicy(true, true, false, 500, true, true, true, true, null));
        assertEquals(500, restored.takeExperience(ids[0]));
        assertEquals(0, restored.takeExperience(ids[3]));
        assertEquals(0, restored.takeExperience(ids[0]));

        var noDeductionsSession = new RiichiSession(UUID.randomUUID(), rules, 2);
        for (int seat = 0; seat < rules.players(); seat++) noDeductionsSession.join(ids[seat], "Player " + seat, seat);
        noDeductionsSession.configureWorld(new WorldPolicy(true, false, true, 5_000, false, true, true, true, null));
        noDeductionsSession.startMatch();
        RiichiGame noDeductions = noDeductionsSession.game();
        noDeductionsSession.configureWorld(new WorldPolicy(true, true, false, 500, true, true, true, true, null));
        noDeductions.round = 7;
        for (int seat = 0; seat < 4; seat++) {
            noDeductions.players[seat].points = scores[seat];
        }
        RiichiSettlement.exhaustive(noDeductions);
        assertEquals(Map.of(ids[0], 500, ids[1], 500), noDeductionsSession.pendingExperience());
    }

    private static RiichiGame finish(RiichiRules rules, int[] scores, int deposits) {
        var session = new RiichiSession(UUID.randomUUID(), rules, 1);
        for (int seat = 0; seat < rules.players(); seat++) session.join(new UUID(40, seat), "Player " + seat, seat);
        session.startMatch();
        RiichiGame game = session.game();
        game.round = 7;
        game.riichiSticks = deposits;
        for (int seat = 0; seat < 4; seat++) game.players[seat].points = scores[seat];
        RiichiSettlement.exhaustive(game);
        assertEquals(RiichiGame.Phase.MATCH_END, game.phase());
        return game;
    }
}
