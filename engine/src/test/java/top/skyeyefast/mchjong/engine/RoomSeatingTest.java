package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomSeatingTest {
    @Test void threeSeatRiichiCanSwitchToFourSeatMcrWithoutLosingHumans() {
        var riichi = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_3, 19);
        for (int seat = 0; seat < 3; seat++) assertTrue(riichi.join(id(seat), "Human " + seat, seat));
        var mcr = riichi.selectVariant(id(0), riichi.decision(), MahjongVariant.MCR);
        assertInstanceOf(McrSession.class, mcr);
        assertEquals(4, mcr.capacity());
        for (int seat = 0; seat < 3; seat++) assertEquals(seat, mcr.seatOf(id(seat)));
        assertFalse(mcr.occupied(3));
        var returned = mcr.selectVariant(id(0), mcr.decision(), MahjongVariant.RIICHI);
        assertInstanceOf(RiichiSession.class, returned);
        assertEquals(4, returned.capacity());
        for (int seat = 0; seat < 3; seat++) assertEquals(seat, returned.seatOf(id(seat)));
    }

    @Test void variantSwitchKeepsOneRoomAndRestoresOneMcrMatch() {
        UUID tableId = UUID.randomUUID();
        var riichi = new RiichiSession(tableId, RiichiPreset.TENHOU_4, 71);
        assertTrue(riichi.join(id(0), "Host", 0));
        assertTrue(riichi.join(id(1), "Guest", 1));
        TableSession session = riichi.selectVariant(id(0), riichi.decision(), MahjongVariant.MCR);
        assertInstanceOf(McrSession.class, session);
        assertEquals(tableId, session.tableId());
        assertEquals(0, session.seatOf(id(0)));
        assertEquals(1, session.seatOf(id(1)));
        assertNotEquals(riichi.incarnation(), session.incarnation());
        assertTrue(session.configureEquipment(false, Tile.standard144Set()));
        assertTrue(session.join(id(2), "West", 2));
        assertTrue(session.join(id(3), "North", 3));
        session.synchronizeSeats(Map.of(id(0), 0, id(1), 1, id(2), 2, id(3), 3));
        var room = session.roomView(id(0));
        int begin = room.actions().indexOf(new RoomAction(RoomAction.Type.BEGIN_SEATING));
        assertTrue(session.actRoom(id(0), tableId, room.incarnation(), room.decision(), begin));
        var mounts = new java.util.HashMap<UUID, Integer>();
        for (int seat = 0; seat < 4; seat++) mounts.put(session.participants().get(seat).id(), seat);
        session.synchronizeSeats(mounts);
        for (int seat = 0; seat < 4; seat++) {
            UUID actor = session.participants().get(seat).id();
            room = session.roomView(actor);
            int ready = room.actions().indexOf(new RoomAction(RoomAction.Type.READY));
            assertTrue(session.actRoom(actor, tableId, room.incarnation(), room.decision(), ready));
        }
        assertEquals(TableSession.Lifecycle.PLAYING, session.lifecycle());
        String saved = TableSessionCodec.save(session);
        assertFalse(saved.contains("\"game_type\""));
        assertFalse(saved.contains("\"mcr_session\""));
        var restored = (McrSession) TableSessionCodec.restore(saved);
        assertEquals(MahjongVariant.MCR, restored.variant());
        assertEquals(tableId, restored.tableId());
        assertTrue(restored.view(id(0)).game().actions().isEmpty());
        assertNotEquals(session.incarnation(), restored.incarnation());
        assertFalse(restored.act(id(0), tableId, session.incarnation(),
            ((McrSession) session).view(id(0)).game().decision(), 0));
    }
    private static UUID id(int seat) { return new UUID(791, seat + 1); }
    private static RiichiSession room(boolean manual, int humans) {
        var game = new RiichiSession(new UUID(11, 18), RiichiPreset.TENHOU_4, 17);
        game.configureEquipment(manual, game.suppliedTiles);
        for (int seat = 0; seat < humans; seat++) assertTrue(game.join(id(seat), "Human " + seat, seat));
        return game;
    }
    private static void act(RiichiSession game, UUID actor, RoomAction.Type type, int... values) {
        var expected = new RoomAction(type, Arrays.stream(values).boxed().toList());
        int index = game.roomView(actor).actions().indexOf(expected);
        assertTrue(index >= 0, () -> "Missing " + expected + " in " + game.roomView(actor).actions());
        assertTrue(game.actRoom(actor, game.tableId(), game.incarnation(), game.decision(), index));
    }
    private static void arriveAndReady(RiichiSession game) {
        var humans = Arrays.stream(game.participants).filter(member -> member.id != null && !member.bot).map(member -> member.id).toList();
        for (var human : humans) assertTrue(game.join(human, "Human", game.seatOf(human)));
        for (var human : humans) act(game, human, RoomAction.Type.READY);
    }

    @Test void seatingRequiresEquipmentBeforeLeavingTheLobby() {
        var game = room(true, 4);
        var tiles = game.suppliedTiles;
        int begin = game.roomView(id(0)).actions().indexOf(new RoomAction(RoomAction.Type.BEGIN_SEATING));
        long decision = game.decision;
        assertTrue(begin >= 0);

        assertTrue(game.configureEquipment(true, List.of()));
        assertTrue(game.roomView(id(0)).actions().stream().noneMatch(action -> action.type() == RoomAction.Type.BEGIN_SEATING));
        assertFalse(game.actRoom(id(0), game.tableId(), game.incarnation(), decision, begin));
        assertEquals(RoomSeating.Stage.GATHERING, game.roomView(null).seating());

        assertTrue(game.configureEquipment(true, tiles));
        act(game, id(0), RoomAction.Type.BEGIN_SEATING);
        assertEquals(RoomSeating.Stage.DRAWING, game.roomView(null).seating());
    }

    @Test void companionsKeepTheirIdentityThroughSeatingDifficultyChangesAndReloads() {
        var game = room(false, 1);
        UUID maid = id(8);
        String modelName = "model.touhou_little_maid.cirno.name";
        assertFalse(game.joinEntityBot(null, maid, "Reimu", 1));
        assertFalse(game.joinEntityBot(id(9), maid, "Reimu", 1));
        assertFalse(game.joinEntityBot(id(0), id(0), "Reimu", 1));
        assertFalse(game.joinEntityBot(id(0), maid, "Reimu", 0));
        assertTrue(game.joinEntityBot(id(0), maid, modelName, 1));
        assertFalse(game.joinEntityBot(id(0), maid, "Duplicate", 2));
        assertFalse(game.transferHost(id(0), maid));
        act(game, id(0), RoomAction.Type.SET_BOT, 1, BotDifficulty.HARD.ordinal());
        assertEquals(modelName, game.roomView(null).seats().get(1).participant().name());
        act(game, id(0), RoomAction.Type.FILL_BOTS);
        act(game, id(0), RoomAction.Type.BEGIN_SEATING);
        game = (RiichiSession) TableSessionCodec.restore(TableSessionCodec.save(game));
        int seat = game.seatOf(maid);
        assertTrue(game.entityBot(maid));
        assertEquals(modelName, game.roomView(null).seats().get(seat).participant().name());
        assertEquals(BotDifficulty.HARD, game.roomView(null).seats().get(seat).participant().difficulty());
        game.synchronizeSeats(Map.of(id(0), game.seatOf(id(0))), java.util.Set.of(id(0)));
        arriveAndReady(game);
        assertTrue(game.roomView(null).lobby(), "A missing companion cannot start a match");
        game.synchronizeSeats(Map.of(id(0), game.seatOf(id(0)), maid, seat), java.util.Set.of(id(0)));
        for (int tick = 0; tick < 24 && game.roomView(null).lobby(); tick++) game.tick();
        assertEquals(RiichiView.Phase.TURN, game.view(null).phase());
        game.validate();
        var concealed = List.copyOf(game.game().players[seat].hand);
        int points = game.game().points(seat);
        game.leaveEntityBot(maid);
        assertEquals(-1, game.seatOf(maid));
        assertTrue(game.trainingSeat(seat));
        assertEquals(concealed, game.game().players[seat].hand);
        assertEquals(points, game.game().points(seat));
        assertFalse(game.game().players[seat].member.entityBot);
        game.validate();
    }

    @Test void aNearbyOwnersCompanionMayClaimAPlaceBeforeTheOwnerJoins() {
        var game = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 1);
        UUID owner = id(0), maid = id(8);
        assertTrue(game.joinEntityBot(owner, maid, "Reimu", 1));
        assertEquals(1, game.seatOf(maid));
        assertEquals(-1, game.roomView(null).host());
        assertTrue(game.join(owner, "Owner", 0));
        assertEquals(0, game.roomView(null).host());
        game.leaveEntityBot(maid);
        assertEquals(-1, game.seatOf(maid));
    }

    @Test void absentCompanionsReleaseLobbySeatsAndCanBeDismissedByTheHost() {
        var game = room(false, 1);
        UUID maid = id(8);
        assertTrue(game.joinEntityBot(id(0), maid, "Marisa", 1));
        game.synchronizeSeats(Map.of(id(0), 0), java.util.Set.of(id(0)));
        for (int tick = 0; tick < TableSession.AWAY_GRACE_TICKS; tick++) game.tick();
        assertEquals(-1, game.seatOf(maid));
        assertNull(game.roomView(null).seats().get(1).participant().id());
        assertTrue(game.joinEntityBot(id(0), maid, "Marisa", 1));
        act(game, id(0), RoomAction.Type.REMOVE_BOT, 1);
        assertFalse(game.entityBot(maid));
        assertTrue(game.isHost(id(0)));
        game.validate();
    }

    @Test void neighboringLotterySeedsDoNotPinWindsToTheSameChoices() {
        for (int players : new int[]{3, 4}) {
            int[] seen = new int[players];
            for (long decision = 0; decision < 64; decision++) {
                var seating = new RoomSeating();
                seating.begin(players, true, 17L ^ decision);
                seating.validate(players);
                for (int slot = 0; slot < players; slot++) seen[slot] |= 1 << seating.concealed[slot];
            }
            for (int winds : seen) assertEquals((1 << players) - 1, winds,
                "Every concealed choice must vary across all winds when the decision counter changes");
        }
    }

    @Test void windDrawingSurvivesVacanciesAndCannotStartUntilAssignedPlayersActuallyArrive() {
        var game = room(true, 4);
        act(game, id(0), RoomAction.Type.BEGIN_SEATING);
        assertEquals(RoomSeating.Stage.DRAWING, game.roomView(null).seating());
        assertFalse(new Gson().toJson(game.roomView(null)).contains("concealed"));
        act(game, id(0), RoomAction.Type.DRAW_WIND, 0);
        act(game, id(1), RoomAction.Type.DRAW_WIND, 1);
        var available = game.roomView(null).availableWinds();
        int[] concealed = game.seating.concealed.clone();
        game = (RiichiSession) TableSessionCodec.restore(TableSessionCodec.save(game));
        assertEquals(available, game.roomView(null).availableWinds());
        game.synchronizeSeats(Map.of(id(0), 0, id(2), 2, id(3), 3), java.util.Set.of(id(0), id(2), id(3)));
        assertArrayEquals(concealed, game.seating.concealed, "Reloading must not reshuffle unturned winds");
        int inheritedWind = game.roomView(null).seats().get(1).wind();
        game.unseat(id(1));
        assertEquals(-1, game.seatOf(id(1)), "Standing in the lobby must leave the room");
        act(game, id(0), RoomAction.Type.SET_BOT, 1, BotDifficulty.HARD.ordinal());
        assertEquals(inheritedWind, game.roomView(null).seats().get(1).wind());
        assertTrue(game.roomActions(1).stream().noneMatch(action -> action.type() == RoomAction.Type.DRAW_WIND));
        act(game, id(2), RoomAction.Type.DRAW_WIND, 2);
        act(game, id(3), RoomAction.Type.DRAW_WIND, 3);
        assertEquals(RoomSeating.Stage.POSITIONING, game.roomView(null).seating());
        assertTrue(game.isHost(id(0)), "Ownership must follow the human, not the old seat");
        for (int seat = 0; seat < 4; seat++) assertEquals(seat, game.roomView(null).seats().get(seat).wind());
        game.synchronizeSeats(Map.of(), java.util.Set.of(id(0), id(2), id(3)));
        for (UUID human : List.of(id(0), id(2), id(3))) {
            assertTrue(game.roomView(human).actions().stream().noneMatch(action -> action.type() == RoomAction.Type.READY));
            assertFalse(game.join(human, "Human", (game.seatOf(human) + 1) % 4));
        }
        game = (RiichiSession) TableSessionCodec.restore(TableSessionCodec.save(game));
        assertTrue(game.roomView(null).seats().stream().filter(seat -> seat.participant().difficulty() == null)
            .allMatch(seat -> seat.presence() == PlayerPresence.DISCONNECTED));
        game.synchronizeSeats(Map.of(), java.util.Set.of(id(0), id(2), id(3)));
        assertTrue(game.roomView(null).seats().stream().filter(seat -> seat.participant().difficulty() == null)
            .allMatch(seat -> seat.presence() == PlayerPresence.AWAY));
        arriveAndReady(game);
        assertEquals(RiichiView.Phase.SHUFFLE, game.view(null).phase());
        assertEquals(0, game.game().dealer);
        game.validate();
    }

    @Test void temporaryAbsenceRetainsMembershipDuringAnActiveMatch() {
        for (var preset : List.of(RiichiPreset.TENHOU_3, RiichiPreset.TENHOU_4)) {
            var game = new RiichiSession(new UUID(11, 18), preset, 17);
            game.configureEquipment(false, game.suppliedTiles);
            assertTrue(game.join(id(0), "Host", 0));
            assertTrue(game.join(id(1), "Guest", 1));
            act(game, id(0), RoomAction.Type.FILL_BOTS);
            act(game, id(0), RoomAction.Type.BEGIN_SEATING);
            arriveAndReady(game);
            assertFalse(game.roomView(null).lobby());
            var preference = new RiichiAutoPlay(false, false);
            game.game().players[0].autoPlay = preference;
            long revision = game.revision();
            game.unseat(id(0));
            assertTrue(game.revision() > revision);
            assertEquals(PlayerPresence.AWAY, game.roomView(null).seats().getFirst().presence());
            for (int tick = 1; tick < TableSession.AWAY_GRACE_TICKS; tick++) game.tick();
            assertEquals(PlayerPresence.AWAY, game.roomView(null).seats().getFirst().presence());
            assertFalse(game.join(id(0), "Host", 2));
            game.synchronizeSeats(Map.of(id(0), 0, id(1), 1), java.util.Set.of(id(0), id(1)));
            game.tick();
            assertEquals(PlayerPresence.SEATED, game.roomView(null).seats().getFirst().presence());
            assertEquals(preference, game.game().players[0].autoPlay);
            assertTrue(game.isHost(id(0)));

            game.unseat(id(0));
            for (int tick = 1; tick < TableSession.AWAY_GRACE_TICKS; tick++) game.tick();
            revision = game.revision();
            assertEquals(PlayerPresence.AWAY, game.roomView(null).seats().getFirst().presence());
            game.tick();
            assertTrue(game.revision() > revision);
            assertEquals(PlayerPresence.DISCONNECTED, game.roomView(null).seats().getFirst().presence());
            assertEquals(0, game.seatOf(id(0)));
            assertEquals(preference, game.game().players[0].autoPlay);
            assertTrue(game.isHost(id(0)));
        }
    }

    @Test void lobbyDismountImmediatelyLeavesAndTransfersHost() {
        var game = room(false, 2);
        game.unseat(id(0));
        assertEquals(-1, game.seatOf(id(0)));
        assertTrue(game.isHost(id(1)));
        assertNull(game.roomView(null).seats().getFirst().presence());
        assertTrue(game.join(id(2), "Replacement", 0));
        game.validate();
    }

    @Test void abandonedLobbyClosesAfterTheLastHumansGracePeriod() {
        for (var preset : List.of(RiichiPreset.TENHOU_3, RiichiPreset.TENHOU_4)) {
            var game = new RiichiSession(new UUID(11, 18), preset, 17);
            assertTrue(game.join(id(0), "Host", 0));
            assertTrue(game.join(id(1), "Guest", 1));
            act(game, id(0), RoomAction.Type.FILL_BOTS);
            act(game, id(0), RoomAction.Type.BEGIN_SEATING);
            game.synchronizeSeats(Map.of(), java.util.Set.of(id(1)));
            for (int tick = 1; tick < TableSession.AWAY_GRACE_TICKS; tick++) game.tick();
            assertTrue(game.isHost(id(0)));
            assertEquals(PlayerPresence.AWAY, game.participants[game.seatOf(id(1))].presence);
            game.tick();
            assertEquals(-1, game.host());
            assertEquals(RoomSeating.Stage.GATHERING, game.roomView(null).seating());
            assertTrue(Arrays.stream(game.participants).allMatch(member -> member.id == null));
            long revision = game.revision();
            game.tick();
            assertEquals(revision, game.revision(), "An empty lobby must not be repeatedly closed");
            assertTrue(game.join(id(2), "New host", 0));
            assertTrue(game.isHost(id(2)));
            game.validate();
        }
    }

    @Test void allConnectionsLostClosesLobbyButRetainsAnActiveMatch() {
        for (boolean active : new boolean[]{false, true}) {
            var game = room(false, 4);
            if (active) {
                act(game, id(0), RoomAction.Type.BEGIN_SEATING);
                arriveAndReady(game);
            }
            game.synchronizeSeats(Map.of(), java.util.Set.of());
            game.tick();
            if (active) {
                assertFalse(game.roomView(null).lobby());
                for (int seat = 0; seat < 4; seat++) {
                    assertTrue(game.seatOf(id(seat)) >= 0);
                    assertEquals(PlayerPresence.DISCONNECTED, game.game().players[seat].member.presence);
                }
            } else {
                assertEquals(-1, game.host());
                assertTrue(Arrays.stream(game.participants).allMatch(member -> member.id == null));
            }
            game.validate();
        }
    }

    @Test void automaticSeatingAllowsAllBotTiersAndReplacementOnlyForDisconnectedHumans() {
        var game = room(false, 2);
        assertTrue(game.roomView(id(1)).actions().stream().noneMatch(action -> action.type() == RoomAction.Type.SET_BOT));
        assertTrue(game.roomView(id(0)).actions().stream().noneMatch(action -> action.type() == RoomAction.Type.SET_BOT && action.arguments().getFirst() == 1));
        act(game, id(0), RoomAction.Type.FILL_BOTS);
        assertTrue(game.roomView(null).seats().stream().filter(seat -> seat.participant().difficulty() != null)
            .allMatch(seat -> seat.participant().difficulty() == BotDifficulty.EASY));
        act(game, id(0), RoomAction.Type.SET_BOT, 3, BotDifficulty.HARD.ordinal());
        assertEquals(BotDifficulty.HARD, game.roomView(null).seats().get(3).participant().difficulty());
        act(game, id(0), RoomAction.Type.REMOVE_BOT, 3);
        assertNull(game.roomView(id(0)).seats().get(3).participant().id());
        act(game, id(0), RoomAction.Type.SET_BOT, 3, BotDifficulty.HARD.ordinal());
        act(game, id(0), RoomAction.Type.BEGIN_SEATING);
        assertEquals(RoomSeating.Stage.POSITIONING, game.roomView(null).seating());
        assertTrue(game.roomView(id(0)).actions().stream().noneMatch(action -> action.type() == RoomAction.Type.DRAW_WIND));
        for (int tick = 0; tick < 60; tick++) game.tick();
        assertTrue(Arrays.stream(game.participants).filter(member -> member.bot).allMatch(member -> member.ready));
        assertTrue(game.roomView(null).lobby());
        int guest = game.seatOf(id(1));
        game.unseat(id(1));
        assertEquals(-1, game.seatOf(id(1)), "Standing in the lobby must release the seat");
        assertTrue(game.join(id(1), "Returning guest", guest));
        var mounted = Map.of(id(0), game.seatOf(id(0)));
        game.synchronizeSeats(mounted, java.util.Set.of(id(0)));
        assertEquals(PlayerPresence.DISCONNECTED, game.roomView(null).seats().get(guest).presence());
        act(game, id(0), RoomAction.Type.SET_BOT, guest, BotDifficulty.EASY.ordinal());
        assertEquals(-1, game.seatOf(id(1)));
        assertEquals(BotDifficulty.EASY, game.roomView(null).seats().get(guest).participant().difficulty());
        act(game, id(0), RoomAction.Type.REMOVE_BOT, guest);
        assertTrue(game.join(id(4), "New human", guest));
        assertEquals(guest, game.roomView(null).seats().get(guest).wind());
        assertEquals(4, Arrays.stream(game.participants).map(member -> member.id).distinct().count());
        assertTrue(game.roomView(null).lobby());
        game.validate();
        arriveAndReady(game);
        assertEquals(RiichiView.Phase.TURN, game.view(null).phase());
        game.validate();
    }

    @Test void rematchRequiresFreshSeatingAndRetainsPlayersHostAndWorldPolicy() {
        var game = room(false, 4);
        var policy = new WorldPolicy(true, false, true, 1_000, true, true, true, true, null);
        game.configureWorld(policy);
        assertTrue(game.configureHandVisibility(id(0), game.decision, PlayerHandVisibility.ALL));
        long token = game.decision;
        assertTrue(game.transferHost(id(0), id(2)));
        assertNotEquals(token, game.decision);
        act(game, id(2), RoomAction.Type.BEGIN_SEATING);
        arriveAndReady(game);
        var roster = Arrays.stream(game.game().players).map(player -> player.member.id).toList();
        game.game().newDecision(RiichiGame.Phase.MATCH_END);
        for (var player : game.game().players) player.member.ready = false;
        assertTrue(game.roomView(id(2)).actions().isEmpty());
        for (UUID human : roster)
            assertEquals(List.of(new RiichiAction(RiichiAction.Type.SKIP_SETTLEMENT)), game.view(human).actions());
        assertFalse(game.requestExit(id(2)));
        for (int tick = 0; tick < RiichiGame.SETTLEMENT_TICKS; tick++) game.tick();
        assertEquals(RiichiView.Phase.MATCH_END, game.view(null).phase());
        assertEquals(RiichiGame.SETTLEMENT_TICKS, game.view(null).settlementTicks());
        for (int tick = 0; tick < RiichiGame.SETTLEMENT_TICKS - 1; tick++) game.tick();
        assertEquals(RiichiView.Phase.MATCH_END, game.view(null).phase());
        game.tick();
        assertTrue(game.roomView(null).lobby());
        assertEquals(RoomSeating.Stage.GATHERING, game.roomView(null).seating());
        assertEquals(roster, Arrays.stream(game.participants).map(member -> member.id).toList());
        assertTrue(game.isHost(id(2)));
        assertEquals(PlayerHandVisibility.ALL, game.playerHandVisibility);
        assertEquals(policy, game.worldPolicy);
        assertNull(game.game());
        assertTrue(game.roomView(id(2)).actions().stream().noneMatch(action -> action.type() == RoomAction.Type.READY));
        game.validate();
    }
}
