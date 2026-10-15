package top.skyeyefast.mchjong.client;

import java.util.List;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class TablePresentationTest {
    @Test void thirteenWaitPopupClearsCardsAutomationAndDecisionsAtMinimumSizes() {
        for (int[] bounds : new int[][]{{320, 116, 108, 165}, {640, 148, 108, 310}, {480, 8, 38, 176}}) {
            var box = RiichiHints.layout(bounds[0] - 10, bounds[1], bounds[2], bounds[3], 13, 1);
            assertNotNull(box);
            assertTrue(box.x() >= bounds[1]);
            assertTrue(box.y() >= bounds[2]);
            assertTrue(box.x() + box.width() <= bounds[0] - 10);
            assertEquals(bounds[3], box.y() + box.height());
            assertTrue(Math.abs((bounds[0] - 10 + bounds[1]) / 2.0 - (box.x() + box.width() / 2.0)) <= .5);
            assertTrue(box.tileWidth() >= 5);
            assertTrue(12 * box.step() + box.tileWidth() + 12 <= box.width());
        }
        assertNull(RiichiHints.layout(320, 116, 108, 165, 0, 1));
        assertNull(RiichiHints.layout(320, 116, 108, 130, 13, 1));
        var wrapped = RiichiHints.layout(208, 112, 80, 165, 13, 1);
        assertNotNull(wrapped);
        assertTrue(wrapped.columns() < 13);
        assertEquals(160, wrapped.x() + wrapped.width() / 2);
        assertEquals(165, wrapped.y() + wrapped.height());
        var immersive = RiichiHints.layout(1050, 230, 38, 620, 13, 2);
        assertNotNull(immersive);
        assertEquals(32, immersive.tileWidth());
        assertEquals(13, immersive.columns());
        assertEquals(640, immersive.x() + immersive.width() / 2);
        assertEquals(620, immersive.y() + immersive.height());
    }

    @Test void seatedMeldSummaryPreservesTextAndFallsBackAtNarrowWidths() {
        var melds = java.util.stream.IntStream.range(0, 4).mapToObj(i ->
            new top.skyeyefast.mchjong.engine.Meld(top.skyeyefast.mchjong.engine.Meld.Type.OPEN_QUAD,
                List.of(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3), 1, i * 4)).toList();
        assertEquals(0, RiichiHud.summaryTileWidth(melds, 0, 63));
        int size = RiichiHud.summaryTileWidth(melds, 0, 143);
        assertTrue(size >= 5);
        assertTrue(melds.stream().mapToInt(meld -> TileGui.meldWidth(meld, 0, size) + 2).sum() - 2 <= 143);
        assertTrue(RiichiHud.summaryTileWidth(melds.subList(0, 1), 0, 63) >= 5);
        var added = new top.skyeyefast.mchjong.engine.Meld(top.skyeyefast.mchjong.engine.Meld.Type.ADDED_QUAD,
            List.of(4, 5, 6, 7), 1, 4);
        for (var part : MeldLayout.of(added, 0).parts()) {
            double depth = part.sideways() ? TileDimensions.SMALL.width() : TileDimensions.SMALL.height();
            int y = 28 + (int) Math.round((part.z() + TileDimensions.SMALL.height() / 2.0 - depth / 2) * 7 / TileDimensions.SMALL.width());
            assertTrue(y >= 24, "Added kan must clear the wind/score row");
            assertTrue(y + Math.round(depth * 7 / TileDimensions.SMALL.width()) < RiichiHud.seatedCardHeight(true, 7));
        }
    }

    @Test void defaultViewLooksOverTheHandFromTheStoolAndAimsAtTheFelt() {
        var settings = new TableSettings();
        assertEquals(TableGeometry.STOOL_DISTANCE, settings.cameraDistance);
        double pitch = Math.toRadians(settings.cameraPitch());
        assertEquals(TableGeometry.FELT_Y, settings.cameraHeight
            - Math.tan(pitch) * (settings.cameraDistance - TableSettings.CAMERA_TARGET_Z), 1e-6);
        for (int side = 0; side < 4; side++) {
            var position = TableGeometry.world(net.minecraft.core.BlockPos.ZERO,
                TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, side));
            var stool = TableGeometry.stool(net.minecraft.core.BlockPos.ZERO, side);
            assertEquals(stool.getX() + .5, position.x);
            assertEquals(stool.getZ() + .5, position.z);
        }
    }

    @Test void seatedFovAdaptsToAspectRatioAndPreservesWiderPlayerSettings() {
        var settings = new TableSettings();
        assertTrue(settings.cameraFov(70, 4.0 / 3) > settings.cameraFov(70, 16.0 / 10));
        assertEquals(110, settings.cameraFov(110, 4.0 / 3));
    }

    @Test void immersiveSeatsFollowTheViewerAndSanmaLeavesNoEmptyOpponent() {
        for (int players : new int[]{3, 4}) for (int viewer = 0; viewer < players; viewer++) {
            assertEquals(0, TableBoard.side(viewer, viewer, players));
            assertEquals(1, TableBoard.side((viewer + 1) % players, viewer, players));
            assertEquals(3, TableBoard.side((viewer + players - 1) % players, viewer, players));
            if (players == 4) assertEquals(2, TableBoard.side((viewer + 2) % players, viewer, players));
        }
    }

    @Test void livePresentationUsesPerspectiveWithoutReplayDiscardColors() {
        var id = java.util.UUID.randomUUID();
        var game = TableLayoutTest.startSession(top.skyeyefast.mchjong.engine.RiichiPreset.TENHOU_4, id);
        var state = TableBoardState.live(game.view(id));
        assertFalse(state.dimTsumogiri());
        assertFalse(state.markTedashi());
        var board = new TableBoard(state, 20, 1260, 68, 620, 800, true);
        assertTrue(board.perspective());
    }

    @Test void foregroundPickingIncludesTileBodyAndKeepsTheDrawGapEmpty() {
        var player = new top.skyeyefast.mchjong.engine.RiichiView.Seat(false, "Viewer", true, false, false,
            25000, List.of(0, 4, 8), 8, List.of(), List.of(), List.of(), false, false, false);
        var hand = new TableHand(player, 0, 1280, 752, 58, true);
        var drawn = hand.point(8);
        int halfHeight = Math.round(hand.tileWidth() * TileDimensions.SMALL.height() / TileDimensions.SMALL.width()) / 2;
        assertEquals(8, hand.pick(drawn.x(), drawn.y() + halfHeight + 5, -1),
            "The visible lower body belongs to the drawn tile");
        assertTrue(hand.contains(drawn.x(), drawn.y() + halfHeight + 5));
        var previous = hand.point(4);
        assertEquals(top.skyeyefast.mchjong.engine.Tile.ABSENT, hand.pick(previous.x() + hand.tileWidth() / 2 + 15, previous.y(), -1),
            "The deliberate draw gap must not submit a discard");
        assertEquals(8, hand.pick(drawn.x(), drawn.y() - halfHeight - 8, 8),
            "The raised selection remains clickable");
        assertEquals(hand.point(0).y(), previous.y(), "Resting hand tiles share one baseline");
        assertEquals(previous.y(), drawn.y(), "The drawn slot uses the same tile height");
        assertEquals(drawn.y() - 11, hand.point(8, 8, -1).y(), "Discard starts at the selected tile's raised pose");
        assertEquals(top.skyeyefast.mchjong.engine.Tile.ABSENT,
            hand.pick(drawn.x() + hand.tileWidth() / 2 + 1, drawn.y(), -1), "No invisible side extrusion is clickable");
    }

    @Test void chineseForegroundHandsClearLeftMeldsAndKeepPickingAligned() {
        for (var variant : List.of(top.skyeyefast.mchjong.engine.MahjongVariant.MCR,
                top.skyeyefast.mchjong.engine.MahjongVariant.SICHUAN, top.skyeyefast.mchjong.engine.MahjongVariant.TAIWAN)) {
            int capacity = variant == top.skyeyefast.mchjong.engine.MahjongVariant.TAIWAN ? 17 : 14;
            int flowerLimit = variant == top.skyeyefast.mchjong.engine.MahjongVariant.SICHUAN ? 0 : 8;
            for (int count = 0; count <= capacity / 3; count++) for (int flowers = 0; flowers <= flowerLimit; flowers++) {
                var melds = java.util.stream.IntStream.range(0, count).mapToObj(i ->
                    new top.skyeyefast.mchjong.engine.Meld(top.skyeyefast.mchjong.engine.Meld.Type.OPEN_QUAD,
                        List.of(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3), 3, i * 4)).toList();
                var tiles = java.util.stream.IntStream.range(32, 32 + capacity - 3 * count).boxed().toList();
                var hand = new TableHand(tiles, tiles.getLast(), melds, flowers, 0, 1280, 740, 58, true, variant);
                var first = hand.point(tiles.getFirst());
                if (count == 0 && flowers == 0) assertEquals(640, hand.centerX(), 1, "An empty public area leaves the hand centered");
                assertTrue(hand.centerX() >= 639);
                assertTrue(hand.point(tiles.getLast()).x() + hand.tileWidth() / 2 <= 1256);
                for (int tile : tiles) {
                    var point = hand.point(tile);
                    assertEquals(tile, hand.pick(point.x(), point.y(), -1));
                    assertEquals(first.y(), point.y());
                }
            }
        }
    }

    @Test void immersiveDiscardsLandExactlyAndKeepDistinctUncoloredTrajectories() {
        var start = new TableProjection.Point(600, 680);
        var end = TableProjection.seat(0, -80, 165, new ImmersiveTable(TileDimensions.SMALL).thickness(32));
        for (boolean tsumogiri : new boolean[]{false, true}) {
            assertEquals(start, ImmersiveMotion.interpolate(start, end, ImmersiveMotion.smooth(0), 0, tsumogiri));
            assertEquals(end, ImmersiveMotion.interpolate(start, end, ImmersiveMotion.smooth(1), 1, tsumogiri));
        }
        var tedashi = ImmersiveMotion.interpolate(start, end, .5, .5, false);
        var tsumogiri = ImmersiveMotion.interpolate(start, end, .5, .5, true);
        assertTrue(tedashi.y() < tsumogiri.y(), "Tedashi has a higher arc");
        assertTrue(ImmersiveMotion.duration(true) < ImmersiveMotion.duration(false));
        assertEquals(TileDimensions.SMALL.depth() / TileDimensions.SMALL.width(), new ImmersiveTable(TileDimensions.SMALL).thickness(32) / 32, 1e-6);
        var hidden = new top.skyeyefast.mchjong.engine.RiichiView.Seat(false, "Opponent", true, false, false,
            25000, java.util.Collections.nCopies(14, top.skyeyefast.mchjong.engine.Tile.HIDDEN),
            top.skyeyefast.mchjong.engine.Tile.HIDDEN, List.of(), List.of(), List.of(), false, false, false);
        assertEquals(6.5 * TileDimensions.SMALL.width() * 320 + RiichiTableScene.DRAW_GAP * 160, TableImmersiveTable.discardSourceX(TableBoardState.seat(hidden), 1, 0, 4, 60, true), 1e-6);
        assertEquals(-RiichiTableScene.DRAW_GAP * 160, TableImmersiveTable.discardSourceX(TableBoardState.seat(hidden), 1, 0, 4, 60, false), 1e-6);
    }

    @Test void immersiveCardsStayOnTheFixedCanvasPerimeterAndClearEveryRiver() {
        for (var rules : List.of(top.skyeyefast.mchjong.engine.RiichiPreset.TENHOU_4, top.skyeyefast.mchjong.engine.RiichiPreset.TENHOU_3)) {
            var id = java.util.UUID.randomUUID();
            var game = TableLayoutTest.startSession(rules, id);
            var view = game.view(id);
            int left = 20, right = 1260, top = 68, bottom = 620;
            var board = new TableBoard(TableBoardState.live(view), left, right, top, bottom, 800, true);
            assertTrue(board.perspective());
            var local = board.card(view.viewerSeat());
            assertTrue(local.x() < TableCanvas.WIDTH / 4, "Local plaque belongs on the lower-left perimeter");
            assertTrue(local.y() > bottom - 80);
            for (int seat = 0; seat < rules.players(); seat++) {
                var area = board.riverArea(seat);
                assertTrue(area.x() >= left && area.right() <= right, area.toString());
                assertTrue(area.y() >= top && area.bottom() <= bottom, area.toString());
                for (int other = 0; other < rules.players(); other++) {
                    var seatCard = board.card(other);
                    assertTrue(seatCard.right() <= area.x() || seatCard.x() >= area.right()
                        || seatCard.bottom() <= area.y() || seatCard.y() >= area.bottom(),
                        "Seat cards must clear all rivers: " + other + " " + seatCard + " / " + seat + " " + area);
                }
            }
        }
    }

}
