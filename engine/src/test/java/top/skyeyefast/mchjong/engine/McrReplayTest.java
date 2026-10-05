package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.McrAction.Type.*;

class McrReplayTest {
    private static final UUID TABLE = new UUID(31, 1);
    private static UUID id(int seat) { return new UUID(32, seat + 1); }

    @Test @Timeout(20) void sealedHandRecordsPhysicalOpeningPenaltyAndReconstructsEveryEvent() {
        var game = McrGameTest.fixed(5, new McrGameTest.Fixture().hand(0, "12345m567p789s11z6m").build());
        var recorder = new McrReplayRecorder(game);
        choose(game, recorder, 0, WIN);
        assertTrue(game.winForbidden(0));
        driveToEnd(game, recorder);
        assertNotNull(game.result());
        var hand = recorder.finish(game);
        assertEquals(144, hand.wall().size());
        assertTrue(Tile.validStandard144Set(hand.wall()));
        assertEquals(14, hand.initialHands().get(0).size());
        assertEquals(1, hand.penalties().size());
        assertTrue(hand.events().stream().anyMatch(event -> event.kind() == McrReplayHand.Kind.WRONG_WIN));
        assertTrue(hand.events().stream().anyMatch(event -> event.kind() == McrReplayHand.Kind.DRAW));
        var match = match(hand);
        var json = new com.google.gson.Gson();
        var encoded = json.toJson(match);
        assertFalse(encoded.contains("\"seed\""));
        assertEquals(match, json.fromJson(encoded, ReplayMatch.class));
        assertTrue(match.permits(id(0)));
        assertFalse(match.permits(id(3)), "A bot never receives replay read permission");
        var timeline = McrReplayPlayback.timeline(json.fromJson(encoded, ReplayMatch.class), 0);
        assertEquals(hand.events().size() + 1, timeline.frames().size());
        assertEquals(hand.finalPoints(), timeline.frames().getLast().view().seats().stream().map(McrView.Seat::points).toList());
        assertFalse(timeline.frames().get(0).view().seats().get(1).hand().contains(Tile.HIDDEN),
            "An authorized archived replay reconstructs all hands");
        var live = McrGameTest.fixed(5, new McrGameTest.Fixture().hand(0, "12345m567p789s11z6m").build()).view(0);
        assertTrue(live.seats().get(1).hand().contains(Tile.HIDDEN), "Live views retain recipient filtering");
    }

    @Test @Timeout(20) void sessionSaveRetainsUnwrittenHandOnceAndRestoreContinuesCurrentRecorder() {
        var roster = List.of(new TableParticipant(id(0), "East"), new TableParticipant(id(1), "South"),
            new TableParticipant(id(2), "West"), new TableParticipant(id(3), "North"));
        var session = McrSession.start(TABLE, roster, 711, Tile.standard144Set());
        session.synchronizeSeats(Map.of(id(0), 0, id(1), 1, id(2), 2, id(3), 3));
        for (int step = 0; step < 650 && session.view(id(0)).game().result() == null; step++) {
            if (step == 10) {
                session = McrCodec.restoreSession(McrCodec.saveSession(session));
                session.synchronizeSeats(Map.of(id(0), 0, id(1), 1, id(2), 2, id(3), 3));
            }
            int seat = -1;
            for (int candidate = 0; candidate < 4; candidate++) if (!session.view(id(candidate)).game().actions().isEmpty()) {
                seat = candidate; break;
            }
            assertTrue(seat >= 0);
            var view = session.view(id(seat));
            var actions = view.game().actions();
            int selected = 0;
            for (int index = 0; index < actions.size(); index++) {
                var action = actions.get(index);
                if (action.type() == PASS || action.type() == DISCARD && action.tiles().get(0) == view.game().seats().get(seat).drawn()) {
                    selected = index; break;
                }
            }
            assertTrue(session.act(id(seat), TABLE, view.incarnation(), view.game().decision(), selected));
        }
        assertNotNull(session.view(id(0)).game().result());
        assertEquals(1, session.pendingReplays().size());
        var restored = McrCodec.restoreSession(McrCodec.saveSession(session));
        assertEquals(1, restored.pendingReplays().size());
        var archived = restored.pendingReplays().getFirst();
        assertEquals(1, archived.handCount());
        McrReplayPlayback.timeline(archived, 0);
        assertEquals(1, McrCodec.restoreSession(McrCodec.saveSession(restored)).pendingReplays().size());
        restored.acknowledgeReplay(archived.id());
        assertTrue(McrCodec.restoreSession(McrCodec.saveSession(restored)).pendingReplays().isEmpty());
    }

    @Test @Timeout(20) void secondHandPlaybackUsesItsOwnOpeningAndCarriedPoints() {
        var game = new McrGame(711);
        var firstRecorder = new McrReplayRecorder(game);
        driveToEnd(game, firstRecorder);
        var first = firstRecorder.finish(game);
        assertTrue(game.nextHand());
        var secondRecorder = new McrReplayRecorder(game);
        driveToEnd(game, secondRecorder);
        var second = secondRecorder.finish(game);
        assertEquals(first.finalPoints(), second.initialPoints());
        var archived = match(first).appendMcr(second, false);
        var timeline = McrReplayPlayback.timeline(archived, 1);
        assertEquals(2, timeline.frames().getFirst().view().handNumber());
        assertEquals(1, timeline.frames().getFirst().view().dealer());
        assertEquals(second.finalPoints(), timeline.frames().getLast().view().seats().stream().map(McrView.Seat::points).toList());
    }

    @Test void reactionDeclarationsRecordOnlyTheCommittedMeldAndBotIdentityStaysPrivate() {
        var game = McrGameTest.fixed(3, new McrGameTest.Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "46m123789p123s11z").hand(2, "55m234678s234p22z").build());
        var recorder = new McrReplayRecorder(game);
        int tile = game.hand(0).stream().filter(value -> Tile.kind(value) == Tile.parseKind("5m")).findFirst().orElseThrow();
        accept(game, recorder, 0, game.actions(0).indexOf(new McrAction(DISCARD, tile)));
        choose(game, recorder, 1, CHOW);
        choose(game, recorder, 2, PUNG);
        for (int seat = 0; seat < 4 && game.phase() == McrGame.Phase.REACTION; seat++)
            if (game.actions(seat).stream().anyMatch(action -> action.type() == PASS)) choose(game, recorder, seat, PASS);
        assertEquals(1, recorder.save().events().stream().filter(event -> event.kind() == McrReplayHand.Kind.PUNG).count());
        assertEquals(0, recorder.save().events().stream().filter(event -> event.kind() == McrReplayHand.Kind.CHOW).count());

        var roster = List.of(new TableParticipant(id(0), "East"), new TableParticipant(id(1), "South"),
            new TableParticipant(id(2), "West"), new TableParticipant(id(3), "Bot", true, false, BotDifficulty.EASY, null, true));
        var session = McrSession.start(TABLE, roster, 711, Tile.standard144Set());
        assertTrue(session.save().replay().participants().get(3).bot());
        assertFalse(session.save().replay().permits(id(3)));
    }

    private static ReplayMatch match(McrReplayHand hand) {
        var players = new ArrayList<ReplayMatch.Participant>();
        for (int seat = 0; seat < 4; seat++) players.add(new ReplayMatch.Participant(id(seat), "Seat " + seat, seat == 3));
        return new ReplayMatch(UUID.randomUUID(), TABLE, 1, 2, players, MahjongVariant.MCR, false,
            null, new McrReplay(List.of(hand)), null, null);
    }

    private static void choose(McrGame game, McrReplayRecorder recorder, int seat, McrAction.Type type) {
        var options = game.actions(seat);
        for (int index = 0; index < options.size(); index++) if (options.get(index).type() == type) {
            accept(game, recorder, seat, index); return;
        }
        fail("Missing MCR action " + type);
    }

    private static void accept(McrGame game, McrReplayRecorder recorder, int seat, int selected) {
        var options = game.actions(seat);
        var before = game.save();
        assertTrue(game.act(seat, game.decision(), selected));
        recorder.accepted(before, seat, options, selected, game);
    }

    private static void driveToEnd(McrGame game, McrReplayRecorder recorder) {
        for (int step = 0; step < 600 && game.result() == null; step++) {
            int seat = -1;
            for (int candidate = 0; candidate < 4; candidate++) if (!game.actions(candidate).isEmpty()) { seat = candidate; break; }
            assertTrue(seat >= 0);
            var options = game.actions(seat);
            int chosen = 0;
            for (int index = 0; index < options.size(); index++) {
                var action = options.get(index);
                if (action.type() == PASS || action.type() == DISCARD && action.tiles().get(0) == game.drawn(seat)) {
                    chosen = index; break;
                }
            }
            accept(game, recorder, seat, chosen);
        }
        assertNotNull(game.result(), "MCR hand did not settle");
    }
}
