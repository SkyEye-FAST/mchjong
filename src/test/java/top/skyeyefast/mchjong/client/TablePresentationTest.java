package top.skyeyefast.mchjong.client;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class TablePresentationTest {
    @Test void thirteenWaitPopupClearsCardsAutomationAndDecisionsAtMinimumSizes() {
        for (int[] bounds : new int[][]{{320, 116, 108, 165}, {640, 148, 108, 310}, {480, 8, 38, 176}}) {
            var box = TableHints.layout(bounds[0] - 10, bounds[1], bounds[2], bounds[3], 13);
            assertNotNull(box);
            assertTrue(box.x() >= bounds[1]);
            assertTrue(box.y() >= bounds[2]);
            assertTrue(box.x() + box.width() <= bounds[0] - 10);
            assertEquals(bounds[3], box.y() + box.height());
            assertTrue(box.tileWidth() >= 5);
            assertTrue(12 * box.step() + box.tileWidth() + 12 <= box.width());
        }
        assertNull(TableHints.layout(320, 116, 108, 165, 0));
        assertNull(TableHints.layout(320, 116, 108, 130, 13));
    }

    @Test void seatedMeldSummaryPreservesTextAndFallsBackAtNarrowWidths() {
        var melds = java.util.stream.IntStream.range(0, 4).mapToObj(i ->
            new top.skyeyefast.mchjong.engine.Meld(top.skyeyefast.mchjong.engine.Meld.Type.OPEN_KAN,
                List.of(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3), 1, i * 4)).toList();
        assertEquals(0, TableHud.summaryTileWidth(melds, 0, 63));
        int size = TableHud.summaryTileWidth(melds, 0, 143);
        assertTrue(size >= 5);
        assertTrue(melds.stream().mapToInt(meld -> TileGui.meldWidth(meld, 0, size) + 2).sum() - 2 <= 143);
        assertTrue(TableHud.summaryTileWidth(melds.subList(0, 1), 0, 63) >= 5);
    }

    @Test void immersiveRequiresEnoughLogicalPixelsForReadableSideRivers() {
        assertFalse(TableScreen.supportsImmersive(320, 240));
        assertFalse(TableScreen.supportsImmersive(479, 400));
        assertFalse(TableScreen.supportsImmersive(640, 299));
        assertTrue(TableScreen.supportsImmersive(480, 300));
        assertTrue(TableScreen.supportsImmersive(640, 400));
    }
    @Test void handPitchIsExactlyTheTileWidthWithoutChangingTheDrawGap() {
        assertEquals((double) TileMesh.WIDTH * TableScene.TILE_SCALE, TableScene.HAND_STEP);
        assertEquals(TableScene.RIVER_STEP, TableScene.HAND_STEP);
        assertTrue(TableScene.DRAW_GAP > 0);
    }

    @Test void defaultViewLooksOverTheHandFromTheStoolAndAimsAtTheFelt() {
        var settings = new TableSettings();
        assertEquals(TableGeometry.STOOL_DISTANCE, settings.cameraDistance);
        assertEquals(2.20, settings.cameraHeight);
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

    @Test void recordedVoicesHaveNoDeviceSpeechMode() {
        assertEquals(List.of(TableSettings.VoiceSource.RESOURCE_PACK, TableSettings.VoiceSource.OFF),
            List.of(TableSettings.VoiceSource.values()));
        assertEquals(TableSettings.VoiceSource.RESOURCE_PACK, new TableSettings().voiceSource);
    }

    @Test void immersiveViewerCardStaysOutsideTheRiverAtBothViewportSizes() {
        for (var rules : List.of(top.skyeyefast.mchjong.engine.RuleSet.TENHOU_4, top.skyeyefast.mchjong.engine.RuleSet.TENHOU_3)) {
            var id = java.util.UUID.randomUUID();
            var game = new top.skyeyefast.mchjong.engine.Game(java.util.UUID.randomUUID(), rules, 15);
            assertTrue(game.join(id, "Viewer", 0));
            var view = game.view(id);
            for (int width : new int[]{480, 640}) {
                int bottom = width == 480 ? 203 : 303;
                var board = new TableBoard(view, 8, width - 8, 38, bottom, bottom);
                var card = board.card(0);
                var river = board.riverArea(0);
                var popup = TableHints.layout(board.card(1).x() - 4,
                    board.card(rules.players() - 1).right() + 4, 38, bottom - 32, 13);
                assertNotNull(popup);
                for (int seat = 0; seat < rules.players(); seat++) {
                    var other = board.card(seat);
                    assertTrue(popup.x() + popup.width() <= other.x() || popup.x() >= other.right()
                        || popup.y() + popup.height() <= other.y() || popup.y() >= other.bottom(),
                        "Wait popup must clear player cards");
                }
                assertTrue(card.right() < river.x(), "The local card must not cover its river");
                for (int seat = 0; seat < rules.players(); seat++) {
                    var area = board.riverArea(seat);
                    assertTrue(area.x() >= 8 && area.right() <= width - 8);
                    assertTrue(area.y() >= 38 && area.bottom() <= bottom);
                    for (int other = 0; other < rules.players(); other++) {
                        var seatCard = board.card(other);
                        assertTrue(seatCard.right() <= area.x() || seatCard.x() >= area.right()
                            || seatCard.bottom() <= area.y() || seatCard.y() >= area.bottom(), "Seat cards must clear all rivers");
                        if (other == seat) continue;
                        var otherRiver = board.riverArea(other);
                        assertTrue(otherRiver.right() <= area.x() || otherRiver.x() >= area.right()
                            || otherRiver.bottom() <= area.y() || otherRiver.y() >= area.bottom(), "Rivers must not overlap");
                    }
                }
                if (rules.players() == 4) assertEquals(board.riverArea(2).height(), board.riverArea(0).height(),
                    "Opposite rivers use the same tile size and row capacity");
            }
        }
    }

    @Test void machineDigitsAreDistinctAndSupportNegativeScores() {
        var patterns = new HashSet<Integer>();
        for (char digit = '0'; digit <= '9'; digit++) assertTrue(patterns.add(TableIndicator.segments(digit)));
        assertEquals(0x7f, TableIndicator.segments('8'));
        assertEquals(0x40, TableIndicator.segments('-'));
        assertThrows(IllegalArgumentException.class, () -> TableIndicator.segments('A'));
    }

    @Test void denseRiversKeepReadablePixelsAndMeldsReflowBeforeShrinking() {
        var rules = top.skyeyefast.mchjong.engine.RuleSet.TENHOU_4;
        var id = java.util.UUID.randomUUID();
        var game = new top.skyeyefast.mchjong.engine.Game(java.util.UUID.randomUUID(), rules, 15);
        game.join(id, "Viewer", 0);
        var base = game.view(id);
        var seats = new java.util.ArrayList<>(base.seats());
        for (int seat = 0; seat < 4; seat++) {
            int rows = seat % 2 == 0 ? 4 : 2;
            var river = java.util.stream.IntStream.range(0, rows * 6)
                .mapToObj(tile -> new top.skyeyefast.mchjong.engine.Discard(tile, tile == 3, false, false)).toList();
            seats.set(seat, new top.skyeyefast.mchjong.engine.TableView.Seat("Player", true, false, false,
                25000, List.of(), -1, List.of(), river, List.of(), false, false));
        }
        var view = new top.skyeyefast.mchjong.engine.TableView(base.tableId(), 1, 1, 1, base.rules(),
            top.skyeyefast.mchjong.engine.Game.Phase.TURN, 0, 0, 0, 0, 0, 0, 0,
            base.wallBreak(), base.wall(), null, seats, List.of(), List.of(), "playing", List.of(), List.of(),
            base.timeControl(), List.of(), List.of(), false, null, null, base.autoPlay(), false, 1);
        for (int bottom : new int[]{178, 203}) {
            var board = new TableBoard(view, 8, 472, 38, bottom, bottom);
            assertTrue(board.riverTileWidth() >= 8);
            assertEquals(bottom == 178, board.scoresOnCards());
            for (int seat = 0; seat < 4; seat++) {
                var area = board.riverArea(seat);
                assertTrue(area.y() >= 38 && area.bottom() <= bottom, area.toString());
                assertTrue(area.x() >= 8 && area.right() <= 472, area.toString());
                for (int other = 0; other < 4; other++) {
                    var card = board.card(other);
                    assertTrue(card.right() <= area.x() || card.x() >= area.right()
                        || card.bottom() <= area.y() || card.y() >= area.bottom(), "Dense score cards overlap rivers");
                }
            }
        }
        var melds = java.util.stream.IntStream.range(0, 4).mapToObj(i ->
            new top.skyeyefast.mchjong.engine.Meld(top.skyeyefast.mchjong.engine.Meld.Type.OPEN_KAN,
                List.of(i * 4, i * 4 + 1, i * 4 + 2, i * 4 + 3), 1, i * 4)).toList();
        var player = new top.skyeyefast.mchjong.engine.TableView.Seat("Player", true, false, false, 25000,
            List.of(80, 81), 81, melds, List.of(), List.of(), false, false);
        int width = TableBoard.outerTileWidth(player, 0, 157);
        assertEquals(10, width);
        var rails = TableBoard.meldRails(player, 0, width, 157);
        assertEquals(2, rails.size());
        assertEquals(2, TableBoard.meldRails(player, 0, width, 195).size(),
            "A wider rail must still reserve a visible concealed tile before fitting four kans");
        assertEquals(4, rails.stream().mapToInt(List::size).sum());
        for (var rail : rails) assertTrue(rail.stream().mapToInt(meld -> TileGui.meldWidth(meld, 0, width)).sum() <= 157);
    }
}
