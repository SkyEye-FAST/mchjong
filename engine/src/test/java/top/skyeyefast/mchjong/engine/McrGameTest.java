package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.Action.Type.*;

class McrGameTest {
    @Test void initialPacketsFinishBeforeOrderedFlowerReplacement() {
        var order = new Fixture().at(0, FlowerTile.SPRING.id()).at(4, FlowerTile.SUMMER.id())
            .at(8, FlowerTile.AUTUMN.id()).at(12, FlowerTile.WINTER.id())
            .at(143, FlowerTile.PLUM.id()).at(142, 0).at(141, 1).at(140, 2).at(139, 3).build();
        var game = new McrGame(1, order);
        for (int seat = 0; seat < 4; seat++) {
            var expected = new HashSet<Integer>();
            for (int i = 0; i < (seat == 0 ? 14 : 13); i++) {
                int tile = order.get(dealSlot(seat, i));
                if (!Tile.isFlower(tile)) expected.add(tile);
            }
            expected.add(order.get(142 - seat));
            assertEquals(expected, new HashSet<>(game.hand(seat)));
            assertEquals(seat == 0 ? 14 : 13, game.hand(seat).size());
        }
        assertEquals(List.of(FlowerTile.SPRING.id(), FlowerTile.PLUM.id()), game.flowers(0));
        assertEquals(List.of(FlowerTile.SUMMER.id()), game.flowers(1));
        assertEquals(List.of(FlowerTile.AUTUMN.id()), game.flowers(2));
        assertEquals(List.of(FlowerTile.WINTER.id()), game.flowers(3));
        assertEquals(86, game.remaining());
        assertEquals(order.get(142), game.drawn(0));
        assertEquals(McrWinContext.KongWin.NONE, game.winningContext(0).kongWin());
        game.validate();
    }

    @Test void ordinaryDrawReplacesConsecutiveFlowersWithoutAKongWin() {
        var game = new McrGame(2, new Fixture().hand(1, "19m19p19s1234567z")
            .at(53, FlowerTile.SPRING.id()).at(143, FlowerTile.SUMMER.id()).at(142, 0).build());
        discard(game, 0, game.drawn(0));
        passAll(game);
        play(game, 1, DRAW);
        assertEquals(List.of(FlowerTile.SPRING.id(), FlowerTile.SUMMER.id()), game.flowers(1));
        assertEquals(14, game.hand(1).size());
        var context = game.winningContext(1);
        assertEquals(McrWinContext.Method.SELF_DRAW, context.method());
        assertEquals(McrWinContext.KongWin.NONE, context.kongWin());
        assertEquals(2, context.flowerCount());
        assertEquals(Tile.SOUTH, context.seatWind());
        assertEquals(Tile.EAST, context.roundWind());
        assertFalse(context.wallLast());
        assertTrue(has(game, 1, TSUMO));
        game.validate();
    }

    @Test void claimsWaitForAllRepliesAndPungOutranksChow() {
        var game = new McrGame(3, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "46m123789p123s11z").hand(2, "55m234678s234p22z").build());
        discardKind(game, 0, "5m");
        assertTrue(has(game, 1, CHI));
        assertFalse(has(game, 2, CHI));
        play(game, 1, CHI);
        assertEquals(McrGame.Phase.REACTION, game.phase());
        play(game, 2, PON);
        passAll(game);
        assertEquals(2, game.turn());
        assertEquals(Meld.Type.PON, game.melds(2).get(0).type());
        assertTrue(game.melds(1).isEmpty());
        assertTrue(game.river(0).getLast().called());
        assertEquals(Tile.ABSENT, game.drawn(2));
        assertFalse(has(game, 2, TSUMO));
        assertFalse(has(game, 2, CLOSED_KAN));
        game.validate();
    }

    @Test void nearestQualifyingRonWinsOnceAheadOfKongRegardlessOfReplyOrder() {
        var game = new McrGame(4, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "123456789p11s46m").hand(2, "123456789s22p46m")
            .hand(3, "555m123789m123p1z").build());
        discardKind(game, 0, "5m");
        assertTrue(has(game, 1, RON));
        assertTrue(has(game, 2, RON));
        assertFalse(has(game, 2, CHI));
        long token = game.decision();
        int choice = index(game, 2, RON);
        assertFalse(game.act(2, token - 1, choice));
        assertFalse(game.act(2, token, 999));
        assertTrue(game.act(2, token, choice));
        assertFalse(game.act(2, token, choice), "A response is accepted only once");
        play(game, 3, OPEN_KAN);
        assertEquals(McrGame.Phase.REACTION, game.phase());
        play(game, 1, RON);
        var win = assertInstanceOf(McrSettlement.Win.class, game.result());
        assertEquals(1, win.winner());
        assertEquals(0, win.fromSeat());
        assertEquals(24 + win.score().totalFan(), game.points(1));
        assertEquals(-8 - win.score().totalFan(), game.points(0));
        assertEquals(-8, game.points(2));
        assertEquals(-8, game.points(3));
        assertTrue(game.melds(3).isEmpty());
        assertTrue(game.hand(1).contains(win.tile()));
        assertFalse(game.hand(2).contains(win.tile()));
        assertTrue(game.river(0).getLast().called());
        assertTrue(game.penalties().isEmpty());
        assertFalse(game.act(1, token, 0));
        game.validate();
    }

    @Test void aLowFanSelfDrawIsOfferedThenPenalizedWithoutEndingTheHand() {
        var game = new McrGame(5, new Fixture().hand(0, "12345m567p789s11z6m").build());
        assertNotNull(game.score(0));
        assertFalse(game.score(0).meetsMinimum());
        assertTrue(has(game, 0, TSUMO));
        var before = game.hand(0);
        play(game, 0, TSUMO);
        assertEquals(McrGame.Phase.TURN, game.phase());
        assertNull(game.result());
        assertEquals(before, game.hand(0));
        assertTrue(game.winForbidden(0));
        assertFalse(has(game, 0, TSUMO));
        assertEquals(List.of(-30, 10, 10, 10), game.penalties().get(0).deltas());
        assertEquals(1, game.penalties().get(0).handNumber());
        assertEquals(-30, game.points(0));
        discard(game, 0, game.drawn(0));
        passAll(game);
        assertEquals(McrGame.Phase.DRAW, game.phase());
        game.validate();
        exhaust(game);
        assertTrue(game.nextHand());
        assertFalse(game.winForbidden(0), "Stop-win lasts only for the current hand");
        assertEquals(-30, game.points(0), "Starting another hand does not erase penalties");
    }

    @Test void aLowFanRonContinuesPlayAndBlocksEvenALaterQualifyingSelfDraw() {
        var game = new McrGame(6, new Fixture().hand(0, "279m147p258s2345z8s")
            .hand(1, "445566m2277779s").at(53, Tile.parseKind("8s")).build());
        discardKind(game, 0, "8s");
        assertNotNull(game.score(1));
        assertFalse(game.score(1).meetsMinimum());
        play(game, 1, RON);
        passAll(game);
        assertNull(game.result());
        assertEquals(List.of(10, -30, 10, 10), game.penalties().get(0).deltas());
        assertFalse(game.river(0).getLast().called());
        play(game, 1, DRAW);
        var beforeWin = new ArrayList<>(game.hand(1));
        beforeWin.remove(Integer.valueOf(game.drawn(1)));
        var wouldWin = McrHandAnalyzer.score(beforeWin, game.melds(1), 1, game.drawn(1), game.winningContext(1));
        assertNotNull(wouldWin);
        assertTrue(wouldWin.meetsMinimum());
        assertFalse(has(game, 1, TSUMO));
        assertTrue(game.winForbidden(1));
        assertTrue(has(game, 1, CLOSED_KAN), "Stopping wins must not stop normal declarations");
        play(game, 1, CLOSED_KAN);
        assertEquals(Meld.Type.CLOSED_KAN, game.melds(1).get(0).type());
        assertEquals(1, game.penalties().size());
        game.validate();
    }

    @Test void incompleteShapesNeverOfferAWinDeclaration() {
        var game = new McrGame(7, new Fixture().hand(0, "147m258p369s1234z5m")
            .hand(1, "159m159p159s1234z").build());
        assertNull(game.score(0));
        assertFalse(has(game, 0, TSUMO));
        discardKind(game, 0, "5m");
        assertFalse(has(game, 1, RON));
        passAll(game);
        assertTrue(game.penalties().isEmpty());
        game.validate();
    }

    @Test void wrongRonDoesNotConsumeAnotherPlayersClaimAndTheOffenderCanStillPung() {
        var game = new McrGame(9, new Fixture().hand(0, "279m147p258s2345z8s")
            .hand(1, "445566m2277779s").hand(2, "88s123456m789p11z").build());
        discardKind(game, 0, "8s");
        play(game, 1, RON);
        play(game, 2, PON);
        passAll(game);
        assertTrue(game.winForbidden(1));
        assertEquals(2, game.turn());
        assertNull(game.result());
        discardKind(game, 2, "5m");
        assertTrue(has(game, 1, PON));
        assertFalse(has(game, 1, RON));
        play(game, 1, PON);
        passAll(game);
        assertEquals(1, game.turn());
        assertEquals(Meld.Type.PON, game.melds(1).get(0).type());
        assertEquals(1, game.penalties().size());
        game.validate();
    }

    @Test void wrongNearestRonIsPenalizedSeparatelyFromTheActualWinnersPayment() {
        var game = new McrGame(10, new Fixture().hand(0, "279m147p258s2345z8s")
            .hand(1, "445566m2277779s").hand(2, "123456789p111z8s").build());
        discardKind(game, 0, "8s");
        play(game, 1, RON);
        play(game, 2, RON);
        passAll(game);
        var win = assertInstanceOf(McrSettlement.Win.class, game.result());
        assertEquals(2, win.winner());
        assertEquals(1, game.penalties().size());
        assertEquals(1, game.penalties().get(0).offender());
        assertEquals(-38, game.points(1));
        assertEquals(34 + win.score().totalFan(), game.points(2));
        assertEquals(-8, win.deltas().get(1), "A hand result does not absorb earlier penalty transfers");
        game.validate();
    }

    @Test void stopWinBlocksLastWallRonAndLastWallIsNotLastCopy() {
        var fixture = new Fixture().hand(0, "279m147p258s2345z8s")
            .hand(1, "445566m2277779s").hand(2, "123456789p111z8s").at(135, Tile.parseKind("8s"));
        int slot = 53;
        for (var flower : FlowerTile.values()) fixture.at(slot++, flower.id());
        var game = new McrGame(11, fixture.build());
        discardKind(game, 0, "8s");
        play(game, 1, RON);
        passAll(game); // The qualifying player declines this first discard.
        for (int steps = 0; steps < 500 && !(game.phase() == McrGame.Phase.TURN && game.remaining() == 0); steps++) {
            switch (game.phase()) {
                case DRAW -> play(game, game.turn(), DRAW);
                case TURN -> discard(game, game.turn(), game.drawn(game.turn()));
                case REACTION -> passAll(game);
                default -> fail("Ended before the last ordinary wall draw");
            }
        }
        assertEquals(3, game.turn());
        assertEquals(0, game.remaining());
        int tile = game.drawn(3);
        assertEquals(Tile.parseKind("8s"), Tile.kind(tile));
        assertTrue(game.winningContext(3).wallLast());
        assertFalse(game.winningContext(3).lastCopy(), "Two other copies are still concealed");
        discard(game, 3, tile);
        assertEquals(McrGame.Phase.REACTION, game.phase());
        var context = game.winningContext(1);
        assertTrue(context.wallLast());
        assertFalse(context.lastCopy());
        assertTrue(McrHandAnalyzer.score(game.hand(1), game.melds(1), 1, tile, context).meetsMinimum());
        assertFalse(has(game, 1, RON));
        assertTrue(has(game, 2, RON));
        assertFalse(has(game, 0, PON));
        passAll(game);
        assertInstanceOf(McrSettlement.Draw.class, game.result());
        assertEquals(-30, game.points(1));
        game.validate();
    }

    @Test void chowExecutesOnlyAfterHigherPriorityResponsesPass() {
        var game = new McrGame(12, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "46m123789p123s11z").hand(2, "55m234678s234p22z").build());
        discardKind(game, 0, "5m");
        play(game, 1, CHI);
        passAll(game);
        assertEquals(1, game.turn());
        var meld = game.melds(1).get(0);
        assertEquals(Meld.Type.CHI, meld.type());
        assertEquals(0, meld.fromSeat());
        assertEquals(List.of(3, 4, 5), meld.tiles().stream().map(Tile::kind).toList());
        assertEquals(11, game.hand(1).size());
        assertEquals(Tile.ABSENT, game.drawn(1));
        discard(game, 1, game.hand(1).getFirst());
        passAll(game);
        assertEquals(2, game.turn());
        game.validate();
    }

    @Test void exposedAndConcealedKongsReplaceWithoutSharingRiichiDeadWallRules() {
        var exposed = new McrGame(13, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "555m123456p78s11z").at(143, Tile.parseKind("9s")).build());
        discardKind(exposed, 0, "5m");
        int remaining = exposed.remaining();
        play(exposed, 1, OPEN_KAN);
        passAll(exposed);
        assertEquals(remaining - 1, exposed.remaining());
        assertEquals(Meld.Type.OPEN_KAN, exposed.melds(1).get(0).type());
        assertEquals(McrWinContext.KongWin.REPLACEMENT, exposed.winningContext(1).kongWin());
        assertTrue(has(exposed, 1, TSUMO));
        play(exposed, 1, TSUMO);
        assertInstanceOf(McrSettlement.Win.class, exposed.result());

        var concealed = new McrGame(14, new Fixture().hand(0, "1111m123456p78s22z")
            .at(143, Tile.parseKind("9s")).build());
        remaining = concealed.remaining();
        play(concealed, 0, CLOSED_KAN);
        assertEquals(remaining - 1, concealed.remaining());
        assertEquals(Meld.Type.CLOSED_KAN, concealed.melds(0).get(0).type());
        assertEquals(McrWinContext.KongWin.REPLACEMENT, concealed.winningContext(0).kongWin());
        assertTrue(has(concealed, 0, TSUMO));
        play(concealed, 0, TSUMO);
        assertInstanceOf(McrSettlement.Win.class, concealed.result());
    }

    @Test void aFlowerAfterKongStartsAFlowerReplacementRatherThanAKongWin() {
        var game = new McrGame(15, new Fixture().hand(0, "1111m123456p78s22z")
            .at(143, FlowerTile.SPRING.id()).at(142, FlowerTile.SUMMER.id())
            .at(141, Tile.parseKind("9s")).build());
        play(game, 0, CLOSED_KAN);
        assertEquals(List.of(FlowerTile.SPRING.id(), FlowerTile.SUMMER.id()), game.flowers(0));
        assertEquals(McrWinContext.KongWin.NONE, game.winningContext(0).kongWin());
        assertEquals(2, game.winningContext(0).flowerCount());
        assertFalse(game.score(0).fans().stream().anyMatch(fan -> fan.id().equals("OUT_WITH_REPLACEMENT_TILE")));
        game.validate();
    }

    @Test void addedKongIsCommittedOnlyAfterRobbingResponsesAndKeepsTheOriginalSupplier() {
        var game = addedKongPosition();
        int remaining = game.remaining();
        assertTrue(game.winningContext(1).lastCopy(), "The exposed pung and its called river tile count only three times");
        assertFalse(game.winningContext(1).wallLast());
        play(game, 1, ADDED_KAN);
        assertEquals(McrGame.Phase.REACTION, game.phase());
        assertEquals(Meld.Type.PON, game.melds(1).get(0).type());
        assertEquals(McrWinContext.KongWin.ROBBED, game.winningContext(2).kongWin());
        assertTrue(has(game, 2, RON));
        assertFalse(has(game, 2, CHI));
        passAll(game);
        assertEquals(remaining - 1, game.remaining());
        var kong = game.melds(1).get(0);
        assertEquals(Meld.Type.ADDED_KAN, kong.type());
        assertEquals(0, kong.fromSeat());
        assertEquals(McrWinContext.KongWin.REPLACEMENT, game.winningContext(1).kongWin());
        assertTrue(has(game, 1, TSUMO));
        game.validate();
    }

    @Test void robbingAnAddedKongLeavesAPungAndDoesNotTakeAReplacement() {
        var game = addedKongPosition();
        int remaining = game.remaining();
        int extra = game.drawn(1);
        var previousRiver = game.river(1);
        play(game, 1, ADDED_KAN);
        play(game, 2, RON);
        passAll(game);
        var win = assertInstanceOf(McrSettlement.Win.class, game.result());
        assertEquals(2, win.winner());
        assertEquals(1, win.fromSeat());
        assertEquals(McrWinContext.KongWin.ROBBED, win.context().kongWin());
        assertEquals(remaining, game.remaining());
        assertEquals(Meld.Type.PON, game.melds(1).get(0).type());
        assertFalse(game.hand(1).contains(extra));
        assertTrue(game.hand(2).contains(extra));
        assertEquals(previousRiver, game.river(1), "Robbing a kong is not a discard event");
        game.validate();
    }

    static McrGame addedKongPosition() {
        var game = new McrGame(16, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "55m123456p789s11z").hand(2, "123456s789p22z46m")
            .at(53, Tile.WEST).at(54, Tile.GREEN).at(55, Tile.RED)
            .at(56, Tile.parseKind("5m")).at(143, Tile.parseKind("9s")).build());
        discardKind(game, 0, "5m");
        play(game, 1, PON);
        passAll(game);
        discardKind(game, 1, "9s");
        passAll(game);
        for (int seat : new int[]{2, 3, 0}) {
            play(game, seat, DRAW);
            discard(game, seat, game.drawn(seat));
            passAll(game);
        }
        play(game, 1, DRAW);
        assertEquals(Tile.parseKind("5m"), Tile.kind(game.drawn(1)));
        assertTrue(has(game, 1, ADDED_KAN));
        return game;
    }

    @Test void dealerSelfDrawPaysNormallyAndAlwaysAdvancesTheDealer() {
        var game = new McrGame(8, new Fixture().hand(0, "19m19p19s1234567z1m").build());
        play(game, 0, TSUMO);
        var win = assertInstanceOf(McrSettlement.Win.class, game.result());
        assertEquals(-1, win.fromSeat());
        assertEquals(3 * (8 + win.score().totalFan()), game.points(0));
        assertEquals(-(8 + win.score().totalFan()), game.points(1));
        game.validate();
        assertTrue(game.nextHand());
        assertEquals(2, game.handNumber());
        assertEquals(1, game.dealer());
        assertEquals(Tile.NORTH, game.seatWind(0));
        assertEquals(Tile.EAST, game.seatWind(1));
        assertEquals(14, game.hand(1).size());
        assertFalse(game.nextHand());
        game.validate();
    }

    @Test @Timeout(20) void sixteenExhaustiveHandsRotateAllWindsWithoutNotenPayments() {
        var game = new McrGame(711);
        for (int hand = 1; hand <= 16; hand++) {
            assertEquals(hand, game.handNumber());
            assertEquals((hand - 1) % 4, game.dealer());
            assertEquals(Tile.EAST + (hand - 1) / 4, game.roundWind());
            for (int seat = 0; seat < 4; seat++) {
                assertEquals(Tile.EAST + Math.floorMod(seat - game.dealer(), 4), game.seatWind(seat));
                assertEquals(seat == game.dealer() ? 14 : 13, game.hand(seat).size());
            }
            exhaust(game);
            assertInstanceOf(McrSettlement.Draw.class, game.result());
            game = McrCodec.restore(McrCodec.save(game));
            for (int seat = 0; seat < 4; seat++) assertEquals(0, game.points(seat));
            assertEquals(0, game.remaining());
            assertEquals(hand < 16 ? McrGame.Phase.HAND_END : McrGame.Phase.MATCH_END, game.phase());
            assertEquals(hand < 16, game.nextHand());
        }
        assertEquals(16, game.handNumber());
        assertFalse(game.nextHand());
        for (int seat = 0; seat < 4; seat++) assertTrue(game.actions(seat).isEmpty());
    }

    static boolean has(McrGame game, int seat, Action.Type type) {
        return game.actions(seat).stream().anyMatch(action -> action.type() == type);
    }

    static int index(McrGame game, int seat, Action.Type type) {
        var actions = game.actions(seat);
        for (int i = 0; i < actions.size(); i++) if (actions.get(i).type() == type) return i;
        fail("Missing " + type + " for seat " + seat + " in " + game.phase() + ": " + actions);
        return -1;
    }

    static void play(McrGame game, int seat, Action.Type type) {
        assertTrue(game.act(seat, game.decision(), index(game, seat, type)));
        game.validate();
    }

    static void discard(McrGame game, int seat, int tile) {
        int choice = game.actions(seat).indexOf(new Action(DISCARD, tile));
        assertTrue(choice >= 0, "Missing discard " + tile);
        assertTrue(game.act(seat, game.decision(), choice));
        game.validate();
    }

    static void discardKind(McrGame game, int seat, String notation) {
        int kind = Tile.parseKind(notation);
        discard(game, seat, game.hand(seat).stream().filter(tile -> Tile.kind(tile) == kind).findFirst().orElseThrow());
    }

    static void passAll(McrGame game) {
        for (int seat = 0; seat < 4 && game.phase() == McrGame.Phase.REACTION; seat++)
            if (has(game, seat, PASS)) play(game, seat, PASS);
    }

    static void exhaust(McrGame game) {
        for (int steps = 0; steps < 600; steps++) {
            switch (game.phase()) {
                case TURN -> discard(game, game.turn(), game.drawn(game.turn()) >= 0
                    ? game.drawn(game.turn()) : game.hand(game.turn()).get(0));
                case DRAW -> play(game, game.turn(), DRAW);
                case REACTION -> passAll(game);
                case HAND_END, MATCH_END -> { return; }
            }
        }
        fail("MCR hand did not finish");
    }

    private static int dealSlot(int seat, int index) {
        return index < 12 ? index / 4 * 16 + seat * 4 + index % 4
            : index == 13 ? 50 : 48 + seat + (seat >= 2 ? 1 : 0);
    }

    /** Build one full physical wall, allocating copies across all explicitly supplied hands. */
    static final class Fixture {
        private final List<Integer> stock = new ArrayList<>(Tile.mcrSet());
        private final Integer[] order = new Integer[144];

        Fixture hand(int seat, String notation) {
            var tiles = TestHands.tiles(notation);
            assertEquals(seat == 0 ? 14 : 13, tiles.size());
            for (int i = 0; i < tiles.size(); i++) at(dealSlot(seat, i), Tile.kind(tiles.get(i)));
            return this;
        }

        Fixture at(int slot, int kindOrFlower) {
            assertNull(order[slot]);
            int tile = stock.stream().filter(id -> Tile.isFlower(id) ? id == kindOrFlower
                : kindOrFlower < 34 && Tile.kind(id) == kindOrFlower).findFirst().orElseThrow();
            stock.remove(Integer.valueOf(tile));
            order[slot] = tile;
            return this;
        }

        List<Integer> build() {
            int next = 0;
            for (int slot = 0; slot < 144; slot++) if (order[slot] == null) order[slot] = stock.get(next++);
            return List.copyOf(Arrays.asList(order));
        }
    }
}
