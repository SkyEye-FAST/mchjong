package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.*;
import static org.junit.jupiter.api.Assertions.*;

class TableLayoutTest {
    private static TableView start(RuleSet rules) {
        UUID player = new UUID(10, 15);
        var game = new Game(UUID.randomUUID(), rules, 15);
        assertTrue(game.join(player, "Test", 0));
        act(game, player, Action.Type.FILL_BOTS);
        act(game, player, Action.Type.BEGIN_SEATING);
        assertTrue(game.join(player, "Test", game.seatOf(player)));
        act(game, player, Action.Type.READY);
        return game.view(null);
    }

    private static void act(Game game, UUID player, Action.Type type) {
        var view = game.view(player);
        for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == type) {
            assertTrue(game.act(player, view.decision(), i));
            return;
        }
        fail("Missing " + type);
    }

    private static TableView replace(TableView v, List<Integer> hand, List<Meld> melds, List<Discard> river) {
        var seats = new ArrayList<>(v.seats());
        seats.set(0, new TableView.Seat(false, "Test", true, false, false, 25000, hand, Tile.ABSENT,
            melds, river, List.of(), false, false));
        return new TableView(v.tableId(), v.revision() + 1, v.decision(), v.handNumber(), v.rules(), v.phase(), v.viewerSeat(),
            v.dealer(), v.round(), v.honba(), v.riichiSticks(), v.turn(), v.remaining(), v.wallBreak(), v.wall(), v.focus(),
            seats, v.actions(), v.wins(), v.result(), v.deltas(), v.finalScores(), v.timeControl(), v.clocks(), v.finalRanks(), v.handVisibility(), v.exitVote(), v.handling(), v.autoPlay(), v.ronBlocked(), v.riichiHan());
    }

    @Test void roomVisibilityOnlyLaysHandsFlatInOpenMode() {
        var id = new UUID(10, 15);
        for (var mode : HandVisibility.values()) {
            var game = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 15);
            assertTrue(game.join(id, "Host", 0));
            if (mode != HandVisibility.SELF) assertTrue(game.configureHandVisibility(id, game.view(id).decision(), mode));
            act(game, id, Action.Type.FILL_BOTS);
            act(game, id, Action.Type.BEGIN_SEATING);
            assertTrue(game.join(id, "Host", game.seatOf(id)));
            act(game, id, Action.Type.READY);
            assertEquals(mode == HandVisibility.OPEN, TableBoardState.live(game.view(id)).layHandsOpen());
            for (var viewer : new UUID[]{null, id}) {
                var pieces = TableScene.build(game.view(viewer)).stream().filter(p -> p.area() == TableScene.Area.HAND).toList();
                assertFalse(pieces.isEmpty());
                assertTrue(pieces.stream().allMatch(p -> p.flat() == (mode == HandVisibility.OPEN)));
            }
        }
    }

    @Test void meldsStayFramedAndBottomAlignedAtTheRightCorner() {
        var view = start(RuleSet.TENHOU_4);
        for (Meld.Type type : Meld.Type.values()) for (int count : new int[]{1, 4}) {
            for (int source = 1; source <= 3; source++) {
                var melds = new ArrayList<Meld>();
                for (int i = 0; i < count; i++) {
                    var tiles = type == Meld.Type.CHI ? List.of(i * 12, i * 12 + 4, i * 12 + 8)
                        : type == Meld.Type.PON ? List.of(i * 4, i * 4 + 1, i * 4 + 2)
                        : List.of(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3);
                    melds.add(new Meld(type, tiles, type == Meld.Type.CLOSED_KAN ? 0 : source,
                        type == Meld.Type.CLOSED_KAN ? Tile.ABSENT : tiles.getFirst()));
                }
                int size = 14 - count * 3;
                var hand = java.util.stream.IntStream.range(100, 100 + size).boxed().toList();
                var pieces = TableScene.build(replace(view, hand, melds, List.of()));
                assertSeatedFraming(pieces);
                var calls = pieces.stream().filter(p -> p.seat() == 0 && p.area() == TableScene.Area.MELD).toList();
                for (var part : calls) {
                    double bottom = TableScene.HAND_Z + TileMesh.HEIGHT * TableScene.TILE_SCALE / 2;
                    if (type == Meld.Type.ADDED_KAN && part.index() % 4 == 3)
                        bottom -= TileMesh.WIDTH * TableScene.TILE_SCALE;
                    assertEquals(bottom, bounds(part).maxZ, 1e-5, "Align the bottom edges; added tiles go in front");
                    assertEquals(top.skyeyefast.mchjong.world.TableGeometry.FELT_Y
                        + TileMesh.DEPTH * TableScene.TILE_SCALE / 2, part.position().y, 1e-7);
                }
                double right = calls.stream().mapToDouble(p -> bounds(p).maxX).max().orElseThrow();
                assertEquals(TableScene.MELD_RIGHT, right, 1e-5);
                assertTrue(top.skyeyefast.mchjong.world.TableGeometry.FELT_HALF_WIDTH - right < 0.1,
                    "The first meld must stay anchored to the owner's right corner");
            }
        }
    }

    @Test void existingMeldsStayAtTheCornerAsAdditionalGroupsAreDeclared() {
        var view = start(RuleSet.TENHOU_4);
        var melds = List.of(
            new Meld(Meld.Type.PON, List.of(0, 1, 2), 1, 0),
            new Meld(Meld.Type.ADDED_KAN, List.of(4, 5, 6, 7), 2, 4),
            new Meld(Meld.Type.CLOSED_KAN, List.of(8, 9, 10, 11), 0, Tile.ABSENT),
            new Meld(Meld.Type.OPEN_KAN, List.of(12, 13, 14, 15), 3, 12));
        List<TableScene.Piece> previous = List.of();
        for (int count = 1; count <= 4; count++) {
            var hand = java.util.stream.IntStream.range(80, 94 - count * 3).boxed().toList();
            var calls = TableScene.build(replace(view, hand, melds.subList(0, count), List.of())).stream()
                .filter(piece -> piece.seat() == 0 && piece.area() == TableScene.Area.MELD).toList();
            assertEquals(previous, calls.subList(0, previous.size()), "Adding a meld must not slide earlier calls away from the corner");
            if (!previous.isEmpty()) assertEquals(
                previous.stream().mapToDouble(p -> bounds(p).minX).min().orElseThrow(),
                calls.subList(previous.size(), calls.size()).stream().mapToDouble(p -> bounds(p).maxX).max().orElseThrow(),
                1e-5, "Adjacent melds touch edge to edge");
            previous = calls;
        }
    }

    @Test void rotatedSeatsKeepHandsMeldsNorthsAndCompleteWallsInsideTheFeltWithoutIntersection() {
        for (RuleSet rules : List.of(RuleSet.TENHOU_4, RuleSet.TENHOU_3)) for (int count = 0; count <= 4; count++) {
            var v = start(rules);
            var seats = new ArrayList<TableView.Seat>();
            for (int seat = 0; seat < rules.players(); seat++) {
                var melds = new ArrayList<Meld>();
                for (int i = 0; i < count; i++) melds.add(new Meld(Meld.Type.OPEN_KAN,
                    List.of(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3), (seat + 1) % rules.players(), i * 4));
                var hand = java.util.stream.IntStream.range(80, 94 - count * 3).boxed().toList();
                seats.add(new TableView.Seat(false, "Test", true, false, false, 25000, hand, hand.getLast(),
                    melds, List.of(), rules.sanma() ? List.of(120, 121, 122, 123) : List.of(), false, true));
            }
            var view = new TableView(v.tableId(), v.revision(), v.decision(), v.handNumber(), v.rules(), v.phase(), v.viewerSeat(),
                v.dealer(), v.round(), v.honba(), v.riichiSticks(), v.turn(), v.remaining(), v.wallBreak(), v.wall(), v.focus(),
                seats, v.actions(), v.wins(), v.result(), v.deltas(), v.finalScores(), v.timeControl(), v.clocks(), v.finalRanks(), v.handVisibility(), v.exitVote(), v.handling(), v.autoPlay(), v.ronBlocked(), v.riichiHan());
            var pieces = new ArrayList<>(TableScene.build(view).stream().filter(p -> p.area() != TableScene.Area.WALL).toList());
            for (int i = 0; i < v.wall().size(); i++) pieces.add(TableScene.wallPiece(v, i, true));
            for (int i = 0; i < pieces.size(); i++) {
                var bounds = bounds(pieces.get(i));
                double edge = top.skyeyefast.mchjong.world.TableGeometry.FELT_HALF_WIDTH;
                assertTrue(bounds.minX >= -edge && bounds.maxX <= edge
                    && bounds.minZ >= -edge && bounds.maxZ <= edge, pieces.get(i).toString());
                for (int j = i + 1; j < pieces.size(); j++) assertFalse(bounds.intersects(bounds(pieces.get(j))),
                    rules + " " + count + ": " + pieces.get(i) + " intersects " + pieces.get(j));
            }
        }
    }

    private static void assertSeatedFraming(List<TableScene.Piece> pieces) {
        var settings = new TableSettings();
        double pitch = Math.toRadians(settings.cameraPitch());
        var forward = new net.minecraft.world.phys.Vec3(0, -Math.sin(pitch), -Math.cos(pitch));
        var up = new net.minecraft.world.phys.Vec3(0, Math.cos(pitch), -Math.sin(pitch));
        var camera = new net.minecraft.world.phys.Vec3(0, settings.cameraHeight, settings.cameraDistance);
        for (int[] viewport : new int[][]{{640, 400}, {320, 240}}) {
            int width = viewport[0], height = viewport[1];
            double fov = settings.cameraFov(70, (double) width / height);
            double focal = height / (2 * Math.tan(Math.toRadians(fov) / 2));
            for (var piece : pieces) {
                if (piece.seat() != 0 || piece.area() != TableScene.Area.HAND && piece.area() != TableScene.Area.MELD) continue;
                var box = bounds(piece).inflate(1e-6);
                for (double x : new double[]{box.minX, box.maxX})
                    for (double y : new double[]{box.minY, box.maxY})
                        for (double z : new double[]{box.minZ, box.maxZ}) {
                            var point = new net.minecraft.world.phys.Vec3(x, y, z).subtract(camera);
                            double depth = point.dot(forward);
                            double screenX = width / 2.0 + point.x * focal / depth;
                            double screenY = height / 2.0 - point.dot(up) * focal / depth;
                            assertTrue(depth > 0 && screenX >= 2 && screenX <= width - 2
                                && screenY >= 64 && screenY <= height - 20,
                                () -> piece + " clipped at " + screenX + "," + screenY + " in " + width + "x" + height);
                        }
            }
        }
    }

    private static net.minecraft.world.phys.AABB bounds(TableScene.Piece piece) {
        double x = TileMesh.WIDTH * TableScene.TILE_SCALE / 2;
        double y = (piece.flat() ? TileMesh.DEPTH : TileMesh.HEIGHT) * TableScene.TILE_SCALE / 2;
        double z = (piece.flat() ? TileMesh.HEIGHT : TileMesh.DEPTH) * TableScene.TILE_SCALE / 2;
        if (Math.floorMod(Math.round(piece.yaw() / 90), 2) == 1) { double swap = x; x = z; z = swap; }
        var p = piece.position();
        return new net.minecraft.world.phys.AABB(p.x - x, p.y - y, p.z - z, p.x + x, p.y + y, p.z + z).deflate(1e-6);
    }

    @Test void meldTilesTouchAndAddedKanLiesInFrontOfItsCalledTile() {
        for (int source = 1; source <= 3; source++) {
            var layout = MeldLayout.of(new Meld(Meld.Type.ADDED_KAN, List.of(0, 1, 2, 3), source, 0), 0);
            for (int i = 1; i < 3; i++) {
                var before = layout.parts().get(i - 1);
                var after = layout.parts().get(i);
                double widths = (before.sideways() ? TileMesh.HEIGHT : TileMesh.WIDTH)
                    + (after.sideways() ? TileMesh.HEIGHT : TileMesh.WIDTH);
                assertEquals(widths / 2, after.x() - before.x(), 1e-7);
            }
            var added = layout.parts().getLast();
            var called = layout.parts().stream().filter(p -> p.tile() == 0).findFirst().orElseThrow();
            assertTrue(added.sideways());
            assertEquals(called.x(), added.x());
            assertEquals(TileMesh.WIDTH, called.z() - added.z(), 1e-7);
            for (var part : layout.parts().subList(0, 3)) assertEquals(TileMesh.HEIGHT / 2.0,
                part.z() + (part.sideways() ? TileMesh.WIDTH : TileMesh.HEIGHT) / 2.0, 1e-7);
        }
    }

    @Test void riverSlotsCloseCalledGapsWithoutLosingDiscardIdentityOrOverlappingRiichi() {
        var discards = new ArrayList<Discard>();
        for (int i = 0; i < 26; i++) discards.add(new Discard(i, i == 2, i == 1 || i == 9, false));
        var view = replace(start(RuleSet.TENHOU_4), List.of(80, 81), List.of(), discards);
        var river = TableScene.build(view).stream().filter(piece -> piece.area() == TableScene.Area.RIVER && piece.seat() == 0).toList();
        assertEquals(24, river.size());
        assertEquals(2, river.get(1).index());
        assertEquals(90, river.get(1).yaw());
        for (int i = 1; i < river.size(); i++) {
            if (i % 6 == 0) assertEquals(TableScene.RIVER_ROW, riverTop(river.get(i)) - riverTop(river.get(i - 1)), 1e-6);
            else {
                double widths = (river.get(i).yaw() == 90 ? .160 : .104) + (river.get(i - 1).yaw() == 90 ? .160 : .104);
                assertEquals(widths * TableScene.TILE_SCALE / 2,
                    river.get(i).position().x - river.get(i - 1).position().x, 1e-7, "Tiles must touch, including the riichi tile");
                assertEquals(riverTop(river.get(i)), riverTop(river.get(i - 1)), 1e-7);
            }
        }
    }

    @Test void completeWallsTouchHorizontallyAndVerticallyForBothPlayerCountsAndEverySeat() {
        for (RuleSet rules : List.of(RuleSet.TENHOU_4, RuleSet.TENHOU_3)) {
            var view = start(rules);
            int stacks = view.wall().size() / (rules.sanma() ? 6 : 8);
            var wall = java.util.stream.IntStream.range(0, view.wall().size())
                .mapToObj(i -> TableScene.wallPiece(view, i, true)).toList();
            for (int seat = 0; seat < rules.players(); seat++) {
                int side = seat;
                var pieces = wall.stream().filter(piece -> piece.seat() == side).toList();
                assertEquals(stacks * 2, pieces.size());
                for (var piece : pieces) {
                    var neighbors = pieces.stream().filter(other -> other != piece)
                        .mapToDouble(other -> piece.position().distanceTo(other.position())).sorted().toArray();
                    assertEquals(TileMesh.DEPTH * TableScene.TILE_SCALE, neighbors[0], 1e-7, "Stack must touch");
                    assertEquals(TileMesh.WIDTH * TableScene.TILE_SCALE, neighbors[1], 1e-7, "Wall must touch");
                }
            }
        }
    }

    @Test void wallDrawsRunClockwiseWhileSeatsAdvanceCounterclockwiseAndTopTilesComeFirst() {
        for (RuleSet rules : List.of(RuleSet.TENHOU_4, RuleSet.TENHOU_3)) {
            var view = start(rules);
            int size = view.wall().size();
            var live = TableScene.wallPiece(view, 0, true);
            var reserve = TableScene.wallPiece(view, size - 1, true);
            assertEquals(live.seat(), reserve.seat());
            var right = top.skyeyefast.mchjong.world.TableGeometry.orient(TableScene.WALL_STEP, 0, 0, live.seat());
            assertEquals(right.x, reserve.position().x - live.position().x, 1e-7);
            assertEquals(right.z, reserve.position().z - live.position().z, 1e-7);
            for (int i = 0; i < size; i += 2) {
                var first = TableScene.wallPiece(view, i, true);
                var second = TableScene.wallPiece(view, i + 1, true);
                var next = TableScene.wallPiece(view, (i + 2) % size, true);
                assertEquals(first.position().x, second.position().x);
                assertEquals(first.position().z, second.position().z);
                assertEquals(TileMesh.DEPTH * TableScene.TILE_SCALE * (i < size - 14 ? 1 : -1),
                    first.position().y - second.position().y, 1e-7);
                if (first.seat() == next.seat()) {
                    var step = top.skyeyefast.mchjong.world.TableGeometry.orient(-TableScene.WALL_STEP, 0, 0, first.seat());
                    assertEquals(step.x, next.position().x - first.position().x, 1e-7);
                    assertEquals(step.z, next.position().z - first.position().z, 1e-7);
                } else assertEquals(Math.floorMod(first.seat() - 1, rules.players()), next.seat());
            }
        }
        var from = top.skyeyefast.mchjong.world.TableGeometry.orient(0, 0, 1, 0);
        var to = top.skyeyefast.mchjong.world.TableGeometry.orient(0, 0, 1, 1);
        assertTrue(from.z * to.x - from.x * to.z > 0, "Seat 0 to 1 is counterclockwise viewed from above");
    }

    @Test void normalRiverRowsTouchAndRiichiAtEveryColumnPreservesEdgeContact() {
        for (int riichi = 0; riichi < 12; riichi++) {
            var discards = new ArrayList<Discard>();
            for (int i = 0; i < 18; i++) discards.add(new Discard(i, i == riichi, false, false));
            var river = TableScene.build(replace(start(RuleSet.TENHOU_4), List.of(), List.of(), discards)).stream()
                .filter(piece -> piece.area() == TableScene.Area.RIVER && piece.seat() == 0).toList();
            for (int i = 0; i < river.size(); i++) {
                if (i % 6 > 0) {
                    double width = (i == riichi ? TileMesh.HEIGHT : TileMesh.WIDTH)
                        + (i - 1 == riichi ? TileMesh.HEIGHT : TileMesh.WIDTH);
                    assertEquals(width * TableScene.TILE_SCALE / 2, river.get(i).position().x - river.get(i - 1).position().x, 1e-7);
                }
                if (i >= 6) assertEquals(TileMesh.HEIGHT * TableScene.TILE_SCALE,
                    riverTop(river.get(i)) - riverTop(river.get(i - 6)), 1e-7);
            }
        }
    }

    private static double riverTop(TableScene.Piece piece) {
        return piece.position().z - (piece.yaw() == 90 ? TileMesh.WIDTH : TileMesh.HEIGHT) * TableScene.TILE_SCALE / 2.0;
    }

    @Test void hidingRiversAlwaysShowsRemainingTilesAndResetRestoresDefaults() {
        var settings = new TableSettings();
        settings.toggle(TableSettings.Information.REMAINING);
        assertFalse(settings.show(TableSettings.Information.REMAINING));
        settings.showRiver = false;
        assertTrue(settings.show(TableSettings.Information.REMAINING));
        settings.toggle(TableSettings.Information.REMAINING);
        settings.toggle(TableSettings.Information.REMAINING);
        assertTrue(settings.show(TableSettings.Information.REMAINING));
        settings.showRiver = true;
        assertFalse(settings.show(TableSettings.Information.REMAINING));
        settings.reset();
        assertTrue(settings.showRiver);
        assertTrue(settings.show(TableSettings.Information.REMAINING));
    }

    @Test void riverVisibilityAndRemainingPreferencesSurviveSaving(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var settings = new TableSettings();
        assertEquals(TableSettings.TileLabels.NAME, settings.tileLabels);
        assertFalse(settings.convenienceHints);
        assertTrue(settings.autoSeat);
        settings.convenienceHints = true;
        settings.autoSeat = false;
        settings.tileLabels = TableSettings.TileLabels.MPSZ;
        settings.toggle(TableSettings.Information.REMAINING);
        settings.showRiver = false;
        var path = directory.resolve("table.toml");
        settings.save(path);
        var restored = TableSettings.load(path);
        assertEquals(TableSettings.TileLabels.MPSZ, restored.tileLabels);
        assertTrue(restored.convenienceHints);
        assertFalse(restored.autoSeat);
        assertFalse(restored.showRiver);
        assertTrue(restored.show(TableSettings.Information.REMAINING));
        restored.showRiver = true;
        assertFalse(restored.show(TableSettings.Information.REMAINING));
        restored.reset();
        assertEquals(TableSettings.TileLabels.NAME, restored.tileLabels);
        assertFalse(restored.convenienceHints);
        assertTrue(restored.autoSeat);
    }
}
