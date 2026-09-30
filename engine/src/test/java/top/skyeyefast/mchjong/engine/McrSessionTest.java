package top.skyeyefast.mchjong.engine;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.McrGameTest.fixed;
import static top.skyeyefast.mchjong.engine.McrAction.Type.*;

class McrSessionTest {
    private static final UUID TABLE = new UUID(10, 1);
    private static final UUID OUTSIDER = new UUID(2, 1);
    private static final List<TableParticipant> ROSTER = List.of(
        new TableParticipant(new UUID(1, 1), "Same name"),
        new TableParticipant(new UUID(1, 2), "Same name"),
        new TableParticipant(new UUID(1, 3), "West"),
        new TableParticipant(new UUID(1, 4), "North"));
    private static final Map<UUID, Integer> MOUNTS = Map.of(id(0), 0, id(1), 1, id(2), 2, id(3), 3);

    @Test void preparedRosterAndCompleteStockStartOneShuffledMatch() {
        var roster = new ArrayList<>(ROSTER);
        var stock = new ArrayList<>(Tile.mcrSet());
        var session = McrSession.start(TABLE, roster, 711, stock);
        roster.clear(); stock.clear();
        assertEquals(0, session.seatOf(id(0)));
        assertEquals(1, session.seatOf(id(1)), "Display names are not participant identities");
        assertEquals(-1, session.seatOf(OUTSIDER));
        assertEquals(-1, session.seatOf(null));
        assertTrue(session.view(id(0)).paused());
        assertEquals(-1, session.view(id(0)).game().viewerSeat());
        assertTrue(session.synchronizeSeats(MOUNTS));
        var reference = new McrGame(711);
        for (int seat = 0; seat < 4; seat++)
            assertEquals(reference.hand(seat), session.view(id(seat)).game().seats().get(seat).hand());
        assertThrows(UnsupportedOperationException.class, () -> session.save().room().participants().clear());
        assertThrows(IllegalArgumentException.class, () -> McrSession.start(TABLE, ROSTER, 1, Tile.set(false)));
        assertThrows(IllegalArgumentException.class, () -> McrSession.start(TABLE, ROSTER.subList(0, 3), 1, Tile.mcrSet()));
        assertThrows(IllegalArgumentException.class, () -> McrSession.start(TABLE,
            List.of(ROSTER.get(0), ROSTER.get(1), ROSTER.get(2), ROSTER.get(0)), 1, Tile.mcrSet()));
    }

    @Test void senderBindingRejectsForeignStaleAndUnmountedRequestsWithoutMutation() {
        var session = McrSession.start(TABLE, ROSTER, 711, Tile.mcrSet());
        session.synchronizeSeats(MOUNTS);
        var offered = session.view(id(0));
        String saved = McrCodec.saveSession(session);
        assertFalse(session.act(OUTSIDER, TABLE, offered.incarnation(), offered.game().decision(), 0));
        assertFalse(session.act(id(1), TABLE, offered.incarnation(), offered.game().decision(), 0));
        assertFalse(session.act(id(0), OUTSIDER, offered.incarnation(), offered.game().decision(), 0));
        assertFalse(session.act(id(0), TABLE, UUID.randomUUID(), offered.game().decision(), 0));
        assertFalse(session.act(id(0), TABLE, offered.incarnation(), offered.game().decision() - 1, 0));
        assertFalse(session.act(id(0), TABLE, offered.incarnation(), offered.game().decision(), 999));
        assertEquals(saved, McrCodec.saveSession(session));
        var mounts = new HashMap<>(MOUNTS);
        mounts.remove(id(0));
        mounts.put(OUTSIDER, 0);
        assertTrue(session.synchronizeSeats(mounts));
        assertEquals(-1, session.view(id(0)).game().viewerSeat());
        assertHidden(session.view(id(0)).game());
        assertHidden(session.view(OUTSIDER).game());
        assertTrue(session.view(id(1)).game().actions().isEmpty());
        assertFalse(session.act(id(0), TABLE, offered.incarnation(), offered.game().decision(), 0));
        mounts.put(id(0), 1); // Being mounted at somebody else's seat is not authorization.
        session.synchronizeSeats(mounts);
        assertEquals(-1, session.view(id(0)).game().viewerSeat());
        assertEquals(-1, session.view(id(1)).game().viewerSeat(), "Ambiguous physical occupancy grants neither player access");
        assertTrue(session.synchronizeSeats(MOUNTS));
        long revision = session.view(id(0)).revision();
        assertFalse(session.synchronizeSeats(MOUNTS));
        assertEquals(revision, session.view(id(0)).revision());
        assertTrue(session.act(id(0), TABLE, offered.incarnation(), offered.game().decision(), 0));
        assertFalse(session.act(id(0), TABLE, offered.incarnation(), offered.game().decision(), 0));
    }

    @Test void sharedExitVotePausesMcrActionsAndSurvivesRestore() {
        var session = McrSession.start(TABLE, ROSTER, 711, Tile.mcrSet());
        session.synchronizeSeats(MOUNTS);
        var before = session.view(id(0));
        assertTrue(session.requestExit(id(0)));
        var vote = session.roomView(id(1)).exitVote();
        assertNotNull(vote);
        assertEquals(4, vote.required());
        assertTrue(session.view(id(0)).game().actions().isEmpty());
        assertFalse(session.act(id(0), TABLE, before.incarnation(), before.game().decision(), 0));
        assertFalse(session.answerExit(OUTSIDER, vote.id(), true));
        assertTrue(session.answerExit(id(1), vote.id(), false));
        assertFalse(session.requestExit(id(0)), "A rejected vote starts the shared cooldown");
        for (int tick = 0; tick < ExitVote.DURATION_TICKS; tick++) session.tick();
        assertTrue(session.requestExit(id(0)));
        String saved = McrCodec.saveSession(session);
        var restored = McrCodec.restoreSession(saved);
        assertNotEquals(session.incarnation(), restored.incarnation());
        assertTrue(restored.paused());
        assertEquals(session.roomView(id(1)).exitVote(), restored.roomView(id(1)).exitVote());
        assertTrue(restored.synchronizeSeats(MOUNTS));
        assertTrue(restored.answerExit(id(1), restored.roomView(id(1)).exitVote().id(), true));
        assertTrue(restored.answerExit(id(2), restored.roomView(id(2)).exitVote().id(), true));
        assertTrue(restored.answerExit(id(3), restored.roomView(id(3)).exitVote().id(), true));
        assertTrue(restored.lobby());
    }

    @Test void lastPlayerMayKeepOrEndPausedMcrMatchAfterDismount() {
        var session = McrSession.start(TABLE, ROSTER, 711, Tile.mcrSet());
        session.synchronizeSeats(MOUNTS);
        session.unseat(id(0));
        assertFalse(session.paused());
        assertFalse(session.leaveDecision(id(0)));
        session.unseat(id(1));
        session.unseat(id(2));
        session.unseat(id(3));
        assertTrue(session.leaveDecision(id(3)));
        var restored = McrCodec.restoreSession(McrCodec.saveSession(session));
        assertTrue(restored.leaveDecision(id(3)));
        assertTrue(restored.resolveLeave(id(3), true));
        assertTrue(restored.paused());
        assertTrue(restored.synchronizeSeats(MOUNTS));
        assertFalse(restored.paused());
        restored.unseat(id(0));
        restored.unseat(id(1));
        restored.unseat(id(2));
        restored.unseat(id(3));
        assertTrue(restored.resolveLeave(id(3), false));
        assertTrue(restored.lobby());
    }

    @Test void pendingResponsesRestoreWithFreshIncarnationsAndNoAssumedPresence() {
        var game = fixed(4, new McrGameTest.Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "123456789p11s46m").hand(2, "123456789s22p46m")
            .hand(3, "555m123789m123p1z").build());
        McrGameTest.discardKind(game, 0, "5m");
        var session = session(game);
        var before = session.view(id(1));
        play(session, 2, WIN);
        assertTrue(session.view(id(2)).game().responded());
        assertEquals(before.game().decision(), session.view(id(1)).game().decision());
        assertEquals(before.revision() + 1, session.view(id(1)).revision());
        String saved = McrCodec.saveSession(session);
        assertFalse(saved.contains("incarnation"));
        assertFalse(saved.contains("seated"));
        var first = McrCodec.restoreSession(saved);
        var second = McrCodec.restoreSession(saved);
        assertTrue(first.view(id(1)).paused());
        assertHidden(first.view(id(1)).game());
        first.synchronizeSeats(MOUNTS);
        second.synchronizeSeats(MOUNTS);
        assertNotEquals(first.incarnation(), second.incarnation());
        assertNotEquals(session.incarnation(), first.incarnation());
        assertEquals(first.view(id(1)).game().decision(), second.view(id(1)).game().decision());
        var request = first.view(id(1));
        int choice = index(request, WIN);
        assertFalse(second.act(id(1), TABLE, request.incarnation(), request.game().decision(), choice));
        assertTrue(second.view(id(2)).game().responded());
        assertTrue(second.view(id(2)).game().actions().isEmpty());
        assertEquals(before.game().actions(), second.view(id(1)).game().actions());
        play(second, 3, MELDED_KONG);
        play(second, 1, WIN);
        assertEquals(1, ((McrSettlement.Win) second.view(id(1)).game().result()).winner());
        assertTrue(second.view(id(1)).canConfirmNextHand());
        var otherMatch = McrSession.start(TABLE, ROSTER, 711, Tile.mcrSet());
        assertNotEquals(session.incarnation(), otherMatch.incarnation());
    }

    @Test void wrongWinAndHandAcknowledgementsPersistWithoutPayingOrAdvancingTwice() {
        var session = session(fixed(5, new McrGameTest.Fixture().hand(0, "12345m567p789s11z6m").build()));
        play(session, 0, WIN);
        var after = session.view(id(0));
        assertEquals(-30, after.game().seats().get(0).points());
        assertTrue(after.game().seats().get(0).winForbidden());
        assertTrue(after.game().actions().stream().noneMatch(action -> action.type() == WIN));
        session = McrCodec.restoreSession(McrCodec.saveSession(session));
        session.synchronizeSeats(MOUNTS);
        for (int steps = 0; steps < 600 && session.view(null).game().phase() != McrGame.Phase.HAND_END; steps++) {
            var state = session.view(null).game();
            if (state.phase() == McrGame.Phase.REACTION) {
                for (int seat = 0; seat < 4; seat++) {
                    var view = session.view(id(seat));
                    if (view.game().phase() == McrGame.Phase.REACTION && !view.game().actions().isEmpty()) play(session, seat, PASS);
                }
            } else play(session, state.turn(), switch (state.phase()) {
                case DRAW -> DRAW;
                case INITIAL_FLOWERS, REPLACE_FLOWER -> REPLACE_FLOWER;
                default -> DISCARD;
            });
        }
        assertEquals(McrGame.Phase.HAND_END, session.view(null).game().phase());
        var end = session.view(id(0));
        assertTrue(session.requestExit(id(0)));
        assertFalse(session.confirmNextHand(id(0), TABLE, end.incarnation(), end.game().decision()),
            "A completed hand cannot advance during an exit vote");
        assertTrue(session.answerExit(id(1), session.roomView(id(1)).exitVote().id(), false));
        assertFalse(session.confirmNextHand(OUTSIDER, TABLE, end.incarnation(), end.game().decision()));
        for (int seat = 0; seat < 3; seat++) {
            assertTrue(session.confirmNextHand(id(seat), TABLE, end.incarnation(), end.game().decision()));
            assertFalse(session.confirmNextHand(id(seat), TABLE, end.incarnation(), end.game().decision()));
            assertEquals(1, session.view(id(seat)).game().handNumber());
            assertFalse(session.view(id(seat)).canConfirmNextHand());
        }
        session = McrCodec.restoreSession(McrCodec.saveSession(session));
        assertFalse(session.view(id(3)).canConfirmNextHand());
        session.synchronizeSeats(MOUNTS);
        assertEquals(7, session.view(id(3)).confirmed());
        assertTrue(session.view(id(3)).canConfirmNextHand());
        assertFalse(session.confirmNextHand(id(3), TABLE, end.incarnation(), end.game().decision()));
        var current = session.view(id(3));
        assertTrue(session.confirmNextHand(id(3), TABLE, current.incarnation(), current.game().decision()));
        var next = session.view(id(3));
        assertEquals(2, next.game().handNumber());
        assertEquals(0, next.confirmed());
        assertEquals(-30, next.game().seats().get(0).points());
        assertFalse(next.game().seats().get(0).winForbidden());
        assertEquals(1, next.game().penalties().size());
        assertFalse(session.confirmNextHand(id(3), TABLE, current.incarnation(), current.game().decision()));
    }

    @Test void privateAndRecipientEnvelopesStaySeparateAndValidateRosterIdentity() {
        var session = McrSession.start(TABLE, ROSTER, 711, Tile.mcrSet());
        session.synchronizeSeats(MOUNTS);
        for (UUID viewer : new UUID[]{id(0), id(1), OUTSIDER, null}) {
            var view = session.view(viewer);
            assertEquals(view, McrCodec.decodeSessionView(McrCodec.encodeSessionView(view)));
            assertThrows(IllegalArgumentException.class, () -> McrCodec.restoreSession(McrCodec.encodeSessionView(view)));
        }
        String saved = McrCodec.saveSession(session);
        assertThrows(IllegalArgumentException.class, () -> McrCodec.decodeSessionView(saved));
        for (String invalid : new String[]{"1-1-1-1-1", "not-a-uuid"}) {
            var json = JsonParser.parseString(saved).getAsJsonObject();
            json.getAsJsonObject("room").addProperty("tableId", invalid);
            assertThrows(IllegalArgumentException.class, () -> McrCodec.restoreSession(json.toString()));
        }
        var duplicate = JsonParser.parseString(saved).getAsJsonObject();
        duplicate.getAsJsonObject("room").getAsJsonArray("participants").set(3,
            duplicate.getAsJsonObject("room").getAsJsonArray("participants").get(0));
        assertThrows(IllegalArgumentException.class, () -> McrCodec.restoreSession(duplicate.toString()));
        var missing = JsonParser.parseString(saved).getAsJsonObject();
        missing.remove("confirmed");
        assertThrows(IllegalArgumentException.class, () -> McrCodec.restoreSession(missing.toString()));
        assertEquals(saved, McrCodec.saveSession(session));
    }

    private static McrSession session(McrGame game) {
        return session(game, TimeControl.DEFAULT);
    }

    private static McrSession session(McrGame game, TimeControl control) {
        var seating = new RoomSeating();
        seating.positioned(4);
        var room = new TableSession.State(TABLE, MahjongVariant.MCR, 4, ROSTER.getFirst().id(),
            ROSTER, seating.save(), TableSession.Lifecycle.PLAYING, 1, 1, 711, false, null, null, 0, 0);
        var clocks = java.util.Collections.nCopies(4, new TimeControl.Clock(control.moveSeconds() * 20, control.reserveSeconds() * 20, false));
        var session = McrSession.restore(new McrSession.State(McrSession.State.FORMAT, room, Tile.mcrSet(), 0,
            control, clocks, 0, game.save(), null, null, List.of()));
        session.synchronizeSeats(MOUNTS);
        return session;
    }

    private static UUID id(int seat) { return ROSTER.get(seat).id(); }

    private static void tick(McrSession session, int ticks) {
        for (int i = 0; i < ticks; i++) session.tick();
    }

    @Test void absentPlayersTimeOutSafelyAndReloadDoesNotReplenishTheirClock() {
        var session = session(fixed(5, new McrGameTest.Fixture().hand(0, "12345m567p789s11z6m").build()), new TimeControl(1, 1));
        assertTrue(index(session.view(id(0)), WIN) >= 0);
        int drawn = session.view(id(0)).game().seats().get(0).drawn();
        session.unseat(id(0));
        tick(session, 25);
        assertEquals(new TimeControl.Clock(0, 15, false), session.save().clocks().get(0));
        var restored = McrCodec.restoreSession(McrCodec.saveSession(session));
        tick(restored, 30); // No observed mounts after loading: explicitly paused.
        assertEquals(session.save().clocks(), restored.save().clocks());
        restored.synchronizeSeats(Map.of(id(1), 1), java.util.Set.of(id(1)));
        tick(restored, 14);
        assertEquals(McrGame.Phase.TURN, restored.view(id(1)).game().phase());
        restored.tick();
        var after = restored.save().game();
        assertEquals(drawn, after.players().get(0).river().getLast().tile());
        assertTrue(after.players().get(0).river().getLast().tsumogiri());
        assertTrue(after.penalties().isEmpty());
        assertNull(after.result());
    }

    @Test void pendingResponsesKeepIndependentTimeAcrossVotesAndRestore() {
        var game = fixed(4, new McrGameTest.Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "123456789p11s46m").hand(2, "123456789s22p46m")
            .hand(3, "555m123789m123p1z").build());
        McrGameTest.discardKind(game, 0, "5m");
        var session = session(game, new TimeControl(1, 1));
        tick(session, 25);
        play(session, 2, PASS);
        tick(session, 4);
        assertEquals(15, session.save().clocks().get(2).reserveTicks());
        assertEquals(11, session.save().clocks().get(1).reserveTicks());
        assertTrue(session.requestExit(id(0)));
        tick(session, 15);
        var restored = McrCodec.restoreSession(McrCodec.saveSession(session));
        restored.synchronizeSeats(MOUNTS);
        assertEquals(session.save().clocks(), restored.save().clocks());
        assertTrue(restored.view(id(2)).game().responded());
        assertTrue(restored.answerExit(id(1), restored.roomView(id(1)).exitVote().id(), false));
        restored.unseat(id(1));
        tick(restored, 10);
        assertEquals(McrGame.Phase.REACTION, restored.view(id(0)).game().phase());
        restored.tick();
        assertEquals(McrGame.Phase.DRAW, restored.view(id(0)).game().phase());
        assertNull(restored.view(id(0)).game().result());
        assertTrue(restored.view(id(0)).game().penalties().isEmpty());
        assertEquals(15, restored.save().clocks().get(2).reserveTicks());
        var beforeDraw = restored.save();
        tick(restored, McrSession.AUTO_ACTION_TICKS);
        assertEquals(beforeDraw.game().wall().tiles().stream().filter(tile -> tile >= 0).count() - 1,
            restored.save().game().wall().tiles().stream().filter(tile -> tile >= 0).count());
        assertEquals(beforeDraw.clocks(), restored.save().clocks(), "A forced draw spends no decision allowance");
    }

    @Test void claimedTurnDefaultsToLegalDiscardAndForcedActionsResumeExactlyOnce() {
        var game = fixed(4, new McrGameTest.Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "123456789p11s46m").hand(2, "123456789s22p46m")
            .hand(3, "555m123789m123p1z").build());
        McrGameTest.discardKind(game, 0, "5m");
        var session = session(game, new TimeControl(0, 1));
        play(session, 1, PASS); play(session, 2, PASS); play(session, 3, PUNG);
        var turn = session.view(id(3));
        assertEquals(Tile.ABSENT, turn.game().seats().get(3).drawn());
        int tile = turn.game().actions().get(index(turn, DISCARD)).tiles().getFirst();
        tick(session, 20);
        assertEquals(tile, session.save().game().players().get(3).river().getLast().tile());

        var flowers = McrSession.start(TABLE, ROSTER, 711, Tile.mcrSet());
        flowers.synchronizeSeats(MOUNTS);
        assertEquals(McrGame.Phase.INITIAL_FLOWERS, flowers.save().game().phase());
        tick(flowers, McrSession.AUTO_ACTION_TICKS - 1);
        var before = flowers.save();
        var restored = McrCodec.restoreSession(McrCodec.saveSession(flowers));
        restored.synchronizeSeats(Map.of(id(3), 3));
        restored.tick();
        flowers.tick();
        assertEquals(flowers.save().game().wall(), restored.save().game().wall());
        assertEquals(flowers.save().game().players(), restored.save().game().players());
        assertNotEquals(before.game().wall(), restored.save().game().wall());
        assertEquals(before.clocks(), restored.save().clocks());
    }

    @Test void absentConfirmationHasFiniteSavedDeadlineAndNewHandRestoresReserve() {
        var session = session(fixed(4, new McrGameTest.Fixture().hand(0, "111222333m444p11z").build()), new TimeControl(1, 1));
        tick(session, 23);
        play(session, 0, WIN);
        var end = session.view(id(0));
        assertEquals(McrGame.Phase.HAND_END, end.game().phase());
        for (int seat = 0; seat < 3; seat++)
            assertTrue(session.confirmNextHand(id(seat), TABLE, end.incarnation(), end.game().decision()));
        session.unseat(id(3));
        tick(session, McrSession.SETTLEMENT_TICKS - 1);
        var restored = McrCodec.restoreSession(McrCodec.saveSession(session));
        restored.synchronizeSeats(Map.of(id(0), 0));
        assertEquals(1, restored.view(id(0)).settlementTicks());
        restored.tick();
        assertEquals(2, restored.view(id(0)).game().handNumber());
        assertEquals(0, restored.view(id(0)).confirmed());
        assertTrue(restored.save().clocks().stream().allMatch(clock -> clock.reserveTicks() == 20));
        assertFalse(restored.confirmNextHand(id(0), TABLE, end.incarnation(), end.game().decision()));
    }

    @Test void onlyLobbyHostConfiguresPersistentClockAndReadinessIsCleared() {
        var session = new McrSession(TABLE, 1);
        session.join(id(0), "Host", 0); session.join(id(1), "Guest", 1);
        session.participants[1].ready = true;
        var clock = new TimeControl(3, 2);
        assertFalse(session.configureClock(id(1), clock));
        assertTrue(session.configureClock(id(0), clock));
        assertFalse(session.participants[1].ready);
        assertEquals(clock, McrCodec.restoreSession(McrCodec.saveSession(session)).timeControl());
        var active = session(new McrGame(711));
        assertFalse(active.configureClock(id(0), clock));
        var invalid = JsonParser.parseString(McrCodec.saveSession(active)).getAsJsonObject();
        invalid.getAsJsonArray("clocks").get(0).getAsJsonObject().addProperty("moveTicks", -1);
        assertThrows(IllegalArgumentException.class, () -> McrCodec.restoreSession(invalid.toString()));
    }

    private static int index(McrSession.View view, McrAction.Type type) {
        var actions = view.game().actions();
        for (int i = 0; i < actions.size(); i++) if (actions.get(i).type() == type) return i;
        throw new AssertionError("Missing " + type + " in " + actions);
    }

    private static void play(McrSession session, int seat, McrAction.Type type) {
        var view = session.view(id(seat));
        assertTrue(session.act(id(seat), view.tableId(), view.incarnation(), view.game().decision(), index(view, type)));
    }

    private static void assertHidden(McrView view) {
        assertEquals(-1, view.viewerSeat());
        assertTrue(view.actions().isEmpty());
        view.seats().forEach(seat -> assertTrue(seat.hand().stream().allMatch(tile -> tile == Tile.HIDDEN)));
    }
}
