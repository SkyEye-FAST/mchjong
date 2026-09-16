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
        var view = game.view(player);
        int action = java.util.stream.IntStream.range(0, view.actions().size())
            .filter(i -> view.actions().get(i).type() == Action.Type.PRACTICE).findFirst().orElseThrow();
        assertTrue(game.act(player, view.decision(), action));
        return game.view(player);
    }

    private static TableView replace(TableView v, List<Integer> hand, List<Meld> melds, List<Discard> river) {
        var seats = new ArrayList<>(v.seats());
        seats.set(0, new TableView.Seat("Test", true, false, false, 25000, hand, Tile.ABSENT,
            melds, river, List.of(), false, false));
        return new TableView(v.tableId(), v.revision() + 1, v.decision(), v.handNumber(), v.rules(), v.phase(), v.viewerSeat(),
            v.dealer(), v.round(), v.honba(), v.riichiSticks(), v.turn(), v.remaining(), v.wallBreak(), v.wall(), v.focus(),
            seats, v.actions(), v.wins(), v.result(), v.deltas(), v.finalScores(), v.timeControl(), v.clocks(), v.finalRanks(), v.openHands(), v.exitVote());
    }

    @Test void callingKeepsTheConcealedRunCenteredRatherThanPinningItsLeftEdge() {
        for (RuleSet rules : RuleSet.values()) {
            var view = start(rules);
            var hand = List.of(0, 1, 2, 4, 5, 6, 8, 9, 10, 12, 13, 14, 16);
            var before = TableScene.build(replace(view, hand, List.of(), List.of())).stream()
                .filter(piece -> piece.area() == TableScene.Area.HAND && piece.seat() == 0).toList();
            var after = TableScene.build(replace(view, hand.subList(3, hand.size()),
                List.of(new Meld(Meld.Type.PON, List.of(0, 1, 2), 1, 0)), List.of())).stream()
                .filter(piece -> piece.area() == TableScene.Area.HAND && piece.seat() == 0).toList();
            assertEquals(0, before.stream().mapToDouble(p -> p.position().x).average().orElseThrow(), 1e-7);
            assertEquals(0, after.stream().mapToDouble(p -> p.position().x).average().orElseThrow(), 1e-7);
            assertTrue(after.getFirst().position().x > before.getFirst().position().x);
        }
    }

    @Test void completeHandAndDrawAreCenteredWithoutChangingSlotsBetweenDraws() {
        for (int count : new int[]{1, 4, 7, 10, 13}) {
            var base = start(RuleSet.TENHOU_4);
            var tiles = java.util.stream.IntStream.range(80, 80 + count).boxed().toList();
            var before = TableScene.build(replace(base, tiles, List.of(), List.of()));
            var drawn = new ArrayList<>(tiles);
            drawn.add(100);
            var seats = new ArrayList<>(base.seats());
            seats.set(0, new TableView.Seat("Test", true, false, false, 25000, drawn, 100,
                List.of(), List.of(), List.of(), false, false));
            var after = TableScene.build(new TableView(base.tableId(), base.revision(), base.decision(), base.handNumber(),
                base.rules(), base.phase(), base.viewerSeat(), base.dealer(), base.round(), base.honba(), base.riichiSticks(),
                base.turn(), base.remaining(), base.wallBreak(), base.wall(), base.focus(), seats, base.actions(), base.wins(),
                base.result(), base.deltas(), base.finalScores(), base.timeControl(), base.clocks(), base.finalRanks(), base.openHands(), base.exitVote()));
            for (int i = 0; i < count; i++) assertEquals(before.get(i).position(), after.get(i).position());
            assertEquals(TableScene.HAND_STEP + TableScene.DRAW_GAP,
                after.get(count).position().x - after.get(count - 1).position().x, 1e-7);
        }
    }

    @Test void everyMeldTypeFitsBesideTheCenteredHandAtTheRightCorner() {
        var view = start(RuleSet.TENHOU_4);
        for (Meld.Type type : Meld.Type.values()) for (int count = 1; count <= 4; count++) {
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
                double meldLeft = pieces.stream().filter(p -> p.seat() == 0 && p.area() == TableScene.Area.MELD)
                    .mapToDouble(p -> p.position().x - (p.yaw() == 90 ? TileMesh.HEIGHT : TileMesh.WIDTH) * TableScene.TILE_SCALE / 2)
                    .min().orElseThrow();
                double handRight = pieces.stream().filter(p -> p.seat() == 0 && p.area() == TableScene.Area.HAND)
                    .mapToDouble(p -> bounds(p).maxX).max().orElseThrow();
                assertTrue(meldLeft > handRight + TableScene.MELD_GAP, type + " x" + count + " overlaps the hand");
                var calls = pieces.stream().filter(p -> p.seat() == 0 && p.area() == TableScene.Area.MELD).toList();
                assertTrue(calls.stream().allMatch(p -> p.position().z == TableScene.HAND_Z),
                    "Melds belong beside the hand, never in an inner/front rail");
                double right = calls.stream().mapToDouble(p -> bounds(p).maxX).max().orElseThrow();
                assertEquals(TableScene.MELD_RIGHT, right, 1e-5);
                assertTrue(top.skyeyefast.mchjong.world.TableGeometry.FELT_HALF_WIDTH - right < 0.1,
                    "The first meld must stay anchored to the owner's right corner");
                assertEquals(0, pieces.stream().filter(p -> p.seat() == 0 && p.area() == TableScene.Area.HAND)
                    .mapToDouble(p -> p.position().x).average().orElseThrow(), 1e-9);
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
            previous = calls;
        }
    }

    @Test void rotatedSeatsKeepHandsMeldsNorthsAndCompleteWallsInsideTheFeltWithoutIntersection() {
        for (RuleSet rules : RuleSet.values()) for (int count = 0; count <= 4; count++) {
            var v = start(rules);
            var seats = new ArrayList<TableView.Seat>();
            for (int seat = 0; seat < rules.players(); seat++) {
                var melds = new ArrayList<Meld>();
                for (int i = 0; i < count; i++) melds.add(new Meld(Meld.Type.OPEN_KAN,
                    List.of(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3), (seat + 1) % rules.players(), i * 4));
                var hand = java.util.stream.IntStream.range(80, 94 - count * 3).boxed().toList();
                seats.add(new TableView.Seat("Test", true, false, false, 25000, hand, hand.getLast(),
                    melds, List.of(), rules.sanma() ? List.of(120, 121, 122, 123) : List.of(), false, true));
            }
            var view = new TableView(v.tableId(), v.revision(), v.decision(), v.handNumber(), v.rules(), v.phase(), v.viewerSeat(),
                v.dealer(), v.round(), v.honba(), v.riichiSticks(), v.turn(), v.remaining(), v.wallBreak(), v.wall(), v.focus(),
                seats, v.actions(), v.wins(), v.result(), v.deltas(), v.finalScores(), v.timeControl(), v.clocks(), v.finalRanks(), v.openHands(), v.exitVote());
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

    private static net.minecraft.world.phys.AABB bounds(TableScene.Piece piece) {
        double x = TileMesh.WIDTH * TableScene.TILE_SCALE / 2;
        double y = (piece.flat() ? TileMesh.DEPTH : TileMesh.HEIGHT) * TableScene.TILE_SCALE / 2;
        double z = (piece.flat() ? TileMesh.HEIGHT : TileMesh.DEPTH) * TableScene.TILE_SCALE / 2;
        if (Math.floorMod(Math.round(piece.yaw() / 90), 2) == 1) { double swap = x; x = z; z = swap; }
        var p = piece.position();
        return new net.minecraft.world.phys.AABB(p.x - x, p.y - y, p.z - z, p.x + x, p.y + y, p.z + z).deflate(1e-6);
    }

    @Test void meldTilesTouchAndAddedKanStacksOnItsCalledTile() {
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
            assertTrue(added.stacked());
            assertEquals(layout.parts().stream().filter(p -> p.sideways() && !p.stacked()).findFirst().orElseThrow().x(), added.x());
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
            if (i % 6 == 0) assertEquals(TableScene.RIVER_ROW, river.get(i).position().z - river.get(i - 1).position().z, 1e-6);
            else {
                double widths = (river.get(i).yaw() == 90 ? .160 : .104) + (river.get(i - 1).yaw() == 90 ? .160 : .104);
                assertEquals(widths * TableScene.TILE_SCALE / 2,
                    river.get(i).position().x - river.get(i - 1).position().x, 1e-7, "Tiles must touch, including the riichi tile");
                assertEquals(river.get(i).position().z, river.get(i - 1).position().z);
            }
        }
    }

    @Test void completeWallsTouchHorizontallyAndVerticallyForEveryRulesetAndSeat() {
        for (RuleSet rules : RuleSet.values()) {
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
                    river.get(i).position().z - river.get(i - 6).position().z, 1e-7);
            }
        }
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
        settings.toggle(TableSettings.Information.REMAINING);
        settings.showRiver = false;
        var path = directory.resolve("table.json");
        settings.save(path);
        var restored = TableSettings.load(path);
        assertFalse(restored.showRiver);
        assertTrue(restored.show(TableSettings.Information.REMAINING));
        restored.showRiver = true;
        assertFalse(restored.show(TableSettings.Information.REMAINING));
    }
}
