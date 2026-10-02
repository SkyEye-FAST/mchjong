package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.SichuanAction.Type.*;

@Timeout(30)
class SichuanReplayTest {
    private static final String WAIT = "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 9p";
    private static final UUID TABLE = new UUID(71, 1);
    private static UUID id(int seat) { return new UUID(72, seat + 1); }

    @Test void milSecretFirstDiscardAndLastKongRefundReplayWithExactRuleNames() {
        var opening = openingWithDraws(SichuanPreset.SBR_2025.config(), List.of(), "9m 9m 9m 9m 8m 8m 8m 8m 9p", WAIT, WAIT, WAIT).initialOpening();
        var game = SichuanGame.replayHand(SichuanPreset.SBR_2025.config(), List.of(), opening);
        var recorder = new SichuanReplayRecorder(game);
        for (int seat = 0; seat < 4; seat++) {
            var options = game.actions(seat);
            int selected = -1;
            for (int index = 0; index < options.size(); index++) if (options.get(index).suit() == (seat == 0 ? 1 : 2)) {
                if (seat != 0 || options.get(index).tiles().stream().anyMatch(tile -> Tile.kind(tile) == Tile.parseKind("9p"))) {
                    selected = index; break;
                }
            }
            accept(game, recorder, seat, selected);
            if (seat == 0) {
                var reconstructed = SichuanReplayPlayback.reconstruct(game.rules(), List.of(), recorder.save());
                assertEquals(game.save().players(), reconstructed.save().players());
                assertEquals(Tile.ABSENT, reconstructed.view(-1).seats().get(0).firstDiscard());
            }
        }
        int firstDiscard = game.save().players().get(0).firstDiscard();
        choose(game, recorder, 0, CONCEALED_KONG, "9m"); choose(game, recorder, 0, DRAW, null);
        choose(game, recorder, 0, CONCEALED_KONG, "8m"); choose(game, recorder, 0, DRAW, null);
        choose(game, recorder, 0, DISCARD, "9p");
        for (int seat = 1; seat < 4; seat++) choose(game, recorder, seat, WIN, null);
        var hand = recorder.finish(game);
        assertEquals(firstDiscard, hand.decisions().getFirst().options().get(hand.decisions().getFirst().selected()).tiles().getFirst());
        assertEquals(List.of(3, 4, 5), hand.result().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND)
            .map(SichuanSettlement.Entry::relatedEntry).toList());
        assertTrue(hand.result().ledger().stream().noneMatch(SichuanSettlement.Entry::kongTransfer));
        var match = match(game.rules(), List.of(hand), false);
        var restored = ReplayCodec.decode(ReplayCodec.encode(match), ReplayMatch.class);
        var timeline = SichuanReplayPlayback.timeline(restored, 0);
        assertEquals(firstDiscard, timeline.frames().getLast().seats().get(0).firstDiscard());
        assertEquals(hand.result(), timeline.frames().getLast().state().result());
        assertPayments(hand);
    }

    @Test void sealedPhysicalOpeningMultiRonAndDealerChainSurviveSerializationAndRecorderRestore() {
        var game = opening("9p", WAIT, WAIT, WAIT);
        var recorder = new SichuanReplayRecorder(game);
        voidAll(game, recorder);
        choose(game, recorder, 0, DISCARD, "9p");
        choose(game, recorder, 2, WIN, null);
        var saved = game.save();
        game = SichuanCodec.restore(SichuanCodec.save(game));
        recorder = new SichuanReplayRecorder(recorder.save());
        assertEquals(saved.players(), game.save().players());
        choose(game, recorder, 3, WIN, null); choose(game, recorder, 1, WIN, null);
        var first = recorder.finish(game);
        assertEquals(List.of(1, 2, 3), first.result().wins().stream().map(SichuanSettlement.Win::seat).toList());
        assertEquals(3, first.events().stream().filter(event -> event.kind() == SichuanReplayHand.Kind.WIN).count());
        assertTrue(game.nextHand());
        var secondRecorder = new SichuanReplayRecorder(game);
        drive(game, secondRecorder, true);
        var second = secondRecorder.finish(game);
        assertEquals(first.finalPoints(), second.initialPoints());
        assertEquals(first.result().nextDealer(first.opening().dealer()), second.opening().dealer());
        var match = match(game.rules(), List.of(first, second), false);
        var encoded = ReplayCodec.encode(match);
        assertFalse(encoded.contains("Seed")); assertFalse(encoded.contains("\"frames\""));
        var restored = ReplayCodec.decode(encoded, ReplayMatch.class);
        assertEquals(match, restored);
        assertEquals(match.header(), ReplayCodec.decode(ReplayCodec.encode(match.header()), ReplayMatch.Header.class));
        for (int index = 0; index < 2; index++) {
            var hand = restored.sichuan().hands().get(index);
            var timeline = SichuanReplayPlayback.timeline(restored, index);
            assertEquals(hand.events().size() + 1, timeline.frames().size());
            assertEquals(hand.result(), timeline.frames().getLast().state().result());
            assertEquals(hand.finalPoints(), timeline.frames().getLast().scores());
            assertEquals(hand.opening().dealer(), timeline.frames().getFirst().state().wall().dealer());
            assertTrue(timeline.frames().getFirst().seats().stream().allMatch(seat -> seat.hand().stream().allMatch(tile -> tile >= 0)));
        }
        assertTrue(game.view(0).seats().get(1).hand().stream().allMatch(tile -> tile >= 0));
    }

    @Test void consecutiveKongsAndCallTransfersReplayAllCommittedPayments() {
        var game = opening("9m 9m 9m 9m 8m 8m 8m 8m 9p", WAIT, WAIT, WAIT);
        var recorder = new SichuanReplayRecorder(game);
        voidAll(game, recorder);
        choose(game, recorder, 0, CONCEALED_KONG, "9m"); choose(game, recorder, 0, DRAW, null);
        choose(game, recorder, 0, CONCEALED_KONG, "8m"); choose(game, recorder, 0, DRAW, null);
        choose(game, recorder, 0, DISCARD, "9p");
        choose(game, recorder, 1, WIN, null); choose(game, recorder, 2, WIN, null); choose(game, recorder, 3, WIN, null);
        var hand = recorder.finish(game);
        assertEquals(2, hand.events().stream().filter(event -> event.kind() == SichuanReplayHand.Kind.KONG).count());
        assertEquals(6, hand.result().ledger().stream().filter(SichuanSettlement.Entry::kong).count());
        assertTrue(hand.result().ledger().stream().anyMatch(entry -> entry.type() == SichuanSettlement.Type.KONG_TRANSFER));
        assertPayments(hand);
        assertEquals(hand.result(), SichuanReplayPlayback.timeline(match(game.rules(), List.of(hand), false), 0).frames().getLast().state().result());
    }

    @Test void pungThenRobbedAddedKongRetiresWinnersAndPreservesPhysicalAliases() {
        String robbery = "1m 2m 3m 4m 5m 6m 7m 8m 1p 2p 3p 9p 9p";
        var game = openingWithDraws(List.of("7p", "6p", "6s", "6s", "9m"),
            "9m 9m 1m 2m 3m 4p 5p 6p 7p 8p 5p 1p 1p 8p",
            "9m 1m 2m 3m 4m 5m 6m 7m 8m 2p 3p 4p 6p", robbery, robbery);
        var recorder = new SichuanReplayRecorder(game);
        voidAll(game, recorder);
        choose(game, recorder, 0, DISCARD, "8p"); passAll(game, recorder);
        choose(game, recorder, 1, DRAW, null); choose(game, recorder, 1, DISCARD, "9m");
        choose(game, recorder, 0, PUNG, null); passAll(game, recorder);
        choose(game, recorder, 0, DISCARD, "8p"); passAll(game, recorder);
        for (int seat = 1; seat < 4; seat++) {
            choose(game, recorder, seat, DRAW, null);
            var drawn = game.save().players().get(seat).drawn();
            accept(game, recorder, seat, game.actions(seat).indexOf(new SichuanAction(DISCARD, List.of(drawn))));
            passAll(game, recorder);
        }
        choose(game, recorder, 0, DRAW, null); choose(game, recorder, 0, ADDED_KONG, null);
        choose(game, recorder, 2, WIN, null); choose(game, recorder, 3, WIN, null); passAll(game, recorder);
        assertTrue(game.actions(2).isEmpty()); assertTrue(game.actions(3).isEmpty());
        assertTrue(game.save().wins().stream().allMatch(SichuanSettlement.Win::robbingKong));
        assertEquals(Meld.Type.TRIPLET, game.save().players().get(0).melds().getFirst().type());
        drive(game, recorder, true);
        var hand = recorder.finish(game);
        var timeline = SichuanReplayPlayback.timeline(match(game.rules(), List.of(hand), false), 0);
        assertEquals(hand.result(), timeline.frames().getLast().state().result());
        assertEquals(1, hand.events().stream().filter(event -> event.kind() == SichuanReplayHand.Kind.PUNG).count());
        assertEquals(0, hand.events().stream().filter(event -> event.kind() == SichuanReplayHand.Kind.KONG).count());
        assertPayments(hand);
    }

    @Test void exhaustiveReadyChecksAndSequentialWinnersUseTheSameLedgerAndSafeLiveViews() {
        var game = openingWithDraws(List.of("9p"), "9p", WAIT, WAIT, "");
        var recorder = new SichuanReplayRecorder(game);
        voidAll(game, recorder);
        choose(game, recorder, 0, DISCARD, "9p"); choose(game, recorder, 1, WIN, null); passAll(game, recorder);
        assertEquals(2, game.turn());
        assertTrue(game.view(0).seats().get(1).hand().stream().allMatch(tile -> tile == Tile.HIDDEN));
        choose(game, recorder, 2, DRAW, null); choose(game, recorder, 2, WIN, null);
        assertEquals(3, game.turn());
        assertTrue(game.actions(1).isEmpty());
        assertTrue(game.save().ledger().stream().noneMatch(entry -> entry.type() == SichuanSettlement.Type.SELF_DRAW_WIN && entry.payer() == 1));
        drive(game, recorder, false);
        var hand = recorder.finish(game);
        assertTrue(hand.result().exhaustive()); assertPayments(hand);
        assertEquals(List.of(1, 2), hand.result().wins().stream().map(SichuanSettlement.Win::seat).toList());
        ReplayCodec.validate(match(game.rules(), List.of(hand), false));

        game = opening("1m 1m 2m 4m 5m 7m 9m 1p 2p 4p 5p 7p 8p 9p", WAIT,
            "1s 1s 1s 1s 2s 2s 2s 2s 3s 3s 3s 3s 4s", "");
        recorder = new SichuanReplayRecorder(game);
        drive(game, recorder, false);
        hand = recorder.finish(game);
        assertEquals(SichuanSettlement.DrawStatus.READY, hand.result().drawStatus().get(1));
        assertEquals(SichuanSettlement.DrawStatus.PASSIVE_FLOWER_PIG, hand.result().drawStatus().get(2));
        assertTrue(hand.result().ledger().stream().anyMatch(entry -> entry.type() == SichuanSettlement.Type.READY_PAYMENT
            && entry.payer() == 0 && entry.recipient() == 1));
        assertPayments(hand);
        ReplayCodec.validate(match(game.rules(), List.of(hand), false));
    }

    @Test void sessionKeepsSealedHandsUntilMatchEndAndNeverRequeuesAcknowledgedArchives() {
        var session = new SichuanSession(TABLE, 711);
        for (int seat = 0; seat < 4; seat++) assertTrue(session.join(id(seat), "Seat " + seat, seat));
        session.seating.positioned(4);
        session.configureEquipment(false, Tile.sichuanSet());
        var base = session.rules();
        session.configureRules(id(0), session.decision(), new SichuanRules(base.fanCap(), base.selfDrawBonus(), base.concealedKongPayment(),
            base.discardKongPayment(), base.addedKongPayment(), base.activeFlowerPigPenalty(), base.transferKongOnShoot(), base.refundKongWhenNotReady(), 2, base.separateKongFan(), base.selectFirstDiscard(), base.addedKongAfterKongIsShoot(), base.eastWestLongWall()));
        session.startMatch(); seated(session);
        for (int number = 1; number <= 2; number++) {
            for (int step = 0; step < 1000 && !session.game().ended(); step++) {
                if (step == 7) { session = SichuanCodec.restoreSession(SichuanCodec.saveSession(session)); seated(session); }
                for (int seat = 0; seat < 4; seat++) {
                    var offered = session.game().actions(seat);
                    if (offered.isEmpty()) continue;
                    assertTrue(session.act(id(seat), TABLE, session.incarnation(), session.game().decision(), selected(session.game(), seat, true)));
                    break;
                }
            }
            assertTrue(session.game().ended());
            assertEquals(number, session.save().replay().handCount());
            assertEquals(number == 2 ? 1 : 0, session.pendingReplays().size());
            session = SichuanCodec.restoreSession(SichuanCodec.saveSession(session)); seated(session);
            if (number == 1) for (int seat = 0; seat < 4; seat++)
                assertTrue(session.confirmNextHand(id(seat), TABLE, session.incarnation(), session.game().decision()));
        }
        var archive = session.pendingReplays().getFirst();
        assertTrue(archive.complete()); assertEquals(session.game().scores(), archive.sichuan().hands().getLast().finalPoints());
        ReplayCodec.validate(archive);
        assertEquals(archive.sichuan().hands().getLast().finalPoints().stream().map(Integer::doubleValue).toList(), archive.header().finalScores());
        session.acknowledgeReplay(archive.id());
        session = SichuanCodec.restoreSession(SichuanCodec.saveSession(session));
        session.tick(); session.clearMatch();
        assertTrue(session.pendingReplays().isEmpty());
    }

    @Test void malformedOrTamperedReplayCannotBeDecodedOrPlayed() {
        var game = opening("9p", WAIT, WAIT, WAIT); var recorder = new SichuanReplayRecorder(game);
        voidAll(game, recorder); choose(game, recorder, 0, DISCARD, "9p");
        for (int seat = 1; seat < 4; seat++) choose(game, recorder, seat, WIN, null);
        var match = match(game.rules(), List.of(recorder.finish(game)), false);
        String encoded = ReplayCodec.encode(match);
        for (String invalid : List.of(encoded.replace("\"complete\":false", "\"complete\":\"false\""),
            encoded.replace("\"complete\":false,", ""), encoded.replace("\"complete\":false", "\"complete\":false,\"complete\":true"),
            encoded.replace("\"mcr\":null", "\"mcr\":{\"hands\":[]}")))
            assertThrows(IllegalArgumentException.class, () -> ReplayCodec.decode(invalid, ReplayMatch.class));
        var tree = com.google.gson.JsonParser.parseString(encoded).getAsJsonObject();
        var hand = tree.getAsJsonObject("sichuan").getAsJsonArray("hands").get(0).getAsJsonObject();
        hand.getAsJsonArray("events").get(0).getAsJsonObject().addProperty("seat", 3);
        var changed = ReplayCodec.decode(tree.toString(), ReplayMatch.class);
        assertThrows(IllegalArgumentException.class, () -> ReplayCodec.validate(changed));
        var session = com.google.gson.JsonParser.parseString(SichuanCodec.saveSession(new SichuanSession(TABLE, 711))).getAsJsonObject();
        session.getAsJsonArray("archiveQueue").add(tree);
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.restoreSession(session.toString()));
        hand.getAsJsonArray("initialHands").get(0).getAsJsonArray().set(0, new com.google.gson.JsonPrimitive(107));
        assertThrows(IllegalArgumentException.class, () -> ReplayCodec.validate(ReplayCodec.decode(tree.toString(), ReplayMatch.class)));
        var missing = com.google.gson.JsonParser.parseString(encoded).getAsJsonObject();
        missing.getAsJsonObject("sichuan").getAsJsonArray("hands").get(0).getAsJsonObject().getAsJsonArray("events").remove(0);
        assertThrows(IllegalArgumentException.class, () -> ReplayCodec.validate(ReplayCodec.decode(missing.toString(), ReplayMatch.class)));
        var points = com.google.gson.JsonParser.parseString(encoded).getAsJsonObject();
        points.getAsJsonObject("sichuan").getAsJsonArray("hands").get(0).getAsJsonObject().getAsJsonArray("initialPoints")
            .set(0, new com.google.gson.JsonPrimitive(1));
        assertThrows(IllegalArgumentException.class, () -> ReplayCodec.decode(points.toString(), ReplayMatch.class));
        var header = com.google.gson.JsonParser.parseString(ReplayCodec.encode(match.header())).getAsJsonObject();
        header.getAsJsonArray("finalRanks").add(1);
        assertThrows(IllegalArgumentException.class, () -> ReplayCodec.decode(header.toString(), ReplayMatch.Header.class));
    }

    private static void seated(SichuanSession session) {
        session.synchronizeSeats(Map.of(id(0), 0, id(1), 1, id(2), 2, id(3), 3), java.util.Set.of(id(0), id(1), id(2), id(3)));
    }
    private static void assertPayments(SichuanReplayHand hand) {
        assertEquals(hand.result().ledger().stream().map(SichuanSettlement.Entry::id).toList(), hand.events().stream()
            .filter(event -> event.kind() == SichuanReplayHand.Kind.PAYMENT).map(SichuanReplayHand.Event::ledgerId).toList());
    }
    private static ReplayMatch match(SichuanRules rules, List<SichuanReplayHand> hands, boolean complete) {
        var players = java.util.stream.IntStream.range(0, 4).mapToObj(seat -> new ReplayMatch.Participant(id(seat), "Seat " + seat, false)).toList();
        return new ReplayMatch(UUID.randomUUID(), TABLE, 1, 2, players, MahjongVariant.SICHUAN, complete, null, null, new SichuanReplay(rules, hands));
    }
    private static void voidAll(SichuanGame game, SichuanReplayRecorder recorder) {
        for (int seat = 0; seat < 4; seat++) {
            var actions = game.actions(seat);
            int selected = java.util.stream.IntStream.range(0, actions.size()).filter(index -> actions.get(index).suit() == 2).findFirst().orElseThrow();
            accept(game, recorder, seat, selected);
        }
    }
    private static void choose(SichuanGame game, SichuanReplayRecorder recorder, int seat, SichuanAction.Type type, String kind) {
        var actions = game.actions(seat);
        var action = actions.stream().filter(candidate -> candidate.type() == type && (kind == null
            || !candidate.tiles().isEmpty() && Tile.kind(candidate.tiles().getFirst()) == Tile.parseKind(kind))).findFirst().orElseThrow();
        accept(game, recorder, seat, actions.indexOf(action));
    }
    private static void accept(SichuanGame game, SichuanReplayRecorder recorder, int seat, int selected) {
        var before = game.save(); var options = game.actions(seat);
        assertTrue(game.act(seat, game.decision(), selected)); recorder.accepted(before, seat, options, selected, game);
    }
    private static void passAll(SichuanGame game, SichuanReplayRecorder recorder) {
        for (int seat = 0; seat < 4 && game.phase() == SichuanGame.Phase.REACTION; seat++)
            if (!game.actions(seat).isEmpty()) choose(game, recorder, seat, PASS, null);
    }
    private static int selected(SichuanGame game, int seat, boolean win) {
        var actions = game.actions(seat);
        for (int index = 0; index < actions.size(); index++) if (win && actions.get(index).type() == WIN) return index;
        for (int index = 0; index < actions.size(); index++) if (actions.get(index).type() == PASS) return index;
        var drawn = game.save().players().get(seat).drawn();
        for (int index = 0; index < actions.size(); index++) if (actions.get(index).type() == DISCARD && actions.get(index).tiles().contains(drawn)) return index;
        for (int index = 0; index < actions.size(); index++) if (actions.get(index).type() == VOID_SUIT && actions.get(index).suit() == 2) return index;
        for (int index = 0; index < actions.size(); index++) if (actions.get(index).type() == DRAW || actions.get(index).type() == DISCARD) return index;
        throw new IllegalStateException("No replay test decision");
    }
    private static void drive(SichuanGame game, SichuanReplayRecorder recorder, boolean win) {
        for (int step = 0; step < 1000 && !game.ended(); step++) for (int seat = 0; seat < 4; seat++)
            if (!game.actions(seat).isEmpty()) { accept(game, recorder, seat, selected(game, seat, win)); break; }
        assertTrue(game.ended());
    }
    private static SichuanGame opening(String... hands) { return openingWithDraws(List.of(), hands); }
    private static SichuanGame openingWithDraws(List<String> draws, String... hands) {
        return openingWithDraws(SichuanPreset.TFMJ_2024.config(), draws, hands);
    }
    private static SichuanGame openingWithDraws(SichuanRules rules, List<String> draws, String... hands) {
        var stock = new ArrayList<>(Tile.sichuanSet());
        var selected = new ArrayList<List<Integer>>();
        for (String hand : hands) {
            var tiles = new ArrayList<Integer>();
            if (!hand.isBlank()) for (String kind : hand.split(" ")) tiles.add(take(stock, kind));
            selected.add(tiles);
        }
        var future = draws.stream().map(kind -> take(stock, kind)).toList();
        for (int seat = 0; seat < 4; seat++) while (selected.get(seat).size() < (seat == 0 ? 14 : 13)) selected.get(seat).add(stock.removeFirst());
        var slots = new ArrayList<>(Collections.nCopies(108, Tile.ABSENT));
        var order = SichuanWallLayout.traversal(0, 1, 1, rules.eastWestLongWall()); int[] used = new int[4];
        for (int packet = 0; packet < 3; packet++) for (int seat = 0; seat < 4; seat++) for (int tile = 0; tile < 4; tile++)
            slots.set(order.get(packet * 16 + seat * 4 + tile), selected.get(seat).get(used[seat]++));
        slots.set(order.get(48), selected.get(0).get(12)); slots.set(order.get(52), selected.get(0).get(13));
        for (int seat = 1; seat < 4; seat++) slots.set(order.get(48 + seat), selected.get(seat).get(12));
        var remaining = new ArrayList<>(future); remaining.addAll(stock);
        for (int index = 0; index < remaining.size(); index++) slots.set(order.get(53 + index), remaining.get(index));
        return SichuanGame.replayHand(rules, List.of(), new SichuanWall.State(slots, 0, 1, 1, 0, rules.eastWestLongWall()));
    }
    private static int take(List<Integer> stock, String kind) {
        int physical = stock.stream().filter(tile -> Tile.kind(tile) == Tile.parseKind(kind)).findFirst().orElseThrow();
        stock.remove(Integer.valueOf(physical)); return physical;
    }
}
