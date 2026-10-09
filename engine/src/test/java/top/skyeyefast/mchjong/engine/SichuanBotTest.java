package top.skyeyefast.mchjong.engine;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.SichuanAction.Type.*;

class SichuanBotTest {
    private static final UUID HUMAN = new UUID(72, 1);
    private static final String WAIT = "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 9p";

    @Test void voidingPreservesGroupsAndSecretPhysicalChoiceAcrossRestore() {
        for (var preset : SichuanPreset.values()) {
            var game = opening(preset, "1m 1m 1m 2m 3m 1p 1p 2p 3p 4p 1s 4s 7s 9s");
            var choice = selected(game, 0);
            assertEquals(VOID_SUIT, choice.type());
            assertEquals(2, choice.suit());
            assertEquals(preset.config().selectFirstDiscard() ? 1 : 0, choice.tiles().size());
            assertTrue(game.act(0, game.decision(), SichuanBot.choose(game.view(0))));
            var spectator = SichuanCodec.decodeView(SichuanCodec.encodeView(game.view(-1)));
            assertEquals(-1, spectator.seats().get(0).voidSuit());
            assertEquals(Tile.ABSENT, spectator.seats().get(0).firstDiscard());
            assertEquals(-1, SichuanBot.choose(spectator));
            game = SichuanCodec.restore(SichuanCodec.save(game));
            for (int seat = 1; seat < 4; seat++) assertTrue(game.act(seat, game.decision(), SichuanBot.choose(game.view(seat))));
            var discard = selected(game, 0);
            assertEquals(DISCARD, discard.type());
            assertEquals(2, Tile.kind(discard.tiles().getFirst()) / 9);
            if (preset.config().selectFirstDiscard()) assertEquals(choice.tiles(), discard.tiles());
            assertTrue(game.act(0, game.decision(), SichuanBot.choose(game.view(0))));
            game.validate();
        }
        var heavenly = opening(SichuanPreset.SBR_2025, "1m 2m 3m 4m 5m 6m 7m 8m 9m 1p 2p 3p 4p 5p");
        assertEquals(new SichuanAction(VOID_SUIT, List.of(), 2), selected(heavenly, 0));
    }

    @Test void quadPairsShareTheAnalyzerAndDeadCopiesDoNotImproveTheHand() {
        var stock = new ArrayList<>(Tile.sichuanSet());
        var hand = take(stock, "1m 1m 1m 1m 2m 2m 3m 3m 4p 4p 5p 5p 6p");
        var progress = SichuanHandAnalyzer.analyze(hand, List.of(), 2, hand);
        assertEquals(0, progress.shanten());
        assertTrue(progress.effectiveKinds().contains(Tile.parseKind("6p")));
        assertTrue(SichuanHandAnalyzer.readyValue(hand, List.of(), 2, SichuanPreset.SBR_2025.config()) > 0);
        var visible = new ArrayList<>(hand);
        visible.addAll(stock.stream().filter(tile -> Tile.kind(tile) == Tile.parseKind("6p")).toList());
        var exhausted = SichuanHandAnalyzer.analyze(hand, List.of(), 2, visible);
        assertFalse(exhausted.effectiveKinds().contains(Tile.parseKind("6p")));
        assertTrue(exhausted.remainingCount() < progress.remainingCount());
        assertEquals(exhausted, SichuanHandAnalyzer.analyze(hand, List.of(), 2, visible.stream().flatMap(tile -> java.util.stream.Stream.of(tile, tile)).toList()));
        var game = turn("1m 1m 1m 1m 2m 2m 3m 3m 4p 4p 5p 5p 6p 9p");
        assertEquals(Tile.parseKind("9p"), Tile.kind(selected(game, 0).tiles().getFirst()));
    }

    @Test void callsCompareTheMandatoryDiscardAndKongsPreserveProgress() {
        var ready = turn("9p", WAIT);
        discard(ready, "9p");
        assertFalse(ready.actions(1).stream().anyMatch(action -> action.type() == PUNG));
        assertEquals(WIN, selected(ready, 1).type());
        var equal = turn("5m", "5m 5m 1m 2m 3m 7m 8m 9m 1p 2p 3p 6p 8p");
        discard(equal, "5m");
        assertTrue(equal.actions(1).stream().anyMatch(action -> action.type() == PUNG));
        assertEquals(PASS, selected(equal, 1).type());
        var advance = turn("5m", "5m 5m 1m 2m 3m 1p 2p 3p 7p 8p 4p 4p 9m");
        discard(advance, "5m");
        assertEquals(PUNG, selected(advance, 1).type());
        var kong = turn("9m 9m 9m 9m 1m 2m 3m 4m 5m 6m 1p 2p 3p 5p");
        assertTrue(kong.actions(0).stream().anyMatch(action -> action.type() == CONCEALED_KONG));
        assertEquals(CONCEALED_KONG, selected(kong, 0).type());
        var pairs = turn("1m 1m 1m 1m 2m 2m 3m 3m 4p 4p 5p 5p 6p 9p");
        assertEquals(DISCARD, selected(pairs, 0).type(), "A kong must not destroy the quad-pair route");
    }

    @Test void sevenPairsRetainPairsAcrossAFasterOrdinaryDiscardAndRejectPungs() {
        var game = turn("7p 7p 8p 8p 3p 3p 1m 1m 9p 9p 5p 6m 4p 5m");
        int kind = Tile.kind(selected(game, 0).tiles().getFirst());
        var hand = game.view(0).seats().getFirst().hand();
        assertEquals(1, hand.stream().filter(tile -> Tile.kind(tile) == kind).count(), "Keep the five pairs");
        var kept = new ArrayList<>(hand); kept.remove(selected(game, 0).tiles().getFirst());
        assertEquals(1, SichuanHandAnalyzer.analyze(kept, List.of(), 2, hand).shanten());
        assertEquals(0, hand.stream().map(tile -> {
            var rest = new ArrayList<>(hand); rest.remove(tile);
            return SichuanHandAnalyzer.analyze(rest, List.of(), 2, hand).shanten();
        }).mapToInt(Integer::intValue).min().orElseThrow());
        var view = game.view(0);
        var seats = new ArrayList<>(view.seats());
        var other = seats.get(1);
        var deadKinds = List.of("4p", "5p", "5m", "6m").stream().map(Tile::parseKind).toList();
        var dead = Tile.sichuanSet().stream().filter(tile -> deadKinds.contains(Tile.kind(tile)) && !hand.contains(tile))
            .map(tile -> new SichuanPlayerState.Discard(tile, false)).toList();
        seats.set(1, new SichuanView.Seat(other.hand(), other.melds(), dead, other.voidSuit(), other.won(), other.drawn(), other.firstDiscard()));
        var exhausted = new SichuanView(view.revision(), view.decision(), view.rules(), view.phase(), view.handNumber(), view.dealer(), view.scores(),
            view.viewerSeat(), view.turn(), view.wall(), seats, view.focus(), view.supplier(), view.robbingKong(), view.submitted(),
            view.actions(), view.winners(), view.ledger(), view.result(), view.passedFan());
        int exhaustedChoice = Tile.kind(exhausted.actions().get(SichuanBot.choose(exhausted)).tiles().getFirst());
        assertEquals(2, hand.stream().filter(tile -> Tile.kind(tile) == exhaustedChoice).count(),
            "With every pair-making single exhausted, abandon the slower seven-pairs route");
        var reaction = turn("1m", "7p 7p 8p 8p 3p 3p 1m 1m 9p 9p 5p 6m 4p");
        discard(reaction, "1m");
        assertTrue(reaction.actions(1).stream().anyMatch(action -> action.type() == PUNG));
        assertEquals(PASS, selected(reaction, 1).type());
    }

    @Test void allPungsRetainPairsAndAcceptUsefulPungs() {
        var game = turn("1m 1m 1m 2m 2m 4m 4m 5m 6m 7p 7p 9p 9p 8p");
        int kind = Tile.kind(selected(game, 0).tiles().getFirst());
        assertTrue(List.of("5m", "6m", "8p").stream().map(Tile::parseKind).toList().contains(kind));
        var reaction = turn("2m", "2m 2m 1m 1m 1m 4m 4m 4m 7m 7m 7m 5p 9p");
        discard(reaction, "2m");
        assertEquals(PUNG, selected(reaction, 1).type());
    }

    @Test void nearlyPureHandsRetainTheirSuitAndRespectFixedMelds() {
        var game = turn("1m 2m 3m 4m 5m 6m 7m 8m 9m 2m 2m 5m 6p 7p");
        assertEquals(1, Tile.kind(selected(game, 0).tiles().getFirst()) / 9);
        var purePairs = turn("1m 1m 2m 2m 3m 3m 5m 5m 7m 7m 4m 6m 8p 9p");
        assertEquals(1, Tile.kind(selected(purePairs, 0).tiles().getFirst()) / 9);
        var reaction = turn("2m", "2m 2m 1m 3m 4m 5m 6m 7m 8m 9m 5m 6p 8p");
        discard(reaction, "2m");
        assertEquals(PUNG, selected(reaction, 1).type());
        assertTrue(reaction.act(1, reaction.decision(), SichuanBot.choose(reaction.view(1))));
        for (int seat = 2; seat < 4; seat++) if (!reaction.actions(seat).isEmpty())
            assertTrue(reaction.act(seat, reaction.decision(), reaction.actions(seat).indexOf(new SichuanAction(PASS))));
        assertEquals(1, Tile.kind(selected(reaction, 1).tiles().getFirst()) / 9);
    }

    @Test void cappedValueStopsBuyingExtraRoutes() {
        var game = turn("7p 7p 8p 8p 3p 3p 1m 1m 9p 9p 5p 6m 4p 5m");
        var noFan = withRules(game.view(0), SichuanRuleOption.FAN_CAP.with(game.rules(), 0));
        var hand = new ArrayList<>(noFan.seats().getFirst().hand());
        hand.remove(noFan.actions().get(SichuanBot.choose(noFan)).tiles().getFirst());
        assertEquals(0, SichuanHandAnalyzer.analyze(hand, List.of(), 2, noFan.seats().getFirst().hand()).shanten());

        var root = turn("9m 9m 9m 9m 7m 8m 1m 2m 3m 4m 4m 5m 6p 7p");
        var capped = withRules(root.view(0), SichuanRuleOption.FAN_CAP.with(root.rules(), 1));
        var choice = capped.actions().get(SichuanBot.choose(capped));
        assertEquals(DISCARD, choice.type());
        assertEquals(Tile.parseKind("5m"), Tile.kind(choice.tiles().getFirst()), "The retained root caps payout; keep the fast mixed-suit wait");
    }

    @Test void kongPaymentsCannotBuyWorseProgressEvenAtTheirMaximum() {
        var game = turn("1m 1m 1m 1m 2m 2m 3m 3m 4p 4p 5p 5p 6p 9p");
        var rich = SichuanRuleOption.CONCEALED_KONG_PAYMENT.with(game.rules(), 8);
        rich = SichuanRuleOption.DISCARD_KONG_PAYMENT.with(rich, 8);
        rich = SichuanRuleOption.ADDED_KONG_PAYMENT.with(rich, 8);
        var richView = withRules(game.view(0), rich);
        assertEquals(DISCARD, richView.actions().get(SichuanBot.choose(richView)).type());
    }

    @Test @Timeout(5) void dangerUsesEachVoidSuitCappedPaymentsAndRetiredWinners() {
        var game = turn("1m 2m 3m 1p 2p 3p 7p 8p 9p 3m 4m 6m 5p 5p");
        var original = game.view(0);
        var seats = new ArrayList<>(original.seats());
        var other = seats.get(1);
        var melds = new ArrayList<Meld>();
        for (int kind = 6; kind <= 8; kind++) melds.add(new Meld(Meld.Type.TRIPLET, List.of(kind * 4, kind * 4 + 1, kind * 4 + 2), 0, kind * 4));
        var river = List.of(new SichuanPlayerState.Discard(Tile.parseKind("3m") * 4 + 2, false));
        seats.set(1, new SichuanView.Seat(Collections.nCopies(4, Tile.HIDDEN), melds, river, 1, false, Tile.ABSENT, Tile.ABSENT));
        var slots = new ArrayList<>(Collections.nCopies(108, Tile.ABSENT));
        for (int i = 0; i < 12; i++) slots.set(i, Tile.HIDDEN);
        var wall = new SichuanView.Wall(slots, original.wall().dealer(), original.wall().die1(), original.wall().die2(), original.wall().eastWestLongWall());
        var threatened = new SichuanView(original.revision(), original.decision(), original.rules(), original.phase(), original.handNumber(), original.dealer(), original.scores(),
            0, original.turn(), wall, seats, original.focus(), original.supplier(), original.robbingKong(), false, original.actions(), original.winners(), original.ledger(), null, original.passedFan());
        var known = new HashSet<>(game.view(0).seats().getFirst().hand());
        known.addAll(melds.stream().flatMap(meld -> meld.tiles().stream()).toList()); known.add(river.getFirst().tile());
        var risk = ChineseBotDanger.sichuan(threatened, known);
        assertEquals(0, risk.against(1, Tile.parseKind("5p")), "Opponent's void suit cannot win");
        assertTrue(risk.against(1, Tile.parseKind("3m")) > 0, "Sichuan repeats are not furiten-safe");
        assertTrue(risk.against(1, 5) > risk.against(2, 5));
        var early = new SichuanView(threatened.revision(), threatened.decision(), threatened.rules(), threatened.phase(), threatened.handNumber(), threatened.dealer(), threatened.scores(),
            0, threatened.turn(), original.wall(), seats, threatened.focus(), threatened.supplier(), false, false, threatened.actions(), threatened.winners(), threatened.ledger(), null, threatened.passedFan());
        assertTrue(risk.against(1, 5) > ChineseBotDanger.sichuan(early, known).against(1, 5), "Short walls increase active-opponent threat");
        assertTrue(risk.against(1, 5) > ChineseBotDanger.sichuan(withRules(threatened, SichuanRuleOption.FAN_CAP.with(game.rules(), 0)), known).against(1, 5));
        assertEquals(Tile.parseKind("3m"), Tile.kind(threatened.actions().get(SichuanBot.choose(threatened)).tiles().getFirst()));
        seats.set(1, new SichuanView.Seat(Collections.nCopies(4, Tile.HIDDEN), melds, river, 1, true, Tile.ABSENT, Tile.ABSENT));
        var retired = new SichuanView(threatened.revision(), threatened.decision(), threatened.rules(), threatened.phase(), threatened.handNumber(), threatened.dealer(), threatened.scores(),
            0, threatened.turn(), wall, seats, threatened.focus(), threatened.supplier(), false, false, threatened.actions(), threatened.winners(), threatened.ledger(), null, threatened.passedFan());
        assertEquals(0, ChineseBotDanger.sichuan(retired, known).against(1, 5));
        assertEquals(Tile.parseKind("6m"), Tile.kind(retired.actions().get(SichuanBot.choose(retired)).tiles().getFirst()));
    }

    @Test void hiddenHandsAndFutureWallCannotChangeTheChoice() {
        var game = turn("1m 2m 3m 1p 2p 3p 7p 8p 9p 3m 4m 6m 5p 5p");
        var original = SichuanCodec.restore(SichuanCodec.save(game)).view(0);
        var json = JsonParser.parseString(SichuanCodec.save(game)).getAsJsonObject();
        var slots = json.getAsJsonObject("wall").getAsJsonArray("slots");
        var opponent = json.getAsJsonArray("players").get(1).getAsJsonObject().getAsJsonArray("hand");
        for (int slot = 0; slot < slots.size(); slot++) if (slots.get(slot).getAsInt() >= 0) {
            var tile = slots.get(slot); slots.set(slot, opponent.get(0)); opponent.set(0, tile); break;
        }
        var altered = SichuanCodec.restore(json.toString()).view(0);
        assertEquals(original, altered);
        assertEquals(SichuanBot.choose(original), SichuanBot.choose(altered));
        assertEquals(original, SichuanCodec.decodeView(SichuanCodec.encodeView(original)));
    }

    @Test void sharedRoomControlsAndWorldPolicyOwnTheRoster() {
        var session = lobby(SichuanPreset.SBR_2025);
        room(session, RoomAction.Type.SET_BOT);
        room(session, RoomAction.Type.REMOVE_BOT);
        room(session, RoomAction.Type.FILL_BOTS);
        assertEquals(3, session.participants().stream().filter(TableParticipant::bot).count());
        assertTrue(session.participants().stream().filter(TableParticipant::bot).allMatch(TableParticipant::ready));
        assertTrue(session.roomActions(HUMAN).stream().noneMatch(action -> action.type() == RoomAction.Type.SET_BOT));
        session = SichuanCodec.restoreSession(SichuanCodec.saveSession(session));
        session.synchronizeSeats(Map.of(HUMAN, 0));
        session.configureWorld(noBots());
        assertEquals(0, session.participants().stream().filter(TableParticipant::bot).count());
        assertTrue(session.roomActions(HUMAN).stream().noneMatch(action -> action.type() == RoomAction.Type.FILL_BOTS || action.type() == RoomAction.Type.SET_BOT));
        session.configureWorld(WorldPolicy.DEFAULT);
        room(session, RoomAction.Type.FILL_BOTS); start(session);
        session.configureWorld(noBots());
        assertEquals(3, session.participants().stream().filter(TableParticipant::bot).count());
        assertTrue(session.requestExit(HUMAN));
        assertTrue(session.lobby());
    }

    @Test void botMultiWinRepliesKeepDelayClocksAndConfirmationsThroughRestore() {
        var game = turn("9p", WAIT, WAIT, WAIT);
        discard(game, "9p");
        var session = active(game, 1);
        for (int tick = 0; tick < 6; tick++) session.tick();
        var clocks = session.save().clocks();
        assertTrue(session.view(HUMAN).clocks().stream().noneMatch(TimeControl.Clock::active));
        session = SichuanCodec.restoreSession(SichuanCodec.saveSession(session));
        for (int tick = 0; tick < 20; tick++) session.tick();
        assertEquals(6, session.save().age());
        session.synchronizeSeats(Map.of(HUMAN, 0));
        for (int tick = 0; tick < 5; tick++) session.tick();
        assertEquals(SichuanGame.Phase.REACTION, session.game().phase());
        session.tick();
        assertEquals(List.of(1, 2, 3), session.game().result().wins().stream().map(SichuanSettlement.Win::seat).toList());
        assertEquals(clocks, session.save().clocks());
        assertEquals(14, session.view(HUMAN).confirmed());
        session = SichuanCodec.restoreSession(SichuanCodec.saveSession(session));
        session.synchronizeSeats(Map.of(HUMAN, 0));
        var end = session.view(HUMAN);
        assertTrue(session.confirmNextHand(HUMAN, session.tableId(), end.incarnation(), end.game().decision()));
        assertEquals(2, session.game().handNumber());
        assertFalse(session.confirmNextHand(HUMAN, session.tableId(), end.incarnation(), end.game().decision()));
        assertEquals(0, session.view(HUMAN).confirmed());
    }

    @Test void votesFreezeBotSchedulingAcrossRestore() {
        var session = active(turn("1m 2m 3m 1p 2p 3p 7p 8p 9p 3m 4m 6m 5p 5p"), 2);
        UUID other = session.participants().get(1).id();
        session.synchronizeSeats(Map.of(HUMAN, 0, other, 1));
        assertTrue(session.requestExit(HUMAN));
        session = SichuanCodec.restoreSession(SichuanCodec.saveSession(session));
        session.synchronizeSeats(Map.of(HUMAN, 0, other, 1));
        for (int tick = 0; tick < 12; tick++) session.tick();
        assertEquals(0, session.save().age());
        assertTrue(session.answerExit(other, session.roomView(other).exitVote().id(), false));
        session.tick(); assertEquals(1, session.save().age());
    }

    // Two full matches plus replay reconstruction need headroom on shared CI runners.
    @Test @Timeout(180) void oneHumanThreeBotsFinishEightHandsWithReplayAndRestorationForBothPresets() {
        for (var preset : SichuanPreset.values()) {
            var session = lobby(preset);
            room(session, RoomAction.Type.FILL_BOTS); start(session);
            int ended = 0;
            boolean midHandRestore = false;
            for (int tick = 0; tick < 50_000 && session.lifecycle() != TableSession.Lifecycle.FINISHED; tick++) {
                var view = session.view(HUMAN);
                if (!midHandRestore && tick >= 30) {
                    session = SichuanCodec.restoreSession(SichuanCodec.saveSession(session));
                    assertTrue(session.paused());
                    session.synchronizeSeats(Map.of(HUMAN, session.seatOf(HUMAN)));
                    midHandRestore = true; view = session.view(HUMAN);
                }
                if (view.game().phase() == SichuanGame.Phase.HAND_END && view.game().handNumber() != ended) {
                    ended = view.game().handNumber();
                    assertEquals(3, view.confirmedCount());
                    assertEquals(ended, session.save().replay().handCount());
                    session = SichuanCodec.restoreSession(SichuanCodec.saveSession(session));
                    session.synchronizeSeats(Map.of(HUMAN, session.seatOf(HUMAN)));
                    view = session.view(HUMAN);
                    if (ended % 2 == 1) assertTrue(session.confirmNextHand(HUMAN, session.tableId(), view.incarnation(), view.game().decision()));
                } else {
                    // The human uses only issued void/discard/pass/win requests; ticks own Bot and forced-draw actions.
                    for (int index = 0; index < view.game().actions().size(); index++) {
                        var type = view.game().actions().get(index).type();
                        if (type == VOID_SUIT || type == DISCARD || type == PASS || type == WIN) {
                            assertTrue(session.act(HUMAN, session.tableId(), view.incarnation(), view.game().decision(), index)); break;
                        }
                    }
                }
                session.tick();
            }
            assertEquals(TableSession.Lifecycle.FINISHED, session.lifecycle());
            assertEquals(8, session.game().completedHands().size());
            session.game().validate();
            var replay = session.pendingReplays().getFirst();
            assertTrue(replay.complete()); assertEquals(8, replay.handCount());
            assertEquals(3, replay.participants().stream().filter(ReplayMatch.Participant::bot).count());
            assertEquals(session.game().scores(), replay.sichuan().hands().getLast().finalPoints());
            assertEquals(session.game().scores().stream().map(Integer::doubleValue).toList(), replay.header().finalScores());
            assertEquals(4, replay.header().finalRanks().size());
            ReplayCodec.validate(replay);
            var restored = SichuanCodec.restoreSession(SichuanCodec.saveSession(session));
            assertEquals(session.game().result(), restored.game().result());
            assertEquals(session.game().scores(), restored.game().scores());
            assertEquals(List.of(replay), restored.pendingReplays());
            restored.acknowledgeReplay(replay.id());
            restored = SichuanCodec.restoreSession(SichuanCodec.saveSession(restored));
            restored.synchronizeSeats(Map.of(HUMAN, restored.seatOf(HUMAN)));
            if (preset == SichuanPreset.TFMJ_2024) restored.configureWorld(noBots());
            room(restored, RoomAction.Type.RETURN_TO_LOBBY);
            assertTrue(restored.pendingReplays().isEmpty());
            assertEquals(preset == SichuanPreset.TFMJ_2024 ? 0 : 3, restored.participants().stream().filter(TableParticipant::bot).count());
        }
    }

    private static SichuanView withRules(SichuanView view, SichuanRules rules) {
        return new SichuanView(view.revision(), view.decision(), rules, view.phase(), view.handNumber(), view.dealer(), view.scores(),
            view.viewerSeat(), view.turn(), view.wall(), view.seats(), view.focus(), view.supplier(), view.robbingKong(), view.submitted(),
            view.actions(), view.winners(), view.ledger(), view.result(), view.passedFan());
    }
    private static SichuanAction selected(SichuanGame game, int seat) {
        int index = SichuanBot.choose(game.view(seat));
        assertTrue(index >= 0 && index < game.actions(seat).size());
        assertEquals(index, SichuanBot.choose(game.view(seat)));
        return game.actions(seat).get(index);
    }
    private static void discard(SichuanGame game, String tile) {
        var actions = game.actions(0);
        int index = java.util.stream.IntStream.range(0, actions.size()).filter(i -> actions.get(i).type() == DISCARD
            && Tile.kind(actions.get(i).tiles().getFirst()) == Tile.parseKind(tile)).findFirst().orElseThrow();
        assertTrue(game.act(0, game.decision(), index));
    }
    private static SichuanSession lobby(SichuanPreset preset) {
        var session = new SichuanSession(new UUID(72, 0), 711);
        session.configureEquipment(false, Tile.sichuanSet()); session.join(HUMAN, "Human", 0);
        session.configureRules(HUMAN, session.decision(), preset.config()); return session;
    }
    private static void room(SichuanSession session, RoomAction.Type type) {
        var action = session.roomActions(HUMAN).stream().filter(candidate -> candidate.type() == type).findFirst().orElseThrow();
        assertTrue(session.actRoom(HUMAN, session.decision(), action));
    }
    private static void start(SichuanSession session) {
        room(session, RoomAction.Type.BEGIN_SEATING);
        session.synchronizeSeats(Map.of(HUMAN, session.seatOf(HUMAN))); room(session, RoomAction.Type.READY);
    }
    private static WorldPolicy noBots() { return new WorldPolicy(true, false, true, 5000, true, false, true, true, null); }
    private static SichuanSession active(SichuanGame game, int humans) {
        var roster = new ArrayList<TableParticipant>();
        for (int seat = 0; seat < 4; seat++) roster.add(new TableParticipant(seat == 0 ? HUMAN : new UUID(72, seat + 1),
            "Seat " + seat, seat >= humans, false, BotDifficulty.EASY, null, true));
        var seating = new RoomSeating(); seating.positioned(4);
        var room = new TableSession.State(new UUID(72, 0), MahjongVariant.SICHUAN, 4, HUMAN, roster, seating.save(),
            TableSession.Lifecycle.PLAYING, 1, 1, 711, false, null, null, 0, 0, false, java.util.Collections.nCopies(4, MatchAutomation.DEFAULT));
        var control = TimeControl.DEFAULT;
        var clocks = Collections.nCopies(4, new TimeControl.Clock(control.moveSeconds() * 20, control.reserveSeconds() * 20, false));
        var session = SichuanSession.restore(new SichuanSession.State(SichuanSession.State.FORMAT, room, game.rules(), Tile.sichuanSet(),
            control, clocks, 0, 0, game.save(), null, null, List.of()));
        session.synchronizeSeats(Map.of(HUMAN, 0)); return session;
    }
    private static SichuanGame turn(String... hands) {
        var game = opening(SichuanPreset.TFMJ_2024, hands);
        for (int seat = 0; seat < 4; seat++) {
            var action = new SichuanAction(VOID_SUIT, List.of(), 2);
            assertTrue(game.act(seat, game.decision(), game.actions(seat).indexOf(action)));
        }
        return game;
    }
    private static SichuanGame opening(SichuanPreset preset, String... hands) {
        var stock = new ArrayList<>(Tile.sichuanSet());
        var desired = new ArrayList<List<Integer>>();
        for (int seat = 0; seat < 4; seat++) desired.add(take(stock, seat < hands.length ? hands[seat] : ""));
        for (int seat = 0; seat < 4; seat++) while (desired.get(seat).size() < (seat == 0 ? 14 : 13)) desired.get(seat).add(stock.removeFirst());
        var source = new SichuanGame(12, preset.config(), Tile.sichuanSet()).initialOpening();
        var slots = new ArrayList<>(source.slots());
        var order = SichuanWallLayout.traversal(0, source.die1(), source.die2(), source.eastWestLongWall());
        for (int seat = 0; seat < 4; seat++) for (int index = 0; index < desired.get(seat).size(); index++) {
            int raw = index < 12 ? (index / 4) * 16 + seat * 4 + index % 4 : seat == 0 ? (index == 12 ? 48 : 52) : 48 + seat;
            Collections.swap(slots, order.get(raw), slots.indexOf(desired.get(seat).get(index)));
        }
        return SichuanGame.replayHand(preset.config(), List.of(), new SichuanWall.State(slots, 0, source.die1(), source.die2(), 0, source.eastWestLongWall()));
    }
    private static List<Integer> take(ArrayList<Integer> stock, String notation) {
        var hand = new ArrayList<Integer>();
        if (!notation.isBlank()) for (String text : notation.split(" ")) {
            int kind = Tile.parseKind(text);
            int tile = stock.stream().filter(id -> Tile.kind(id) == kind).findFirst().orElseThrow();
            hand.add(tile); stock.remove(Integer.valueOf(tile));
        }
        return hand;
    }
}
