package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class ExitVotingTest {
    private static final UUID HOST = new UUID(50, 1);
    private static final UUID GUEST = new UUID(50, 2);

    private static void action(Game game, UUID actor, Action.Type type) {
        var view = game.view(actor);
        assertTrue(game.act(actor, view.decision(), GameLifecycleTest.index(view, type)));
    }

    @ParameterizedTest @EnumSource(RuleSet.class)
    void singleHumanExitsWithBotsAndReleasesTheEntireTable(RuleSet rules) {
        var game = new Game(UUID.randomUUID(), rules, 71);
        assertTrue(game.join(HOST, "Host", 0));
        game.configureWorld(true, false);
        action(game, HOST, Action.Type.PRACTICE);
        assertEquals(Game.Phase.TURN, game.phase());
        assertTrue(game.requestExit(HOST));
        assertEquals(Game.Phase.LOBBY, game.phase());
        assertEquals(-1, game.seatOf(HOST));
        assertNull(game.view(null).exitVote());
        assertTrue(game.view(null).wall().isEmpty());
        assertTrue(game.view(null).seats().stream().noneMatch(TableView.Seat::occupied));
        assertTrue(game.view(null).openHands());
        assertTrue(game.join(HOST, "Host", 0));
        action(game, HOST, Action.Type.PRACTICE);
        assertEquals(Game.Phase.TURN, game.phase());
        game.validate();
    }

    @Test void twoHumansAndBotsNeedExactlyTwoApprovals() {
        var game = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 72);
        game.join(HOST, "Host", 0);
        game.join(GUEST, "Guest", 1);
        action(game, GUEST, Action.Type.READY);
        action(game, HOST, Action.Type.PRACTICE);
        assertTrue(game.requestExit(HOST));
        var vote = game.view(GUEST).exitVote();
        assertEquals(2, vote.required());
        assertEquals(List.of(0), vote.agreed());
        assertFalse(game.answerExit(game.players[2].id, vote.id(), true));
        assertFalse(game.answerExit(UUID.randomUUID(), vote.id(), true));
        assertFalse(game.answerExit(HOST, vote.id(), true));
        assertFalse(game.answerExit(GUEST, vote.id() + 1, true));
        game.leave(GUEST);
        assertEquals(1, game.seatOf(GUEST), "Dismounting is not consent or a way to reduce the electorate");
        assertTrue(game.answerExit(GUEST, vote.id(), true));
        assertEquals(Game.Phase.LOBBY, game.phase());
        assertTrue(game.view(null).seats().stream().noneMatch(TableView.Seat::occupied));
        game.validate();
    }

    @Test void rejectedAndExpiredVotesPreserveTheDecisionAndClocksWithoutGrantingTime() {
        var game = GameLifecycleTest.started(RuleSet.MAHJONG_SOUL_4, 81);
        game.age = 1;
        UUID actor = game.players[game.turn].id;
        var before = game.view(actor);
        int[] move = game.moveTicks.clone(), reserve = game.reserveTicks.clone();
        assertTrue(game.requestExit(actor));
        var vote = game.view(actor).exitVote();
        assertTrue(game.view(actor).actions().isEmpty());
        assertFalse(game.act(actor, before.decision(), 0));
        assertTrue(game.view(actor).clocks().stream().noneMatch(TimeControl.Clock::active));
        for (int tick = 0; tick < 100; tick++) game.tick();
        assertArrayEquals(move, game.moveTicks);
        assertArrayEquals(reserve, game.reserveTicks);
        assertTrue(game.answerExit(game.players[(game.turn + 1) % 4].id, vote.id(), false));
        assertEquals(before.actions(), game.view(actor).actions());
        assertFalse(game.requestExit(actor), "A rejected ballot has a cooldown");
        game.exitCooldown = 0;
        assertTrue(game.requestExit(actor));
        long second = game.view(actor).exitVote().id();
        assertNotEquals(vote.id(), second);
        assertFalse(game.answerExit(game.players[(game.turn + 1) % 4].id, vote.id(), true));
        game = new Gson().fromJson(new Gson().toJson(game), Game.class);
        game.validate();
        for (int tick = 0; tick < ExitVote.DURATION_TICKS; tick++) game.tick();
        assertNull(game.view(actor).exitVote());
        assertEquals(before.actions(), game.view(actor).actions());
        assertArrayEquals(move, game.moveTicks);
        assertArrayEquals(reserve, game.reserveTicks);
        game.validate();
    }

    @Test void lobbyCannotChangeElectorateDuringAVote() {
        var game = new Game(UUID.randomUUID(), RuleSet.MAHJONG_SOUL_4, 93);
        game.join(HOST, "Host", 0);
        game.join(GUEST, "Guest", 1);
        assertTrue(game.requestExit(HOST));
        var vote = game.view(HOST).exitVote();
        assertNotNull(vote);
        game.leave(GUEST);
        assertEquals(1, game.seatOf(GUEST));
        assertFalse(game.join(UUID.randomUUID(), "Late join", 2));
        assertFalse(game.configureClock(HOST, new TimeControl(30, 10)));
        assertFalse(game.transferHost(HOST, GUEST));
        assertTrue(game.answerExit(GUEST, vote.id(), true));
        game.validate();
    }

    @Test void hostOwnershipIsIndependentOfSeatOrderAndSurvivesReload() {
        var game = new Game(UUID.randomUUID(), RuleSet.TENHOU_3, 106);
        game.join(HOST, "Host", 2);
        game.join(GUEST, "Guest", 0);
        assertTrue(game.isHost(HOST));
        assertFalse(game.transferHost(GUEST, HOST));
        assertFalse(game.transferHost(HOST, UUID.randomUUID()));
        assertTrue(game.transferHost(HOST, GUEST));
        assertFalse(game.configureClock(HOST, TimeControl.DEFAULT));
        assertTrue(game.configureClock(GUEST, TimeControl.DEFAULT));
        game = new Gson().fromJson(new Gson().toJson(game), Game.class);
        assertTrue(game.isHost(GUEST));
        game.leave(GUEST);
        assertTrue(game.isHost(HOST));
        game.validate();
    }
}
