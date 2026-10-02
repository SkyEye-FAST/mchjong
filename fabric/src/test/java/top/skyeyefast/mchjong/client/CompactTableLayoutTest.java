package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.*;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class CompactTableLayoutTest {
    private static RiichiView base(RiichiPreset rules) {
        var id = new UUID(15, 20);
        return TableLayoutTest.startSession(rules, id).view(id);
    }

    private static List<Meld> melds(Meld.Type type, int count, int owner, int source) {
        return IntStream.range(0, count).mapToObj(i -> {
            var tiles = type == Meld.Type.SEQUENCE ? List.of(i * 12, i * 12 + 4, i * 12 + 8)
                : IntStream.range(i * 4, i * 4 + (type == Meld.Type.TRIPLET ? 3 : 4)).boxed().toList();
            return new Meld(type, tiles, type == Meld.Type.CONCEALED_QUAD ? owner : source,
                type == Meld.Type.CONCEALED_QUAD ? Tile.ABSENT : tiles.getFirst());
        }).toList();
    }

    private static List<RiichiTableScene.Piece> scene(RiichiView v, int owner, List<Meld> melds,
                                               int handSize, boolean drawn, boolean exposed) {
        var seats = new ArrayList<>(v.seats());
        var hand = IntStream.range(80, 80 + handSize).boxed().toList();
        seats.set(owner, new RiichiView.Seat(false, "Test", true, false, false, 25000, hand,
            drawn ? hand.getLast() : Tile.ABSENT, melds, List.of(), List.of(), false, exposed, false));
        var view = new RiichiView(v.tableId(), v.revision(), v.decision(), v.handNumber(), v.rules(), v.phase(), owner,
            v.dealer(), v.round(), v.honba(), v.riichiSticks(), v.turn(), v.remaining(), v.wallBreak(), v.wall(), v.focus(),
            seats, v.actions(), v.wins(), v.result(), v.deltas(), v.finalScores(), v.finalUma(), v.timeControl(), v.clocks(), v.finalRanks(), v.playerHandVisibility(), v.openHands(), v.exitVote(), v.handling(), v.autoPlay(), v.ronBlocked(), v.riichiHan(), v.riichiSafeTiles(), v.externalBots(), v.settlementTicks(), v.settlementSkippedSeats());
        return RiichiTableScene.build(view).stream().filter(p -> p.seat() == owner).toList();
    }

    private static double x(RiichiTableScene.Piece p) {
        return TableGeometry.orient(p.position().x, p.position().y, p.position().z, (4 - p.seat()) % 4).x;
    }

    private static double halfWidth(RiichiTableScene.Piece p) {
        boolean sideways = Math.floorMod(Math.round(p.yaw() / 90) - p.seat(), 2) == 1;
        return (double) (sideways ? TileMesh.HEIGHT : TileMesh.WIDTH) * RiichiTableScene.TILE_SCALE / 2;
    }

    private static double center(List<RiichiTableScene.Piece> pieces, int concealed) {
        return pieces.stream().filter(p -> p.area() == RiichiTableScene.Area.HAND && p.index() < concealed)
            .mapToDouble(CompactTableLayoutTest::x).average().orElseThrow();
    }

    @Test void openKanClearanceKeepsWaitingDrawnAndExposedHandsCenteredAndPickable() {
        // Open kans occupy the widest corner. TableLayoutTest owns per-meld geometry.
        for (RiichiPreset rules : List.of(RiichiPreset.TENHOU_4, RiichiPreset.TENHOU_3)) {
            var v = base(rules);
            for (int owner = 0; owner < rules.players(); owner++) for (int count = 0; count <= 4; count++)
                for (int state = 0; state < 4; state++) {
                    boolean drawn = state == 1;
                    boolean exposed = state == 3;
                    int size = (state == 0 ? 13 : 14) - count * 3;
                    int concealed = size - (drawn ? 1 : 0);
                    var pieces = scene(v, owner, melds(Meld.Type.OPEN_QUAD, count, owner, (owner + 1) % rules.players()), size, drawn, exposed);
                    var hand = pieces.stream().filter(p -> p.area() == RiichiTableScene.Area.HAND).toList();
                    double shift = center(pieces, concealed);
                    String context = rules + " seat=" + owner + " kans=" + count + " state=" + state;
                    assertTrue(shift <= 1e-9, context);
                    assertTrue(shift > -TableGeometry.FELT_HALF_WIDTH / 2, context);
                    if (count == 0) assertEquals(0, shift, 1e-9, context);
                    else {
                        double left = pieces.stream().filter(p -> p.area() == RiichiTableScene.Area.MELD)
                            .mapToDouble(p -> x(p) - halfWidth(p)).min().orElseThrow();
                        double right = hand.stream().mapToDouble(p -> x(p) + halfWidth(p)).max().orElseThrow();
                        double gap = left - right;
                        assertTrue(gap >= RiichiTableScene.HAND_MELD_GAP - 1e-9, context);
                        if (shift < -1e-9) assertEquals(RiichiTableScene.HAND_MELD_GAP, gap, 1e-9,
                            context + ": a shifted hand must not leave unused clearance toward the center");
                    }
                    for (int i = 1; i < size; i++) assertEquals(RiichiTableScene.HAND_STEP
                        + (drawn && i == size - 1 ? RiichiTableScene.DRAW_GAP : 0), x(hand.get(i)) - x(hand.get(i - 1)), 1e-9, context);
                    var settings = new TableSettings();
                    var camera = TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, owner);
                    for (var piece : hand) {
                        assertEquals(80 + piece.index(), piece.tile(), context);
                        assertTrue(Double.isFinite(TilePicking.distanceSquared(new RiichiAnimation.Frame(piece, 0),
                            camera, piece.position().subtract(camera), false)), context);
                    }
                }
        }
    }

    @Test void twoOpenKansDoNotMoveAWaitingHandForAnAbsentDraw() {
        var v = base(RiichiPreset.TENHOU_4);
        for (int owner = 0; owner < 4; owner++) {
            var melds = melds(Meld.Type.OPEN_QUAD, 2, owner, (owner + 1) % 4);
            var waiting = scene(v, owner, melds, 7, false, false);
            var drawn = scene(v, owner, melds, 8, true, false);
            assertEquals(0, center(waiting, 7), 1e-9, "An absent draw must not force an otherwise centered hand left");
            assertTrue(center(drawn, 7) < 0);
            assertTrue(center(drawn, 7) > -RiichiTableScene.HAND_STEP - RiichiTableScene.DRAW_GAP);
            var offset = TableGeometry.orient(center(drawn, 7) - center(waiting, 7), 0, 0, owner);
            var before = waiting.stream().filter(p -> p.area() == RiichiTableScene.Area.HAND).toList();
            var after = drawn.stream().filter(p -> p.area() == RiichiTableScene.Area.HAND).toList();
            for (int i = 0; i < 7; i++) assertEquals(0,
                before.get(i).position().add(offset).distanceTo(after.get(i).position()), 1e-9);
            assertEquals(waiting.stream().filter(p -> p.area() == RiichiTableScene.Area.MELD).toList(),
                drawn.stream().filter(p -> p.area() == RiichiTableScene.Area.MELD).toList());
        }
    }

    @Test void upgradingAPonOnlyMovesTheHandWhenTheKanReallyWidensTheCorner() {
        var v = base(RiichiPreset.TENHOU_4);
        for (int owner = 0; owner < 4; owner++) {
            int source = (owner + 1) % 4;
            var pons = scene(v, owner, melds(Meld.Type.TRIPLET, 3, owner, source), 5, true, false);
            var added = scene(v, owner, melds(Meld.Type.ADDED_QUAD, 3, owner, source), 5, true, false);
            var open = scene(v, owner, melds(Meld.Type.OPEN_QUAD, 3, owner, source), 5, true, false);
            assertEquals(center(pons, 4), center(added, 4), 1e-9, "Front-aligned added tiles must not reserve extra horizontal width");
            assertTrue(center(open, 4) < center(pons, 4), "A genuinely wider open kan needs more room");
        }
    }

    @Test void aDrawnTileMovedIntoTheHandHasNoStrayDrawGap() {
        var base = base(RiichiPreset.TENHOU_4);
        var player = new RiichiView.Seat(false, "Test", true, false, false, 25000,
            List.of(0, 4, 8), 4, List.of(), List.of(), List.of(), false, false, false);
        var hand = new TableHand(player, 0, 640, 400, 32, true);
        assertEquals(hand.tileWidth(), hand.centerX(8) - hand.centerX(4));
        var point = hand.point(4);
        assertEquals(4, hand.pick(point.x(), point.y(), Tile.ABSENT));
        var seats = new ArrayList<>(base.seats());
        seats.set(0, player);
        var view = new RiichiView(base.tableId(), base.revision(), base.decision(), base.handNumber(), base.rules(),
            base.phase(), 0, base.dealer(), base.round(), base.honba(), base.riichiSticks(), base.turn(),
            base.remaining(), base.wallBreak(), base.wall(), base.focus(), seats, base.actions(), base.wins(),
            base.result(), base.deltas(), base.finalScores(), base.finalUma(), base.timeControl(), base.clocks(),
            base.finalRanks(), base.playerHandVisibility(), base.openHands(), base.exitVote(), base.handling(), base.autoPlay(),
            base.ronBlocked(), base.riichiHan(), base.riichiSafeTiles(), base.externalBots(), base.settlementTicks(), base.settlementSkippedSeats());
        var pieces = RiichiTableScene.build(view).stream().filter(piece -> piece.area() == RiichiTableScene.Area.HAND && piece.seat() == 0).toList();
        assertEquals(RiichiTableScene.HAND_STEP, pieces.get(2).position().distanceTo(pieces.get(1).position()), 1e-9);
    }
}
