package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.SichuanAction.Type.*;

class SichuanGameTest {
    private static final String WAIT = "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 9p";

    @Test void stockOpeningAndSealedVoiding() {
        assertEquals(108, Tile.sichuanSet().size());
        assertEquals(108, Tile.sichuanSet().stream().distinct().count());
        assertFalse(Tile.validSet(Tile.sichuanSet()));
        assertFalse(Tile.validSichuanSet(Tile.set(true, RedFives.NONE)));
        var game = new SichuanGame(12);
        assertEquals(List.of(14, 13, 13, 13), game.save().players().stream().map(player -> player.hand().size()).toList());
        assertEquals(55, game.view(-1).wall().remaining());
        long decision = game.decision();
        choose(game, 0, VOID_SUIT, 0);
        assertEquals(decision, game.decision());
        assertEquals(0, game.view(0).seats().get(0).voidSuit());
        assertEquals(-1, game.view(1).seats().get(0).voidSuit());
        var restored = SichuanCodec.restore(SichuanCodec.save(game));
        assertFalse(restored.act(1, decision, 0));
        choose(restored, 1, VOID_SUIT, 1); choose(restored, 2, VOID_SUIT, 2); choose(restored, 3, VOID_SUIT, 0);
        assertEquals(SichuanGame.Phase.TURN, restored.phase());
        assertEquals(0, restored.view(-1).seats().get(0).voidSuit());
        restored.validate();
    }

    @Test void voidSuitDiscardsAndCallsAreConstrainedAndThereIsNoChow() {
        var game = position(new int[]{0, 2, 2, 2}, false,
            "9m 9m 9m 9m 2m 3m 4m 5m 6m 7m 8m 8m 1p 1p", WAIT, WAIT, "");
        assertTrue(game.actions(0).stream().filter(action -> action.type() == DISCARD).allMatch(action -> Tile.kind(action.tiles().get(0)) < 9));
        assertTrue(game.actions(0).stream().noneMatch(action -> action.type() == WIN || action.type() == CONCEALED_KONG));
        choose(game, 0, DISCARD, Tile.parseKind("9m"));
        assertTrue(game.actions(1).stream().noneMatch(action -> action.type().name().contains("CHOW")));
        passAll(game);
        game.validate();
        var prohibited = position(new int[]{2, 0, 2, 2}, false, "1m", "1m 1m", "", "");
        choose(prohibited, 0, DISCARD, 0);
        assertTrue(prohibited.actions(1).stream().noneMatch(action -> action.type() == PUNG || action.type() == DISCARD_KONG || action.type() == WIN));
    }

    @Test void multipleWinsKeepOnePhysicalTileAndFinishAtThree() {
        var game = position(new int[]{2, 2, 2, 2}, false, "9p", WAIT, WAIT, WAIT);
        choose(game, 0, DISCARD, Tile.parseKind("9p"));
        long decision = game.decision();
        choose(game, 2, WIN, -1);
        assertEquals(decision, game.decision());
        var restored = SichuanCodec.restore(SichuanCodec.save(game));
        assertTrue(restored.actions(2).isEmpty());
        assertTrue(restored.view(2).submitted());
        choose(restored, 3, WIN, -1); choose(restored, 1, WIN, -1);
        assertEquals(SichuanGame.Phase.HAND_END, restored.phase());
        assertEquals(List.of(1, 2, 3), restored.result().wins().stream().map(SichuanSettlement.Win::seat).toList());
        assertEquals(List.of(13, 13, 13, 14), restored.save().players().stream().map(player -> player.hand().size()).toList());
        assertEquals(3, restored.result().ledger().size());
        assertEquals(0, restored.result().deltas().stream().mapToInt(Integer::intValue).sum());
        restored.validate();
        assertEquals(restored.result(), SichuanCodec.restore(SichuanCodec.save(restored)).result());
    }

    @Test void oneWinnerExitsAndBloodBattleContinuesAfterThatWinner() {
        var game = position(new int[]{2, 2, 2, 2}, false, "9p", WAIT, "", "");
        choose(game, 0, DISCARD, Tile.parseKind("9p"));
        choose(game, 1, WIN, -1); passAll(game);
        assertEquals(SichuanGame.Phase.TURN, game.phase());
        assertEquals(2, game.turn());
        assertTrue(game.actions(1).isEmpty());
        assertTrue(game.view(0).seats().get(1).hand().stream().allMatch(tile -> tile == Tile.HIDDEN));
        var saved = game.save();
        assertEquals(1, saved.wins().size());
        choose(game, 2, DRAW, -1);
        assertEquals(saved.wins(), game.save().wins());
        game.validate();
    }

    @Test void finalTileChecksPassiveFlowerPigsAndMaximumReadyValue() {
        var game = position(new int[]{2, 2, 2, 0}, true,
            "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 7p 8p", WAIT,
            "1m 2m 4m 5m 7m 8m 1p 2p 4p 5p 7p 8p 8p", "1m 2m 3m 4m 5m 6m 7m 8m 9m 1p 2p 3p 4p");
        choose(game, 0, DISCARD, Tile.parseKind("8p")); passAll(game);
        assertEquals(SichuanGame.Phase.HAND_END, game.phase());
        assertTrue(game.result().exhaustive());
        assertEquals(SichuanSettlement.DrawStatus.READY, game.result().drawStatus().get(1));
        assertEquals(SichuanSettlement.DrawStatus.PASSIVE_FLOWER_PIG, game.result().drawStatus().get(3));
        assertTrue(game.result().ledger().stream().anyMatch(entry -> entry.type() == SichuanSettlement.Type.READY_PAYMENT && entry.payer() == 3 && entry.recipient() == 1));
        assertTrue(game.result().ledger().stream().noneMatch(entry -> entry.type() == SichuanSettlement.Type.FLOWER_PIG));
        game.validate();
    }

    @Test void scoringIncludesQuadPairsAndKongsWithoutRiichiSemantics() {
        var stock = new ArrayList<>(Tile.sichuanSet());
        var pairs = take(stock, "1m 1m 1m 1m 2m 2m 3m 3m 4m 4m 5m 5m 6m 6m");
        var score = SichuanHandAnalyzer.score(pairs, List.of(), SichuanPreset.SBR_2025.config(), false, false, false, false);
        assertNotNull(score);
        assertEquals(5, score.fan());
        assertEquals(8, score.value());
        assertTrue(score.patterns().contains(SichuanSettlement.Fan.ROOT));
        assertTrue(score.patterns().contains(SichuanSettlement.Fan.SEVEN_PAIRS));
    }

    @Test void selfDrawPaysOnlyActiveOpponentsAndDiscardKongUsesItsSupplier() {
        var winner = position(new int[]{2, 2, 2, 2}, false,
            "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 9p 9p", "", "", "");
        choose(winner, 0, WIN, -1);
        assertEquals(1, winner.turn());
        assertEquals(3, winner.save().ledger().size());
        assertTrue(winner.save().ledger().stream().allMatch(entry -> entry.type() == SichuanSettlement.Type.SELF_DRAW_WIN
            && entry.amount() == 2 && entry.recipient() == 0));
        winner.validate();
        var kong = position(new int[]{2, 2, 2, 2}, false, "9m", "9m 9m 9m", "", "");
        choose(kong, 0, DISCARD, 8); choose(kong, 1, DISCARD_KONG, -1); passAll(kong);
        assertEquals(1, kong.turn());
        assertEquals(List.of(new SichuanSettlement.Entry(0, SichuanSettlement.Type.DISCARD_KONG, 0, 1, 2, -1)), kong.save().ledger());
        choose(kong, 1, DRAW, -1);
        kong.validate();
    }

    @Test void kongTakesFrontReplacementAndIsRefundedOnShoot() {
        var game = position(new int[]{2, 2, 2, 2}, false,
            "9m 9m 9m 9m 9p", WAIT, "", "");
        choose(game, 0, CONCEALED_KONG, 8);
        assertEquals(3, game.save().ledger().size());
        int remaining = game.view(-1).wall().remaining();
        var before = game.save().wall();
        int next = before.slots().get(SichuanWallLayout.traversal(before.dealer(), before.die1(), before.die2()).get(before.cursor()));
        choose(game, 0, DRAW, -1);
        assertEquals(next, game.save().players().get(0).drawn());
        assertEquals(remaining - 1, game.view(-1).wall().remaining());
        choose(game, 0, DISCARD, Tile.parseKind("9p"));
        choose(game, 1, WIN, -1); passAll(game);
        assertTrue(game.save().wins().get(0).score().patterns().contains(SichuanSettlement.Fan.SHOOT_AFTER_KONG));
        assertEquals(3, game.save().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND).count());
        game.validate();
    }

    @Test void safeViewsAndStrictRestorationRejectMalformedDocuments() {
        var game = new SichuanGame(8);
        var view = game.view(0);
        assertEquals(view, SichuanCodec.decodeView(SichuanCodec.encodeView(view)));
        assertTrue(view.seats().get(1).hand().stream().allMatch(tile -> tile == Tile.HIDDEN));
        assertTrue(view.wall().slots().stream().allMatch(tile -> tile == Tile.HIDDEN || tile == Tile.ABSENT));
        String json = SichuanCodec.save(game);
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.decodeView(json));
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.restore(json.replace("\"revision\":1", "\"revision\":1,\"revision\":2")));
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.restore(json.replace("\"turn\":0,", "")));
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.restore(json.replace("\"replacementDraw\":false", "\"replacementDraw\":\"false\"")));
        var state = game.save();
        var players = new ArrayList<>(state.players());
        var first = players.get(0);
        var hand = new ArrayList<>(first.hand()); hand.set(0, hand.get(1));
        players.set(0, new SichuanPlayerState(hand, first.melds(), first.river(), first.voidSuit(), first.won(), first.drawn(), first.passedFan()));
        assertThrows(IllegalArgumentException.class, () -> SichuanGame.restore(copy(state, players, state.wall())));
    }

    @Test void addedKongRobberyRestoresThePungAndRetiresAllWinners() {
        String wait = "1m 2m 3m 4m 5m 6m 7m 8m 1p 2p 3p 9p 9p";
        var game = pungPosition(true, wait, wait);
        choose(game, 0, ADDED_KONG, 8);
        var restored = SichuanCodec.restore(SichuanCodec.save(game));
        choose(restored, 1, WIN, -1); choose(restored, 2, WIN, -1); passAll(restored);
        assertEquals(3, restored.turn());
        assertEquals(2, restored.save().wins().size());
        assertEquals(Meld.Type.TRIPLET, restored.save().players().get(0).melds().getFirst().type());
        assertEquals(Tile.ABSENT, restored.save().players().get(0).drawn());
        assertTrue(restored.save().wins().stream().allMatch(win -> win.robbingKong()
            && win.score().patterns().contains(SichuanSettlement.Fan.ROBBING_KONG)));
        assertTrue(restored.save().ledger().stream().noneMatch(SichuanSettlement.Entry::kong));
        restored.validate();
    }

    @Test void timelyAddedKongChargesEachActivePlayerButDelayedAddedKongIsFree() {
        for (boolean timely : new boolean[]{true, false}) {
            var game = pungPosition(timely, "", "");
            choose(game, 0, ADDED_KONG, 8); passAll(game);
            assertEquals(Meld.Type.ADDED_QUAD, game.save().players().get(0).melds().getFirst().type());
            assertEquals(timely ? 3 : 0, game.save().ledger().size());
            assertTrue(game.actions(0).stream().allMatch(action -> action.type() == DRAW));
            choose(game, 0, DRAW, -1);
            game.validate();
        }
    }

    @Test void exhaustiveDrawChargesActiveFlowerPigsAndRefundsNotReadyKongs() {
        var game = position(new int[]{2, 2, 2, 0}, true,
            "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 7p 8p", WAIT,
            "1m 2m 4m 5m 7m 8m 1p 2p 4p 5p 7p 8p 8p", "1m 2m 3m 4m 5m 6m 7m 8m 9m 1p 2p 3p 4p");
        var state = game.save();
        var players = new ArrayList<>(state.players());
        var river = new ArrayList<>(players.get(0).river());
        var discarded = river.stream().filter(tile -> Tile.kind(tile.tile()) / 9 == 1).findFirst().orElseThrow();
        river.remove(discarded);
        var owner = players.get(0);
        players.set(0, new SichuanPlayerState(owner.hand(), owner.melds(), river, owner.voidSuit(), false, owner.drawn(), -1));
        var pig = players.get(3);
        players.set(3, new SichuanPlayerState(pig.hand(), pig.melds(), List.of(discarded), pig.voidSuit(), false, pig.drawn(), -1));
        game = SichuanGame.restore(copy(state, players, state.wall()));
        choose(game, 0, DISCARD, Tile.parseKind("8p")); passAll(game);
        assertEquals(SichuanSettlement.DrawStatus.ACTIVE_FLOWER_PIG, game.result().drawStatus().get(3));
        assertEquals(1, game.result().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.FLOWER_PIG
            && entry.amount() == 24 && entry.recipient() == -1).count());
        assertEquals(-24, game.result().deltas().stream().mapToInt(Integer::intValue).sum());

        var kong = position(new int[]{2, 2, 2, 2}, false, "9m 9m 9m 9m", "", "", "");
        choose(kong, 0, CONCEALED_KONG, 8); choose(kong, 0, DRAW, -1);
        state = kong.save(); players = new ArrayList<>(state.players());
        var slots = new ArrayList<>(Collections.nCopies(108, Tile.ABSENT));
        owner = players.get(0); river = new ArrayList<>(owner.river());
        for (int tile : state.wall().slots()) if (tile != Tile.ABSENT) river.add(new SichuanPlayerState.Discard(tile, false));
        players.set(0, new SichuanPlayerState(owner.hand(), owner.melds(), river, owner.voidSuit(), false, owner.drawn(), -1));
        kong = SichuanGame.restore(copy(state, players, new SichuanWall.State(slots, 0, 1, 1, 108)));
        choose(kong, 0, DISCARD, Tile.kind(owner.hand().getFirst())); passAll(kong);
        assertEquals(3, kong.result().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND).count());
        kong.validate();
    }

    private static SichuanGame pungPosition(boolean timely, String second, String third) {
        var state = position(new int[]{2, 2, 2, 2}, false, "9m 9m 9m 9m", second, third, "").save();
        var players = new ArrayList<>(state.players());
        var player = players.get(0);
        var hand = new ArrayList<>(player.hand());
        var pung = hand.stream().filter(tile -> Tile.kind(tile) == 8).limit(3).toList();
        hand.removeAll(pung);
        int drawn = hand.stream().filter(tile -> (Tile.kind(tile) == 8) == timely).findFirst().orElseThrow();
        players.set(0, new SichuanPlayerState(hand, List.of(new Meld(Meld.Type.TRIPLET, pung, 3, pung.getFirst())),
            player.river(), player.voidSuit(), false, drawn, -1));
        return SichuanGame.restore(copy(state, players, state.wall()));
    }

    @Test void threeVariantSessionsRoundTripWithFreshAuthority() {
        var id = UUID.randomUUID();
        TableSession session = new RiichiSession(UUID.randomUUID(), RiichiPreset.MAHJONG_SOUL_4, 5);
        assertTrue(session.join(id, "host", 0));
        session = session.selectVariant(id, session.decision(), MahjongVariant.SICHUAN);
        assertInstanceOf(SichuanSession.class, session);
        assertEquals(4, session.capacity());
        assertTrue(session.configureEquipment(false, Tile.sichuanSet()));
        var restored = TableSessionCodec.restore(TableSessionCodec.save(session));
        assertInstanceOf(SichuanSession.class, restored);
        assertEquals(MahjongVariant.SICHUAN, restored.variant());
        assertNotEquals(session.incarnation(), restored.incarnation());
        assertTrue(restored.equipped());
        assertInstanceOf(McrSession.class, restored.selectVariant(id, restored.decision(), MahjongVariant.MCR));
    }

    private static void choose(SichuanGame game, int seat, SichuanAction.Type type, int argument) {
        var actions = game.actions(seat);
        for (int index = 0; index < actions.size(); index++) {
            var action = actions.get(index);
            if (action.type() == type && (argument < 0 || type == VOID_SUIT && action.suit() == argument
                || !action.tiles().isEmpty() && Tile.kind(action.tiles().get(0)) == argument)) {
                assertTrue(game.act(seat, game.decision(), index)); return;
            }
        }
        fail("Missing " + type + " for " + seat + ": " + actions);
    }
    private static void passAll(SichuanGame game) {
        for (int seat = 0; seat < 4 && game.phase() == SichuanGame.Phase.REACTION; seat++)
            if (!game.actions(seat).isEmpty()) choose(game, seat, PASS, -1);
    }
    private static SichuanGame position(int[] suits, boolean exhausted, String... hands) {
        var stock = new ArrayList<>(Tile.sichuanSet());
        var selected = new ArrayList<ArrayList<Integer>>();
        for (String hand : hands) selected.add(take(stock, hand));
        for (int seat = 0; seat < 4; seat++) while (selected.get(seat).size() < (seat == 0 ? 14 : 13)) selected.get(seat).add(stock.remove(0));
        var players = new ArrayList<SichuanPlayerState>();
        for (int seat = 0; seat < 4; seat++) players.add(new SichuanPlayerState(selected.get(seat), List.of(),
            exhausted && seat == 0 ? stock.stream().map(tile -> new SichuanPlayerState.Discard(tile, false)).toList() : List.of(),
            suits[seat], false, seat == 0 ? selected.get(seat).get(0) : Tile.ABSENT, -1));
        var slots = new ArrayList<>(Collections.nCopies(108, Tile.ABSENT));
        var order = SichuanWallLayout.traversal(0, 1, 1);
        if (!exhausted) for (int index = 0; index < stock.size(); index++) slots.set(order.get(53 + index), stock.get(index));
        var state = new SichuanGame.State(1, SichuanPreset.SBR_2025.config(), SichuanGame.Phase.TURN, 1, 1, 0,
            new SichuanWall.State(slots, 0, 1, 1, exhausted ? 108 : 53), players, Tile.ABSENT, -1, -1,
            false, false, -1, List.of(), List.of(), List.of(), null);
        return SichuanGame.restore(state);
    }
    private static ArrayList<Integer> take(ArrayList<Integer> stock, String notation) {
        var hand = new ArrayList<Integer>();
        if (!notation.isBlank()) for (String tile : notation.split(" ")) {
            int kind = Tile.parseKind(tile);
            int physical = stock.stream().filter(candidate -> Tile.kind(candidate) == kind).findFirst().orElseThrow();
            hand.add(physical); stock.remove(Integer.valueOf(physical));
        }
        return hand;
    }
    private static SichuanGame.State copy(SichuanGame.State state, List<SichuanPlayerState> players, SichuanWall.State wall) {
        return new SichuanGame.State(state.format(), state.rules(), state.phase(), state.revision(), state.decision(), state.turn(),
            wall, players, state.focus(), state.supplier(), state.pendingKong(), state.replacementDraw(), state.afterKong(),
            state.kongLedgerStart(), state.responses(), state.wins(), state.ledger(), state.result());
    }
}
