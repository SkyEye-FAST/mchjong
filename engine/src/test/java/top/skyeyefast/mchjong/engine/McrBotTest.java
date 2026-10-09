package top.skyeyefast.mchjong.engine;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.McrAction.Type.*;
import static top.skyeyefast.mchjong.engine.McrGameTest.*;

class McrBotTest {
    private static final UUID HUMAN = new UUID(71, 1);

    @Test void refusesLowFanWinsAndPrioritizesQualifiedWins() {
        var low = fixed(5, new Fixture().hand(0, "12345m567p789s11z6m").build());
        assertTrue(has(low, 0, WIN));
        assertFalse(low.view(0).qualifyingWin());
        assertEquals(DISCARD, choice(low, 0).type());
        assertTrue(low.act(0, low.decision(), McrBot.choose(low.view(0))));
        assertTrue(low.penalties().isEmpty());

        var claim = fixed(6, new Fixture().hand(0, "279m147p258s2345z8s")
            .hand(1, "445566m2277779s").at(53, Tile.parseKind("8s")).build());
        discardKind(claim, 0, "8s");
        assertTrue(has(claim, 1, WIN));
        assertEquals(PASS, choice(claim, 1).type());
        passAll(claim);
        play(claim, 1, DRAW);
        assertTrue(claim.view(1).qualifyingWin());
        assertEquals(WIN, choice(claim, 1).type());
    }

    @Test void discardsAnIsolatedHonorAndUsesStablePhysicalTieBreak() {
        var game = fixed(8, new Fixture().hand(0, "123m123p123s45s77z1z").build());
        var selected = choice(game, 0);
        assertEquals(DISCARD, selected.type());
        assertEquals(Tile.EAST, Tile.kind(selected.tiles().getFirst()));
        var tied = fixed(9, new Fixture().hand(0, "12345m567p789s11z6m").build());
        int tile = choice(tied, 0).tiles().getFirst();
        assertEquals(tied.hand(0).stream().filter(id -> Tile.kind(id) == Tile.kind(tile)).min(Integer::compare).orElseThrow(), tile);
    }

    @Test void evaluatesTheMandatoryDiscardBeforeCalling() {
        var ready = fixed(3, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "67m123789p789s11z").build());
        discardKind(ready, 0, "5m");
        assertTrue(has(ready, 1, CHOW));
        assertFalse(ready.view(1).qualifyingWin());
        assertEquals(CHOW, choice(ready, 1).type(), "A call can retain shanten and establish a qualifying wait");
        assertTrue(ready.act(1, ready.decision(), McrBot.choose(ready.view(1))));
        passAll(ready);
        assertEquals(Tile.parseKind("9s"), Tile.kind(choice(ready, 1).tiles().getFirst()));
        assertTrue(ready.act(1, ready.decision(), McrBot.choose(ready.view(1))));
        int six = Tile.parseKind("6s") * 4;
        while (ready.hand(1).contains(six)) six++;
        assertTrue(McrHandAnalyzer.score(ready.hand(1), ready.melds(1), 1, six,
            new McrWinContext(McrWinContext.Method.SELF_DRAW, Tile.SOUTH, Tile.EAST, false,
                McrWinContext.KongWin.NONE, false, 0)).meetsMinimum());
        var advancing = fixed(3, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "46m123789p12s112z").build());
        discardKind(advancing, 0, "5m");
        assertEquals(CHOW, choice(advancing, 1).type());
    }

    @Test void publicRemainingCopiesChangeThePreferredDiscard() {
        var game = fixed(8, new Fixture().hand(0, "123m123p123s346s11z").build());
        var view = game.view(0);
        assertEquals(Tile.parseKind("6s"), Tile.kind(choice(game, 0).tiles().getFirst()));
        var seats = new ArrayList<>(view.seats());
        var opponent = seats.get(1);
        var river = new ArrayList<McrDiscard>();
        int kind = Tile.parseKind("2s");
        for (int tile = kind * 4; tile < kind * 4 + 4; tile++)
            if (!game.hand(0).contains(tile)) river.add(new McrDiscard(tile, false, false));
        seats.set(1, new McrView.Seat(opponent.wind(), opponent.points(), opponent.hand(), opponent.drawn(),
            opponent.melds(), river, opponent.flowers(), opponent.winForbidden()));
        var visible = new McrView(view.revision(), view.decision(), view.handNumber(), view.phase(), view.viewerSeat(),
            view.dealer(), view.roundWind(), view.turn(), view.remaining(), view.opening(), view.wall(), view.focus(),
            seats, view.actions(), view.responded(), view.qualifyingWin(), view.result(), view.penalties());
        assertEquals(Tile.parseKind("3s"), Tile.kind(visible.actions().get(McrBot.choose(visible)).tiles().getFirst()));
    }

    @Test void kongsRequireGuaranteedEfficiencyOrFanProgress() {
        var concealed = fixed(14, new Fixture().hand(0, "1111m123456p78s22z")
            .tail(0, Tile.parseKind("9s")).build());
        assertTrue(has(concealed, 0, CONCEALED_KONG));
        assertEquals(CONCEALED_KONG, choice(concealed, 0).type());
        assertTrue(concealed.act(0, concealed.decision(), McrBot.choose(concealed.view(0))));
        assertTrue(concealed.view(0).qualifyingWin());
        assertEquals(WIN, choice(concealed, 0).type());
        var exposed = fixed(13, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "555m123456p78s11z").tail(0, Tile.parseKind("9s")).build());
        discardKind(exposed, 0, "5m");
        assertTrue(has(exposed, 1, MELDED_KONG));
        assertEquals(PASS, choice(exposed, 1).type());
        var added = addedKongPosition();
        assertEquals(DISCARD, choice(added, 1).type());
    }

    @Test @Timeout(5) void publicOpponentThreatTradesWidthForSaferQualifyingWaitsWithoutFuriten() {
        var game = fixed(8, new Fixture().hand(0, "123m123p123s346s11z").build());
        var original = game.view(0);
        assertEquals(Tile.parseKind("6s"), Tile.kind(choice(game, 0).tiles().getFirst()));
        var seats = new ArrayList<>(original.seats());
        var other = seats.get(1);
        var melds = new ArrayList<Meld>();
        for (int kind = 24; kind <= 26; kind++) melds.add(new Meld(Meld.Type.TRIPLET, List.of(kind * 4, kind * 4 + 1, kind * 4 + 2), 0, kind * 4));
        var river = List.of(new McrDiscard(Tile.parseKind("3s") * 4 + 2, false, false));
        seats.set(1, new McrView.Seat(other.wind(), other.points(), java.util.Collections.nCopies(4, Tile.HIDDEN), Tile.ABSENT, melds, river, List.of(), false));
        var wall = new ArrayList<>(java.util.Collections.nCopies(144, Tile.ABSENT));
        for (int i = 0; i < 12; i++) wall.set(i, Tile.HIDDEN);
        var threatened = new McrView(original.revision(), original.decision(), original.handNumber(), original.phase(), original.viewerSeat(),
            original.dealer(), original.roundWind(), original.turn(), 12, original.opening(), wall, original.focus(), seats,
            original.actions(), original.responded(), original.qualifyingWin(), original.result(), original.penalties());
        var known = new HashSet<>(game.hand(0));
        known.addAll(melds.stream().flatMap(meld -> meld.tiles().stream()).toList()); known.add(river.getFirst().tile());
        var risk = ChineseBotDanger.mcr(threatened, known);
        assertTrue(risk.against(1, Tile.parseKind("6s")) > risk.against(2, Tile.parseKind("6s")));
        assertTrue(risk.against(1, Tile.parseKind("3s")) > 0, "MCR repeats are not furiten-safe");
        var early = new McrView(threatened.revision(), threatened.decision(), threatened.handNumber(), threatened.phase(), 0, threatened.dealer(), threatened.roundWind(), threatened.turn(),
            original.remaining(), threatened.opening(), original.wall(), threatened.focus(), seats, threatened.actions(), false, threatened.qualifyingWin(), null, threatened.penalties());
        assertTrue(risk.against(1, 23) > ChineseBotDanger.mcr(early, known).against(1, 23), "Short walls increase public threat");
        var selected = threatened.actions().get(McrBot.choose(threatened)).tiles().getFirst();
        assertEquals(Tile.parseKind("3s"), Tile.kind(selected));
        var kept = new ArrayList<>(game.hand(0)); kept.remove(selected);
        assertEquals(0, McrHandAnalyzer.analyze(kept, List.of(), 0, known.stream().filter(tile -> !kept.contains(tile)).toList()).shanten(), "A live qualifying attack should keep readiness");
        var flowerSeat = seats.get(1);
        seats.set(1, new McrView.Seat(flowerSeat.wind(), flowerSeat.points(), flowerSeat.hand(), flowerSeat.drawn(), flowerSeat.melds(), flowerSeat.river(), List.of(136, 137, 138), false));
        var flowers = new McrView(threatened.revision(), threatened.decision(), threatened.handNumber(), threatened.phase(), 0, threatened.dealer(), threatened.roundWind(), threatened.turn(),
            threatened.remaining(), threatened.opening(), threatened.wall(), threatened.focus(), seats, threatened.actions(), false, threatened.qualifyingWin(), null, threatened.penalties());
        assertEquals(risk.against(1, 23) * 19 / 16, ChineseBotDanger.mcr(flowers, known).against(1, 23), 1e-9, "Flowers affect exposure, not minimum-fan qualification");
        var mixed = List.of(6, 15, 24).stream().map(kind -> new Meld(Meld.Type.SEQUENCE, List.of(kind * 4, (kind + 1) * 4, (kind + 2) * 4), 0, kind * 4)).toList();
        seats.set(1, new McrView.Seat(other.wind(), other.points(), java.util.Collections.nCopies(4, Tile.HIDDEN), Tile.ABSENT, mixed, river, List.of(), false));
        var mixedView = new McrView(threatened.revision(), threatened.decision(), threatened.handNumber(), threatened.phase(), 0, threatened.dealer(), threatened.roundWind(), threatened.turn(),
            threatened.remaining(), threatened.opening(), threatened.wall(), threatened.focus(), seats, threatened.actions(), false, threatened.qualifyingWin(), null, threatened.penalties());
        var mixedKnown = new HashSet<>(game.hand(0)); mixedKnown.add(river.getFirst().tile()); mixedKnown.addAll(mixed.stream().flatMap(meld -> meld.tiles().stream()).toList());
        assertTrue(ChineseBotDanger.mcr(mixedView, mixedKnown).against(1, Tile.parseKind("3s")) < risk.against(1, Tile.parseKind("3s")), "Unproven eight-fan routes get less exposure than a strong same-suit signal");
    }

    @Test void hiddenHandAndFutureWallPermutationsCannotAffectTheDecision() {
        var game = fixed(8, new Fixture().hand(0, "123m123p123s45s77z1z").build());
        var original = McrCodec.restore(McrCodec.save(game)).view(0);
        var json = JsonParser.parseString(McrCodec.save(game)).getAsJsonObject();
        var wall = json.getAsJsonObject("wall").getAsJsonArray("tiles");
        var opponent = json.getAsJsonArray("players").get(1).getAsJsonObject().getAsJsonArray("hand");
        int first = -1;
        for (int slot = 0; slot < wall.size(); slot++) if (wall.get(slot).getAsInt() >= 0 && wall.get(slot).getAsInt() < 136) {
            if (first < 0) {
                first = slot;
                var tile = wall.get(slot);
                wall.set(slot, opponent.get(0));
                opponent.set(0, tile);
            } else {
                var tile = wall.get(slot);
                wall.set(slot, wall.get(first));
                wall.set(first, tile);
                break;
            }
        }
        var altered = McrCodec.restore(json.toString()).view(0);
        assertEquals(original, altered);
        assertEquals(McrBot.choose(original), McrBot.choose(altered));
        assertEquals(original, McrCodec.decodeView(McrCodec.encodeView(original)));
        assertEquals(-1, McrBot.choose(game.view(-1)));
    }

    @Test void sharedLobbyFillsThreeBotsAndWorldPolicyRemovesThemBeforePlay() {
        var session = lobby();
        room(session, RoomAction.Type.FILL_BOTS);
        assertEquals(3, session.participants().stream().filter(TableParticipant::bot).count());
        assertTrue(session.roomActions(HUMAN).stream().noneMatch(action -> action.type() == RoomAction.Type.SET_BOT));
        session.configureWorld(new WorldPolicy(true, false, true, 5000, true, false, true, true, null));
        assertEquals(0, session.participants().stream().filter(TableParticipant::bot).count());
        assertTrue(session.roomActions(HUMAN).stream().noneMatch(action -> action.type() == RoomAction.Type.FILL_BOTS));
        session.configureWorld(WorldPolicy.DEFAULT);
        room(session, RoomAction.Type.FILL_BOTS);
        start(session);
        assertEquals(TableSession.Lifecycle.PLAYING, session.lifecycle());
        assertTrue(session.requestExit(HUMAN));
        assertTrue(session.lobby(), "Only the human votes on ending a one-human match");
    }

    @Test void simultaneousBotRepliesKeepTheirClocksAndResumeAfterSaving() {
        var game = fixed(4, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "123456789p11s46m").hand(2, "123456789s22p46m")
            .hand(3, "555m123789m123p1z").build());
        discardKind(game, 0, "5m");
        var session = active(game, 1);
        for (int i = 0; i < 6; i++) session.tick();
        var clocks = session.save().clocks();
        session = McrCodec.restoreSession(McrCodec.saveSession(session));
        long decision = session.view(null).game().decision();
        for (int i = 0; i < 20; i++) session.tick();
        assertEquals(decision, session.view(null).game().decision(), "No seated human pauses bots too");
        session.synchronizeSeats(Map.of(HUMAN, 0));
        for (int i = 0; i < 5; i++) session.tick();
        assertEquals(McrGame.Phase.REACTION, session.view(HUMAN).game().phase());
        session.tick();
        assertEquals(1, assertInstanceOf(McrSettlement.Win.class, session.view(HUMAN).game().result()).winner());
        assertEquals(clocks, session.save().clocks());
        assertEquals(14, session.view(HUMAN).confirmed());
        var end = session.view(HUMAN);
        assertTrue(session.confirmNextHand(HUMAN, session.tableId(), end.incarnation(), end.game().decision()));
        assertEquals(2, session.view(HUMAN).game().handNumber());
    }

    @Test void exitVoteFreezesBotsAndTheirDelayAcrossRestore() {
        var session = active(fixed(8, new Fixture().hand(0, "123m123p123s45s77z1z").build()), 2);
        UUID other = session.participants().get(1).id();
        session.synchronizeSeats(Map.of(HUMAN, 0, other, 1));
        assertTrue(session.requestExit(HUMAN));
        int age = session.save().age();
        session = McrCodec.restoreSession(McrCodec.saveSession(session));
        session.synchronizeSeats(Map.of(HUMAN, 0, other, 1));
        for (int i = 0; i < 12; i++) session.tick();
        assertEquals(age, session.save().age());
        assertTrue(session.answerExit(other, session.roomView(other).exitVote().id(), false));
        session.tick();
        assertEquals(age + 1, session.save().age());
    }

    // Full-match lifecycle coverage, not a hardware-dependent Bot speed benchmark.
    @Test @Timeout(120) void oneHumanAndThreeBotsFinishAllSixteenHands() {
        var session = lobby();
        room(session, RoomAction.Type.FILL_BOTS);
        start(session);
        int hands = 0;
        for (int tick = 0; tick < 100_000 && session.lifecycle() != TableSession.Lifecycle.FINISHED; tick++) {
            var view = session.view(HUMAN);
            hands = Math.max(hands, view.game().handNumber());
            // The test human only passes/discards. Session ticks own all forced actions and all bots.
            for (int index = 0; index < view.game().actions().size(); index++) {
                var type = view.game().actions().get(index).type();
                if (type == PASS || type == DISCARD) {
                    assertTrue(session.act(HUMAN, session.tableId(), view.incarnation(), view.game().decision(), index));
                    break;
                }
            }
            session.tick();
        }
        assertEquals(16, hands);
        assertEquals(TableSession.Lifecycle.FINISHED, session.lifecycle());
        assertTrue(session.view(HUMAN).game().penalties().isEmpty());
        assertEquals(0, session.view(HUMAN).game().seats().stream().mapToInt(McrView.Seat::points).sum());
        var restored = McrCodec.restoreSession(McrCodec.saveSession(session));
        assertEquals(session.view(HUMAN).game().result(), restored.view(null).game().result());
        assertEquals(TableSession.Lifecycle.FINISHED, restored.lifecycle());
    }

    private static McrAction choice(McrGame game, int seat) { return game.actions(seat).get(McrBot.choose(game.view(seat))); }

    private static McrSession lobby() {
        var session = new McrSession(new UUID(71, 0), 711);
        session.configureEquipment(false, Tile.standard144Set());
        assertTrue(session.join(HUMAN, "Human", 0));
        return session;
    }

    private static void room(McrSession session, RoomAction.Type type) {
        var action = session.roomActions(HUMAN).stream().filter(candidate -> candidate.type() == type).findFirst().orElseThrow();
        assertTrue(session.actRoom(HUMAN, session.decision(), action));
    }

    private static void start(McrSession session) {
        room(session, RoomAction.Type.BEGIN_SEATING);
        session.synchronizeSeats(Map.of(HUMAN, session.seatOf(HUMAN)));
        room(session, RoomAction.Type.READY);
    }

    private static McrSession active(McrGame game, int humans) {
        var roster = new ArrayList<TableParticipant>();
        for (int seat = 0; seat < 4; seat++) roster.add(new TableParticipant(seat == 0 ? HUMAN : new UUID(71, seat + 1),
            "Seat " + seat, seat >= humans, false, BotDifficulty.EASY, null, true));
        var seating = new RoomSeating();
        seating.positioned(4);
        var room = new TableSession.State(new UUID(71, 0), MahjongVariant.MCR, 4, HUMAN, roster, seating.save(),
            TableSession.Lifecycle.PLAYING, 1, 1, 711, false, null, null, 0, 0, false, java.util.Collections.nCopies(4, MatchAutomation.DEFAULT));
        var control = TimeControl.DEFAULT;
        var clocks = java.util.Collections.nCopies(4, new TimeControl.Clock(control.moveSeconds() * 20, control.reserveSeconds() * 20, false));
        var session = McrSession.restore(new McrSession.State(McrSession.State.FORMAT, room, Tile.standard144Set(), 0,
            control, clocks, 0, game.save(), null, null, java.util.List.of()));
        session.synchronizeSeats(Map.of(HUMAN, 0));
        return session;
    }
}
