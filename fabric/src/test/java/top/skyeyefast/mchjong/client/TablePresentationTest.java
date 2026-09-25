package top.skyeyefast.mchjong.client;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class TablePresentationTest {
    @Test void thirteenWaitPopupClearsCardsAutomationAndDecisionsAtMinimumSizes() {
        for (int[] bounds : new int[][]{{320, 116, 108, 165}, {640, 148, 108, 310}, {480, 8, 38, 176}}) {
            var box = TableHints.layout(bounds[0] - 10, bounds[1], bounds[2], bounds[3], 13, 1);
            assertNotNull(box);
            assertTrue(box.x() >= bounds[1]);
            assertTrue(box.y() >= bounds[2]);
            assertTrue(box.x() + box.width() <= bounds[0] - 10);
            assertEquals(bounds[3], box.y() + box.height());
            assertTrue(Math.abs((bounds[0] - 10 + bounds[1]) / 2.0 - (box.x() + box.width() / 2.0)) <= .5);
            assertTrue(box.tileWidth() >= 5);
            assertTrue(12 * box.step() + box.tileWidth() + 12 <= box.width());
        }
        assertNull(TableHints.layout(320, 116, 108, 165, 0, 1));
        assertNull(TableHints.layout(320, 116, 108, 130, 13, 1));
        var wrapped = TableHints.layout(208, 112, 80, 165, 13, 1);
        assertNotNull(wrapped);
        assertTrue(wrapped.columns() < 13);
        assertEquals(160, wrapped.x() + wrapped.width() / 2);
        assertEquals(165, wrapped.y() + wrapped.height());
        var immersive = TableHints.layout(1050, 230, 38, 620, 13, 2);
        assertNotNull(immersive);
        assertEquals(32, immersive.tileWidth());
        assertEquals(13, immersive.columns());
        assertEquals(640, immersive.x() + immersive.width() / 2);
        assertEquals(620, immersive.y() + immersive.height());
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
        assertEquals(24, TableHud.seatedCardHeight(false, 0));
        assertEquals(36, TableHud.seatedCardHeight(true, 0));
        assertEquals(40, TableHud.seatedCardHeight(true, size));
        var added = new top.skyeyefast.mchjong.engine.Meld(top.skyeyefast.mchjong.engine.Meld.Type.ADDED_KAN,
            List.of(4, 5, 6, 7), 1, 4);
        for (var part : MeldLayout.of(added, 0).parts()) {
            double depth = part.sideways() ? TileMesh.WIDTH : TileMesh.HEIGHT;
            int y = 27 + (int) Math.round((part.z() + TileMesh.HEIGHT / 2.0 - depth / 2) * 7 / TileMesh.WIDTH);
            assertTrue(y >= 24, "Added kan must clear the wind/score row");
            assertTrue(y + Math.round(depth * 7 / TileMesh.WIDTH) < TableHud.seatedCardHeight(true, 7));
        }
    }

    @Test void immersiveUsesOneFixedCanvasAtEveryPositiveViewportSize() {
        assertTrue(TableScreen.supportsImmersive(320, 240));
        assertTrue(TableScreen.supportsImmersive(479, 400));
        assertTrue(TableScreen.supportsImmersive(640, 299));
        assertTrue(TableScreen.supportsImmersive(480, 300));
        assertTrue(TableScreen.supportsImmersive(640, 400));
        assertFalse(TableScreen.supportsImmersive(0, 400));
        assertEquals(1280, TableScreen.IMMERSIVE_WIDTH);
        assertEquals(800, TableScreen.IMMERSIVE_HEIGHT);

        var small = TableScreen.immersiveCanvas(320, 240);
        assertEquals(.25, small.scale(), 1e-9);
        assertEquals(0, small.x(), 1e-9);
        assertEquals(20, small.y(), 1e-9);

        var wide = TableScreen.immersiveCanvas(800, 400);
        assertEquals(.5, wide.scale(), 1e-9);
        assertEquals(80, wide.x(), 1e-9);
        assertEquals(0, wide.y(), 1e-9);

        var large = TableScreen.immersiveCanvas(2560, 1600);
        assertEquals(2, large.scale(), 1e-9);
        assertEquals(0, large.x(), 1e-9);
        assertEquals(0, large.y(), 1e-9);
    }
    @Test void handPitchIsExactlyTheTileWidthWithoutChangingTheDrawGap() {
        assertEquals((double) TileMesh.WIDTH * TableScene.TILE_SCALE, TableScene.HAND_STEP);
        assertEquals(TableScene.RIVER_STEP, TableScene.HAND_STEP);
        assertTrue(TableScene.DRAW_GAP > 0);
    }

    @Test void defaultViewLooksOverTheHandFromTheStoolAndAimsAtTheFelt() {
        var settings = new TableSettings();
        assertEquals(TableGeometry.STOOL_DISTANCE, settings.cameraDistance);
        assertEquals(2.10, settings.cameraHeight);
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
        var game = new top.skyeyefast.mchjong.engine.Game(java.util.UUID.randomUUID(),
            top.skyeyefast.mchjong.engine.RuleSet.TENHOU_4, 19);
        assertTrue(game.join(id, "Viewer", 0));
        var state = TableBoardState.live(game.view(id));
        assertFalse(state.dimTsumogiri());
        assertFalse(state.markTedashi());
        var board = new TableBoard(state, 20, 1260, 68, 620, 800, true);
        assertTrue(board.perspective());
    }

    @Test void foregroundPickingIncludesTileBodyAndKeepsTheDrawGapEmpty() {
        var player = new top.skyeyefast.mchjong.engine.TableView.Seat(false, "Viewer", true, false, false,
            25000, List.of(0, 4, 8), 8, List.of(), List.of(), List.of(), false, false);
        var hand = new TableHand(player, 0, 1280, 752, 58, true);
        var drawn = hand.point(8);
        int halfHeight = Math.round(hand.tileWidth() * TileMesh.HEIGHT / TileMesh.WIDTH) / 2;
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

    @Test void immersiveDiscardsLandExactlyAndKeepDistinctUncoloredTrajectories() {
        var start = new TableProjection.Point(600, 680);
        var end = TableProjection.seat(0, -80, 165, ImmersiveTable.thickness(32));
        for (boolean tsumogiri : new boolean[]{false, true}) {
            assertEquals(start, ImmersiveMotion.interpolate(start, end, ImmersiveMotion.smooth(0), 0, tsumogiri));
            assertEquals(end, ImmersiveMotion.interpolate(start, end, ImmersiveMotion.smooth(1), 1, tsumogiri));
        }
        var tedashi = ImmersiveMotion.interpolate(start, end, .5, .5, false);
        var tsumogiri = ImmersiveMotion.interpolate(start, end, .5, .5, true);
        assertTrue(tedashi.y() < tsumogiri.y(), "Tedashi has a higher arc");
        assertTrue(ImmersiveMotion.duration(true) < ImmersiveMotion.duration(false));
        assertEquals(TileMesh.DEPTH / TileMesh.WIDTH, ImmersiveTable.thickness(32) / 32, 1e-6);
        var hidden = new top.skyeyefast.mchjong.engine.TableView.Seat(false, "Opponent", true, false, false,
            25000, java.util.Collections.nCopies(14, top.skyeyefast.mchjong.engine.Tile.HIDDEN),
            top.skyeyefast.mchjong.engine.Tile.HIDDEN, List.of(), List.of(), List.of(), false, false);
        assertEquals(195, ImmersiveTable.discardSourceX(hidden, 1, 60, true));
        assertEquals(0, ImmersiveTable.discardSourceX(hidden, 1, 60, false));
    }

    @Test void recordedVoicesHaveNoDeviceSpeechMode() {
        assertEquals(List.of(TableSettings.VoiceSource.SELECTED, TableSettings.VoiceSource.OFF),
            List.of(TableSettings.VoiceSource.values()));
        assertEquals(TableSettings.VoiceSource.SELECTED, new TableSettings().voiceSource);
        assertEquals(VoicePresets.DEFAULT, new TableSettings().voicePreset);
    }

    @Test void immersiveCardsStayOnTheFixedCanvasPerimeterAndClearEveryRiver() {
        for (var rules : List.of(top.skyeyefast.mchjong.engine.RuleSet.TENHOU_4, top.skyeyefast.mchjong.engine.RuleSet.TENHOU_3)) {
            var id = java.util.UUID.randomUUID();
            var game = new top.skyeyefast.mchjong.engine.Game(java.util.UUID.randomUUID(), rules, 15);
            assertTrue(game.join(id, "Viewer", 0));
            var view = game.view(id);
            int left = 20, right = 1260, top = 68, bottom = 620;
            var board = new TableBoard(TableBoardState.live(view), left, right, top, bottom, 800, true);
            assertTrue(board.perspective());
            var local = board.card(0);
            assertTrue(local.x() < TableScreen.IMMERSIVE_WIDTH / 4, "Local plaque belongs on the lower-left perimeter");
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
                    if (other == seat) continue;
                    var otherRiver = board.riverArea(other);
                    assertTrue(otherRiver.right() <= area.x() || otherRiver.x() >= area.right()
                        || otherRiver.bottom() <= area.y() || otherRiver.y() >= area.bottom(),
                        "Rivers must not overlap: " + seat + " " + area + " / " + other + " " + otherRiver);
                }
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
            seats.set(seat, new top.skyeyefast.mchjong.engine.TableView.Seat(false, "Player", true, false, false,
                25000, List.of(), -1, List.of(), river, List.of(), false, false));
        }
        var view = new top.skyeyefast.mchjong.engine.TableView(base.tableId(), 1, 1, 1, base.rules(),
            top.skyeyefast.mchjong.engine.Game.Phase.TURN, 0, 0, 0, 0, 0, 0, 0,
            base.wallBreak(), base.wall(), null, seats, List.of(), List.of(), "playing", List.of(), List.of(),
            base.timeControl(), List.of(), List.of(), top.skyeyefast.mchjong.engine.HandVisibility.SELF, null, null, base.autoPlay(), false, 1);
        var immersive = new TableBoard(TableBoardState.live(view), 20, 1260, 68, 620, 800, true);
        assertTrue(immersive.riverRowWidth(0, 1) > immersive.riverRowWidth(0, 0),
            "The local river grows subtly toward the foreground");
        assertTrue(immersive.riverRowWidth(2, 3) < immersive.riverRowWidth(2, 0),
            "The opposite river recedes toward the far rail");
        assertTrue(immersive.riverRowWidth(1, 1) <= immersive.riverRowWidth(1, 0),
            "Side rivers retain their own shallow depth plane");
        for (int bottom : new int[]{178, 203}) {
            var board = new TableBoard(TableBoardState.live(view), 8, 472, 38, bottom, bottom);
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
        var player = new top.skyeyefast.mchjong.engine.TableView.Seat(false, "Player", true, false, false, 25000,
            List.of(80, 81), 81, melds, List.of(), List.of(), false, false);
        var immersiveRails = ImmersiveTable.outerRails(player, 0);
        assertEquals(2, immersiveRails.size());
        assertEquals(4, immersiveRails.stream().mapToInt(List::size).sum());
        assertTrue(60 + 18 + immersiveRails.getFirst().stream().mapToInt(m -> TileGui.meldWidth(m, 0, 30) + 5).sum() <= 600,
            "Four kans wrap at the inner corner without shrinking or pushing the standing hand off its rail");
        int width = TableBoard.outerTileWidth(player, 0, 157);
        assertEquals(10, width);
        var rails = TableBoard.meldRails(player, 0, width, 157);
        assertEquals(2, rails.size());
        assertEquals(2, TableBoard.meldRails(player, 0, width, 195).size(),
            "A wider rail must still reserve a visible concealed tile before fitting four kans");
        assertEquals(4, rails.stream().mapToInt(List::size).sum());
        for (var rail : rails) assertTrue(rail.stream().mapToInt(meld -> TileGui.meldWidth(meld, 0, width)).sum() <= 157);
    }

    @Test void tableResultsRetainsMaterialAndBackDye() {
        var rules = top.skyeyefast.mchjong.engine.RuleSet.MAHJONG_SOUL_4.config();
        var seat = new top.skyeyefast.mchjong.engine.TableView.Seat(false, "Player", true, false, false, 25000,
            List.of(), top.skyeyefast.mchjong.engine.Tile.ABSENT, List.of(), List.of(), List.of(), false, false);
        var view = new top.skyeyefast.mchjong.engine.TableView(new java.util.UUID(1, 1), 1, 1, 1, rules,
            top.skyeyefast.mchjong.engine.Game.Phase.HAND_END, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), null,
            List.of(seat), List.of(), List.of(), "ron", List.of(0), List.of(),
            top.skyeyefast.mchjong.engine.TimeControl.DEFAULT, List.of(), List.of(),
            top.skyeyefast.mchjong.engine.HandVisibility.SELF, null, null, null, false, 1);
        var results = new TableResults(null, view, top.skyeyefast.mchjong.item.TileFacePreset.KANSAI,
            top.skyeyefast.mchjong.item.TileMaterial.AMETHYST, net.minecraft.world.item.DyeColor.PURPLE,
            0, 0, 300, 200, 0, TableResults.Page.HAND, 0L, 1);
        assertEquals(top.skyeyefast.mchjong.item.TileMaterial.AMETHYST, results.material());
        assertEquals(net.minecraft.world.item.DyeColor.PURPLE, results.dye());
    }
}
