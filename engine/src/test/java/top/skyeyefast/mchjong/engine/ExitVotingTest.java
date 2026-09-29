package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ExitVotingTest {
    private static final UUID HOST = new UUID(50, 1);
    private static final UUID GUEST = new UUID(50, 2);

    @ParameterizedTest @EnumSource(value = RiichiPreset.class, names = {"TENHOU_4", "TENHOU_3"})
    void singleHumanExitsEveryMatchStageWithBotsAndCanStartAgain(RiichiPreset rules) {
        for (var phase : RiichiGame.Phase.values()) {
            if (phase == RiichiGame.Phase.MATCH_END) continue;
            var session = new RiichiSession(UUID.randomUUID(), rules, 71);
            assertTrue(session.join(HOST, "Host", 0));
            assertTrue(session.configureOpenHands(HOST, session.decision, true));
            GameLifecycleTest.startPositioned(session);
            var game = session.game();
            game.phase = phase;
            assertTrue(session.requestExit(HOST), phase.name());
            assertEquals(TableSession.Lifecycle.LOBBY, session.lifecycle());
            assertEquals(-1, session.seatOf(HOST));
            var view = session.view(null);
            assertNull(view.exitVote());
            assertTrue(view.wall().isEmpty());
            assertTrue(view.seats().stream().noneMatch(TableView.Seat::occupied));
            assertTrue(view.openHands());
            session.validate();
            assertTrue(session.join(HOST, "Host", 0));
            GameLifecycleTest.startPositioned(session);
            assertEquals(RiichiGame.Phase.TURN, session.game().phase());
            session.validate();
        }
    }

    @ParameterizedTest @ValueSource(ints = {2, 3})
    void humansMustUnanimouslyApproveWhileBotsAndInvalidVotesAreRejected(int humans) {
        var session = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 72);
        var voters = List.of(HOST, GUEST, new UUID(50, 3));
        for (int seat = 0; seat < humans; seat++) session.join(voters.get(seat), "Human " + seat, seat);
        GameLifecycleTest.startPositioned(session);
        var game = session.game();
        assertFalse(session.requestExit(UUID.randomUUID()));
        long decision = session.decision;
        assertTrue(session.requestExit(HOST));
        assertNotEquals(decision, session.decision);
        var vote = session.view(GUEST).exitVote();
        assertEquals(humans, vote.required());
        assertEquals(List.of(0), vote.agreed());
        assertFalse(session.answerExit(game.players[3].member.id, vote.id(), true));
        assertFalse(session.answerExit(UUID.randomUUID(), vote.id(), true));
        assertFalse(session.answerExit(HOST, vote.id(), true));
        assertFalse(session.answerExit(GUEST, vote.id() + 1, true));
        assertEquals(RiichiGame.Phase.TURN, game.phase(), "Partial approval must not terminate play");
        session.unseat(GUEST);
        assertEquals(1, session.seatOf(GUEST), "Dismounting is not consent or a way to reduce the electorate");
        assertEquals(humans, game.view(GUEST).exitVote().required());
        for (int seat = 1; seat < humans; seat++) {
            assertTrue(session.answerExit(voters.get(seat), vote.id(), true));
            if (seat + 1 < humans) {
                assertEquals(RiichiGame.Phase.TURN, game.phase());
                assertEquals(seat + 1, game.view(HOST).exitVote().agreed().size());
                assertTrue(game.view(HOST).actions().isEmpty());
            }
        }
        assertEquals(TableSession.Lifecycle.LOBBY, session.lifecycle());
        assertTrue(session.view(null).seats().stream().noneMatch(TableView.Seat::occupied));
        session.validate();
    }

    @Test void rejectedAndExpiredVotesPreserveTheDecisionAndClocksWithoutGrantingTime() {
        var game = GameLifecycleTest.started(RiichiPreset.MAHJONG_SOUL_4, 81);
        game.age = 1;
        UUID actor = game.players[game.turn].member.id;
        var before = game.view(actor);
        int[] move = game.moveTicks.clone(), reserve = game.reserveTicks.clone();
        assertTrue(game.session.requestExit(actor));
        var vote = game.view(actor).exitVote();
        assertTrue(game.view(actor).actions().isEmpty());
        assertFalse(game.act(actor, before.decision(), 0));
        assertTrue(game.view(actor).clocks().stream().noneMatch(TimeControl.Clock::active));
        for (int tick = 0; tick < 100; tick++) game.tick();
        assertArrayEquals(move, game.moveTicks);
        assertArrayEquals(reserve, game.reserveTicks);
        assertTrue(game.session.answerExit(game.players[(game.turn + 1) % 4].member.id, vote.id(), false));
        assertEquals(before.actions(), game.view(actor).actions());
        assertFalse(game.session.requestExit(actor), "A rejected ballot has a cooldown");
        game.session.exitCooldown = 0;
        assertTrue(game.session.requestExit(actor));
        long second = game.view(actor).exitVote().id();
        assertNotEquals(vote.id(), second);
        assertFalse(game.session.answerExit(game.players[(game.turn + 1) % 4].member.id, vote.id(), true));
        assertEquals(second, game.view(actor).exitVote().id());
        game = GameLifecycleTest.reloadMounted(game);
        for (int tick = 0; tick < ExitVote.DURATION_TICKS; tick++) game.tick();
        assertNull(game.view(actor).exitVote());
        assertEquals(before.actions(), game.view(actor).actions());
        assertArrayEquals(move, game.moveTicks);
        assertArrayEquals(reserve, game.reserveTicks);
        game.validate();
    }

    @Test void lobbyGuestsLeaveIndividuallyAndOnlyHostCanDissolve() {
        var game = new RiichiSession(UUID.randomUUID(), RiichiPreset.MAHJONG_SOUL_4, 93);
        game.join(HOST, "Host", 0);
        game.join(GUEST, "Guest", 1);
        assertFalse(game.requestExit(GUEST));
        game.unseat(GUEST);
        assertEquals(-1, game.seatOf(GUEST));
        assertNull(game.view(HOST).exitVote());
        assertTrue(game.join(UUID.randomUUID(), "Late join", 2));
        assertTrue(game.configureClock(HOST, new TimeControl(30, 10)));
        assertFalse(game.transferHost(HOST, GUEST));
        assertTrue(game.requestExit(HOST));
        assertTrue(game.view(null).seats().stream().noneMatch(TableView.Seat::occupied));
        game.validate();
    }

    @Test void hostOwnershipIsIndependentOfSeatOrderAndSurvivesReload() {
        var game = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_3, 106);
        game.join(HOST, "Host", 2);
        game.join(GUEST, "Guest", 0);
        assertTrue(game.isHost(HOST));
        assertFalse(game.transferHost(GUEST, HOST));
        assertFalse(game.transferHost(HOST, UUID.randomUUID()));
        assertTrue(game.transferHost(HOST, GUEST));
        assertFalse(game.configureClock(HOST, TimeControl.DEFAULT));
        assertTrue(game.configureClock(GUEST, TimeControl.DEFAULT));
        game = GameLifecycleTest.reloadMounted(game);
        assertTrue(game.isHost(GUEST));
        var room = game.roomView(GUEST);
        int leave = room.actions().indexOf(new RoomAction(RoomAction.Type.LEAVE_ROOM));
        assertTrue(game.actRoom(GUEST, room.tableId(), room.incarnation(), room.decision(), leave));
        assertTrue(game.isHost(HOST));
        game.validate();
    }
}
