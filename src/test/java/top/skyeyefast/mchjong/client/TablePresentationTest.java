package top.skyeyefast.mchjong.client;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class TablePresentationTest {
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
                var river = board.area(0);
                assertTrue(card.right() < river.x(), "The local card must not cover its river");
                for (int seat = 0; seat < rules.players(); seat++) {
                    var area = board.area(seat);
                    assertTrue(area.x() >= 8 && area.right() <= width - 8);
                    assertTrue(area.y() >= 38 && area.bottom() <= bottom);
                }
                if (rules.players() == 4) assertTrue(board.area(2).height() > board.area(0).height(),
                    "The upper sector must also accommodate the opponent's card and hand");
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
}
