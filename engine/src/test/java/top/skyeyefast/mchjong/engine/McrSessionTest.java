package top.skyeyefast.mchjong.engine;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.McrAction.Type.*;

class McrSessionTest {
    private static final UUID TABLE = new UUID(10, 1);
    private static final UUID OUTSIDER = new UUID(2, 1);
    private static final List<McrSession.Participant> ROSTER = List.of(
        new McrSession.Participant(new UUID(1, 1), "Same name"),
        new McrSession.Participant(new UUID(1, 2), "Same name"),
        new McrSession.Participant(new UUID(1, 3), "West"),
        new McrSession.Participant(new UUID(1, 4), "North"));
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
        assertThrows(UnsupportedOperationException.class, () -> session.save().participants().clear());
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

    @Test void pendingResponsesRestoreWithFreshIncarnationsAndNoAssumedPresence() {
        var game = new McrGame(4, new McrGameTest.Fixture().hand(0, "279m147p258s2345z5m")
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
        var session = session(new McrGame(5, new McrGameTest.Fixture().hand(0, "12345m567p789s11z6m").build()));
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
            } else play(session, state.turn(), state.phase() == McrGame.Phase.DRAW ? DRAW : DISCARD);
        }
        assertEquals(McrGame.Phase.HAND_END, session.view(null).game().phase());
        var end = session.view(id(0));
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
            json.addProperty("tableId", invalid);
            assertThrows(IllegalArgumentException.class, () -> McrCodec.restoreSession(json.toString()));
        }
        var duplicate = JsonParser.parseString(saved).getAsJsonObject();
        duplicate.getAsJsonArray("participants").set(3, duplicate.getAsJsonArray("participants").get(0));
        assertThrows(IllegalArgumentException.class, () -> McrCodec.restoreSession(duplicate.toString()));
        var missing = JsonParser.parseString(saved).getAsJsonObject();
        missing.remove("confirmed");
        assertThrows(IllegalArgumentException.class, () -> McrCodec.restoreSession(missing.toString()));
        assertEquals(saved, McrCodec.saveSession(session));
    }

    private static McrSession session(McrGame game) {
        var session = McrSession.restore(new McrSession.State(McrSession.State.FORMAT, TABLE, 1, ROSTER, 0, game.save()));
        session.synchronizeSeats(MOUNTS);
        return session;
    }

    private static UUID id(int seat) { return ROSTER.get(seat).id(); }

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
