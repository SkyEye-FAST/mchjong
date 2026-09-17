package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class HandClearanceTest {
    @Test void waitingAndDrawnHandsUseTheSmallestActualOffsetAndRemainPickableForEveryRuleset() {
        for (RuleSet rules : RuleSet.values()) {
            var base = new Game(UUID.randomUUID(), rules, 15).view(new UUID(10, 15));
            for (int owner = 0; owner < rules.players(); owner++)
                for (Meld.Type type : Meld.Type.values()) for (int count = 0; count <= 4; count++) {
                    var melds = melds(type, count, owner, (owner + 1) % rules.players());
                    int concealed = 13 - count * 3;
                    var tiles = IntStream.range(80, 80 + concealed).boxed().toList();
                    var waiting = pieces(withHand(base, owner, tiles, Tile.ABSENT, melds), owner, TableScene.Area.HAND);
                    var drawn = new ArrayList<>(tiles);
                    drawn.add(100);
                    var after = withHand(base, owner, drawn, 100, melds);
                    var hand = pieces(after, owner, TableScene.Area.HAND);
                    var calls = pieces(after, owner, TableScene.Area.MELD);
                    assertEquals(TableScene.HAND_STEP + TableScene.DRAW_GAP,
                        localX(hand.getLast()) - localX(hand.get(concealed - 1)), 1e-7);
                    assertEquals(calls, pieces(withHand(base, owner, tiles, Tile.ABSENT, melds), owner, TableScene.Area.MELD));

                    double waitingCenter = waiting.stream().mapToDouble(HandClearanceTest::localX).average().orElseThrow();
                    double drawnCenter = hand.subList(0, concealed).stream().mapToDouble(HandClearanceTest::localX).average().orElseThrow();
                    double halfTile = TableScene.RIVER_STEP / 2;
                    double drawnRightAtCenter = (concealed - 1) * TableScene.HAND_STEP / 2
                        + TableScene.HAND_STEP + TableScene.DRAW_GAP + halfTile;
                    double meldLeft = calls.stream().mapToDouble(p -> localX(p)
                        - (p.yaw() == p.seat() * 90 + 90 ? TileMesh.HEIGHT : TileMesh.WIDTH) * TableScene.TILE_SCALE / 2)
                        .min().orElse(Double.POSITIVE_INFINITY);
                    double expectedDrawn = Math.min(0, meldLeft - TableScene.HAND_MELD_GAP - drawnRightAtCenter);
                    double expectedWaiting = Math.min(0, meldLeft - TableScene.HAND_MELD_GAP
                        - (drawnRightAtCenter - TableScene.HAND_STEP - TableScene.DRAW_GAP));
                    String context = rules + " seat " + owner + " " + type + " x" + count;
                    assertEquals(expectedWaiting, waitingCenter, 1e-7, context);
                    assertEquals(expectedDrawn, drawnCenter, 1e-7, context);
                    assertTrue(meldLeft - localX(hand.getLast()) - halfTile >= TableScene.HAND_MELD_GAP - 1e-7);
                    assertTrue(drawnCenter > -TableGeometry.FELT_HALF_WIDTH / 2, "Keep the run near the table center even with four kans");
                    var offset = TableGeometry.orient(drawnCenter - waitingCenter, 0, 0, owner);
                    for (int i = 0; i < concealed; i++) {
                        assertEquals(waiting.get(i).tile(), hand.get(i).tile());
                        assertEquals(0, waiting.get(i).position().add(offset).distanceTo(hand.get(i).position()), 1e-7,
                            "Drawing only translates the constrained run without changing its ordering or spacing");
                    }

                    var settings = new TableSettings();
                    var camera = TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, owner);
                    for (var piece : hand) assertTrue(Double.isFinite(TilePicking.distanceSquared(
                        new TableAnimation.Frame(piece, 0), camera, piece.position().subtract(camera), false)),
                        "Picking must follow the shifted hand at every seat");
                }
        }
    }

    @Test void stackedAddedKansDoNotReserveAnExtraHorizontalTile() {
        var base = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 15).view(new UUID(10, 15));
        var tiles = IntStream.range(80, 88).boxed().toList();
        var pon = pieces(withHand(base, 0, tiles, 87, melds(Meld.Type.PON, 2, 0, 1)), 0, TableScene.Area.HAND);
        var added = pieces(withHand(base, 0, tiles, 87, melds(Meld.Type.ADDED_KAN, 2, 0, 1)), 0, TableScene.Area.HAND);
        var open = pieces(withHand(base, 0, tiles, 87, melds(Meld.Type.OPEN_KAN, 2, 0, 1)), 0, TableScene.Area.HAND);
        assertEquals(pon, added, "A stacked added tile must not displace the hand");
        assertEquals(0, pon.subList(0, 7).stream().mapToDouble(HandClearanceTest::localX).average().orElseThrow(), 1e-7);
        double center = open.subList(0, 7).stream().mapToDouble(HandClearanceTest::localX).average().orElseThrow();
        assertTrue(center < 0 && center > -TableScene.HAND_STEP, "Two open kans need less than one hand slot of clearance");
    }

    private static List<Meld> melds(Meld.Type type, int count, int owner, int source) {
        var result = new ArrayList<Meld>();
        for (int i = 0; i < count; i++) {
            var tiles = type == Meld.Type.CHI ? List.of(i * 12, i * 12 + 4, i * 12 + 8)
                : type == Meld.Type.PON ? List.of(i * 4, i * 4 + 1, i * 4 + 2)
                : List.of(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3);
            result.add(new Meld(type, tiles, type == Meld.Type.CLOSED_KAN ? owner : source,
                type == Meld.Type.CLOSED_KAN ? Tile.ABSENT : tiles.getFirst()));
        }
        return result;
    }

    private static double localX(TableScene.Piece piece) {
        return switch (piece.seat()) {
            case 0 -> piece.position().x;
            case 1 -> -piece.position().z;
            case 2 -> -piece.position().x;
            case 3 -> piece.position().z;
            default -> throw new IllegalArgumentException("Invalid seat");
        };
    }

    private static List<TableScene.Piece> pieces(TableView view, int owner, TableScene.Area area) {
        return TableScene.build(view).stream().filter(p -> p.seat() == owner && p.area() == area).toList();
    }

    private static TableView withHand(TableView v, int owner, List<Integer> hand, int drawn, List<Meld> melds) {
        var seats = new ArrayList<>(v.seats());
        seats.set(owner, new TableView.Seat("Test", true, false, false, 25000, hand, drawn,
            melds, List.of(), List.of(), false, false));
        return new TableView(v.tableId(), v.revision(), v.decision(), v.handNumber(), v.rules(), v.phase(), owner,
            v.dealer(), v.round(), v.honba(), v.riichiSticks(), v.turn(), v.remaining(), v.wallBreak(), v.wall(), v.focus(),
            seats, v.actions(), v.wins(), v.result(), v.deltas(), v.finalScores(), v.timeControl(), v.clocks(), v.finalRanks(), v.openHands(), v.exitVote(), v.handling(), v.autoPlay());
    }
}
