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
    private static TableView base(RuleSet rules) {
        var id = new UUID(15, 20);
        var game = new Game(UUID.randomUUID(), rules, 15);
        assertTrue(game.join(id, "Test", 0));
        return game.view(id);
    }

    private static List<Meld> melds(Meld.Type type, int count, int owner, int source) {
        return IntStream.range(0, count).mapToObj(i -> {
            var tiles = type == Meld.Type.CHI ? List.of(i * 12, i * 12 + 4, i * 12 + 8)
                : IntStream.range(i * 4, i * 4 + (type == Meld.Type.PON ? 3 : 4)).boxed().toList();
            return new Meld(type, tiles, type == Meld.Type.CLOSED_KAN ? owner : source,
                type == Meld.Type.CLOSED_KAN ? Tile.ABSENT : tiles.getFirst());
        }).toList();
    }

    private static List<TableScene.Piece> scene(TableView v, int owner, List<Meld> melds,
                                               int handSize, boolean drawn, boolean exposed) {
        var seats = new ArrayList<>(v.seats());
        var hand = IntStream.range(80, 80 + handSize).boxed().toList();
        seats.set(owner, new TableView.Seat("Test", true, false, false, 25000, hand,
            drawn ? hand.getLast() : Tile.ABSENT, melds, List.of(), List.of(), false, exposed));
        var view = new TableView(v.tableId(), v.revision(), v.decision(), v.handNumber(), v.rules(), v.phase(), owner,
            v.dealer(), v.round(), v.honba(), v.riichiSticks(), v.turn(), v.remaining(), v.wallBreak(), v.wall(), v.focus(),
            seats, v.actions(), v.wins(), v.result(), v.deltas(), v.finalScores(), v.timeControl(), v.clocks(), v.finalRanks(), v.openHands(), v.exitVote(), v.handling(), v.autoPlay());
        return TableScene.build(view).stream().filter(p -> p.seat() == owner).toList();
    }

    private static double x(TableScene.Piece p) {
        return TableGeometry.orient(p.position().x, p.position().y, p.position().z, (4 - p.seat()) % 4).x;
    }

    private static double halfWidth(TableScene.Piece p) {
        boolean sideways = Math.floorMod(Math.round(p.yaw() / 90) - p.seat(), 2) == 1;
        return (double) (sideways ? TileMesh.HEIGHT : TileMesh.WIDTH) * TableScene.TILE_SCALE / 2;
    }

    private static double center(List<TableScene.Piece> pieces, int concealed) {
        return pieces.stream().filter(p -> p.area() == TableScene.Area.HAND && p.index() < concealed)
            .mapToDouble(CompactTableLayoutTest::x).average().orElseThrow();
    }

    @Test void openKanClearanceKeepsWaitingDrawnAndExposedHandsCenteredAndPickable() {
        // Open kans occupy the widest corner. TableLayoutTest owns per-meld geometry.
        for (RuleSet rules : List.of(RuleSet.TENHOU_4, RuleSet.TENHOU_3)) {
            var v = base(rules);
            for (int owner = 0; owner < rules.players(); owner++) for (int count = 0; count <= 4; count++)
                for (int state = 0; state < 4; state++) {
                    boolean drawn = state == 1;
                    boolean exposed = state == 3;
                    int size = (state == 0 ? 13 : 14) - count * 3;
                    int concealed = size - (drawn ? 1 : 0);
                    var pieces = scene(v, owner, melds(Meld.Type.OPEN_KAN, count, owner, (owner + 1) % rules.players()), size, drawn, exposed);
                    var hand = pieces.stream().filter(p -> p.area() == TableScene.Area.HAND).toList();
                    double shift = center(pieces, concealed);
                    String context = rules + " seat=" + owner + " kans=" + count + " state=" + state;
                    assertTrue(shift <= 1e-9, context);
                    assertTrue(shift > -TableGeometry.FELT_HALF_WIDTH / 2, context);
                    if (count == 0) assertEquals(0, shift, 1e-9, context);
                    else {
                        double left = pieces.stream().filter(p -> p.area() == TableScene.Area.MELD)
                            .mapToDouble(p -> x(p) - halfWidth(p)).min().orElseThrow();
                        double right = hand.stream().mapToDouble(p -> x(p) + halfWidth(p)).max().orElseThrow();
                        double gap = left - right;
                        assertTrue(gap >= TableScene.HAND_MELD_GAP - 1e-9, context);
                        if (shift < -1e-9) assertEquals(TableScene.HAND_MELD_GAP, gap, 1e-9,
                            context + ": a shifted hand must not leave unused clearance toward the center");
                    }
                    for (int i = 1; i < size; i++) assertEquals(TableScene.HAND_STEP
                        + (drawn && i == size - 1 ? TableScene.DRAW_GAP : 0), x(hand.get(i)) - x(hand.get(i - 1)), 1e-9, context);
                    var settings = new TableSettings();
                    var camera = TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, owner);
                    for (var piece : hand) {
                        assertEquals(80 + piece.index(), piece.tile(), context);
                        assertTrue(Double.isFinite(TilePicking.distanceSquared(new TableAnimation.Frame(piece, 0),
                            camera, piece.position().subtract(camera), false)), context);
                    }
                }
        }
    }

    @Test void twoOpenKansDoNotMoveAWaitingHandForAnAbsentDraw() {
        var v = base(RuleSet.TENHOU_4);
        for (int owner = 0; owner < 4; owner++) {
            var melds = melds(Meld.Type.OPEN_KAN, 2, owner, (owner + 1) % 4);
            var waiting = scene(v, owner, melds, 7, false, false);
            var drawn = scene(v, owner, melds, 8, true, false);
            assertEquals(0, center(waiting, 7), 1e-9, "An absent draw must not force an otherwise centered hand left");
            assertTrue(center(drawn, 7) < 0);
            assertTrue(center(drawn, 7) > -TableScene.HAND_STEP - TableScene.DRAW_GAP);
            var offset = TableGeometry.orient(center(drawn, 7) - center(waiting, 7), 0, 0, owner);
            var before = waiting.stream().filter(p -> p.area() == TableScene.Area.HAND).toList();
            var after = drawn.stream().filter(p -> p.area() == TableScene.Area.HAND).toList();
            for (int i = 0; i < 7; i++) assertEquals(0,
                before.get(i).position().add(offset).distanceTo(after.get(i).position()), 1e-9);
            assertEquals(waiting.stream().filter(p -> p.area() == TableScene.Area.MELD).toList(),
                drawn.stream().filter(p -> p.area() == TableScene.Area.MELD).toList());
        }
    }

    @Test void upgradingAPonOnlyMovesTheHandWhenTheKanReallyWidensTheCorner() {
        var v = base(RuleSet.TENHOU_4);
        for (int owner = 0; owner < 4; owner++) {
            int source = (owner + 1) % 4;
            var pons = scene(v, owner, melds(Meld.Type.PON, 3, owner, source), 5, true, false);
            var added = scene(v, owner, melds(Meld.Type.ADDED_KAN, 3, owner, source), 5, true, false);
            var open = scene(v, owner, melds(Meld.Type.OPEN_KAN, 3, owner, source), 5, true, false);
            assertEquals(center(pons, 4), center(added, 4), 1e-9, "Front-aligned added tiles must not reserve extra horizontal width");
            assertTrue(center(open, 4) < center(pons, 4), "A genuinely wider open kan needs more room");
        }
    }
}
