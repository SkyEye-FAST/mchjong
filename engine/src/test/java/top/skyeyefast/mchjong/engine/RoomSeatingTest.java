package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoomSeatingTest {
    private static UUID id(int seat) { return new UUID(791, seat + 1); }
    private static Game room(boolean manual, int humans) {
        var game = new Game(new UUID(11, 18), RuleSet.TENHOU_4, 17);
        game.configureEquipment(manual, game.suppliedTiles);
        for (int seat = 0; seat < humans; seat++) assertTrue(game.join(id(seat), "Human " + seat, seat));
        return game;
    }
    private static void act(Game game, UUID actor, Action.Type type, int... values) {
        var expected = new Action(type, Arrays.stream(values).boxed().toList());
        int index = game.view(actor).actions().indexOf(expected);
        assertTrue(index >= 0, () -> "Missing " + expected + " in " + game.view(actor).actions());
        assertTrue(game.act(actor, game.decision, index));
    }
    private static void arriveAndReady(Game game) {
        var humans = Arrays.stream(game.players).filter(player -> player.id != null && !player.bot).map(player -> player.id).toList();
        for (var human : humans) assertTrue(game.join(human, "Human", game.seatOf(human)));
        for (var human : humans) act(game, human, Action.Type.READY);
    }

    @Test void windDrawingSurvivesVacanciesAndCannotStartUntilAssignedPlayersActuallyArrive() {
        var game = room(true, 4);
        act(game, id(0), Action.Type.BEGIN_SEATING);
        assertEquals(RoomSeating.Stage.DRAWING, game.roomView().seating());
        assertFalse(new Gson().toJson(game.roomView()).contains("concealed"));
        act(game, id(0), Action.Type.DRAW_WIND, 0);
        act(game, id(1), Action.Type.DRAW_WIND, 1);
        int inheritedWind = game.roomView().seats().get(1).wind();
        game.leave(id(1));
        act(game, id(0), Action.Type.SET_BOT, 1, BotDifficulty.HARD.ordinal());
        assertEquals(inheritedWind, game.roomView().seats().get(1).wind());
        assertTrue(game.actions(1).stream().noneMatch(action -> action.type() == Action.Type.DRAW_WIND));
        act(game, id(2), Action.Type.DRAW_WIND, 2);
        act(game, id(3), Action.Type.DRAW_WIND, 3);
        assertEquals(RoomSeating.Stage.POSITIONING, game.roomView().seating());
        assertTrue(game.isHost(id(0)), "Ownership must follow the human, not the old seat");
        for (int seat = 0; seat < 4; seat++) assertEquals(seat, game.roomView().seats().get(seat).wind());
        game.synchronizeSeats(Map.of());
        for (UUID human : List.of(id(0), id(2), id(3))) {
            assertTrue(game.view(human).actions().stream().noneMatch(action -> action.type() == Action.Type.READY));
            assertFalse(game.join(human, "Human", (game.seatOf(human) + 1) % 4));
        }
        game = new Gson().fromJson(new Gson().toJson(game), Game.class);
        game.validate();
        assertTrue(game.roomView().seats().stream().filter(seat -> seat.difficulty() == null).noneMatch(RoomView.Seat::present));
        arriveAndReady(game);
        assertEquals(Game.Phase.SHUFFLE, game.phase());
        assertEquals(0, game.dealer);
        game.validate();
    }

    @Test void automaticSeatingAllowsAllBotTiersAndReplacementOnlyForUnoccupiedPhysicalSeats() {
        var game = room(false, 2);
        assertTrue(game.view(id(1)).actions().stream().noneMatch(action -> action.type() == Action.Type.SET_BOT));
        assertTrue(game.view(id(0)).actions().stream().noneMatch(action -> action.type() == Action.Type.SET_BOT && action.tiles().getFirst() == 1));
        act(game, id(0), Action.Type.SET_BOT, 2, BotDifficulty.EASY.ordinal());
        act(game, id(0), Action.Type.SET_BOT, 3, BotDifficulty.HARD.ordinal());
        act(game, id(0), Action.Type.BEGIN_SEATING);
        assertEquals(RoomSeating.Stage.POSITIONING, game.roomView().seating());
        assertTrue(game.view(id(0)).actions().stream().noneMatch(action -> action.type() == Action.Type.DRAW_WIND));
        for (int tick = 0; tick < 60; tick++) game.tick();
        assertTrue(Arrays.stream(game.players).filter(player -> player.bot).allMatch(player -> player.ready));
        assertEquals(Game.Phase.LOBBY, game.phase());
        int guest = game.seatOf(id(1));
        game.leave(id(1));
        assertEquals(guest, game.seatOf(id(1)), "Standing to relocate must retain room membership");
        act(game, id(0), Action.Type.SET_BOT, guest, BotDifficulty.NORMAL.ordinal());
        assertEquals(-1, game.seatOf(id(1)));
        assertEquals(BotDifficulty.NORMAL, game.roomView().seats().get(guest).difficulty());
        act(game, id(0), Action.Type.REMOVE_BOT, guest);
        assertTrue(game.join(id(4), "New human", guest));
        assertEquals(guest, game.roomView().seats().get(guest).wind());
        assertEquals(4, Arrays.stream(game.players).map(player -> player.id).distinct().count());
        assertEquals(Game.Phase.LOBBY, game.phase());
        game.validate();
        arriveAndReady(game);
        assertEquals(Game.Phase.TURN, game.phase());
        game.validate();
    }

    @Test void rematchRequiresFreshSeatingAndRetainsPlayersHostAndWorldPolicy() {
        var game = room(false, 4);
        game.configureWorld(true, true);
        long token = game.decision;
        assertTrue(game.transferHost(id(0), id(2)));
        assertNotEquals(token, game.decision);
        act(game, id(2), Action.Type.BEGIN_SEATING);
        arriveAndReady(game);
        var roster = Arrays.stream(game.players).map(player -> player.id).toList();
        game.phase = Game.Phase.MATCH_END;
        for (var player : game.players) player.ready = false;
        for (UUID human : roster) act(game, human, Action.Type.NEXT);
        assertEquals(Game.Phase.LOBBY, game.phase());
        assertEquals(RoomSeating.Stage.GATHERING, game.roomView().seating());
        assertEquals(roster, Arrays.stream(game.players).map(player -> player.id).toList());
        assertTrue(game.isHost(id(2)));
        assertTrue(game.openHands && game.roomView().invitationTeleport());
        assertNull(game.wall);
        assertTrue(game.view(id(2)).actions().stream().noneMatch(action -> action.type() == Action.Type.READY));
        game.validate();
    }
}
