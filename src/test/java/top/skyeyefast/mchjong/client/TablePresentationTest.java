package top.skyeyefast.mchjong.client;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class TablePresentationTest {
    @Test void handPitchIsExactlyTheTileWidthWithoutChangingTheDrawGap() {
        assertEquals((double) TileMesh.WIDTH * TableScene.TILE_SCALE, TableScene.HAND_STEP);
        assertEquals(TableScene.RIVER_STEP, TableScene.HAND_STEP);
        assertTrue(TableScene.DRAW_GAP > 0);
    }

    @Test void closerDefaultViewKeepsTheNearCornerTilesInsideAFourByThreeViewport() {
        var settings = new TableSettings();
        assertEquals(2.15, settings.cameraDistance);
        assertEquals(2.4, settings.cameraHeight);
        double pitch = Math.toRadians(settings.cameraPitch());
        for (double x : new double[]{-TableGeometry.FELT_HALF_WIDTH, TableGeometry.FELT_HALF_WIDTH})
            for (double y : new double[]{TableGeometry.FELT_Y, TableGeometry.FELT_Y + TileMesh.HEIGHT * TableScene.TILE_SCALE}) {
                double dy = y - settings.cameraHeight;
                double dz = TableScene.HAND_Z + TileMesh.HEIGHT * TableScene.TILE_SCALE / 2 - settings.cameraDistance;
                double depth = -dy * Math.sin(pitch) - dz * Math.cos(pitch);
                double up = dy * Math.cos(pitch) - dz * Math.sin(pitch);
                double halfHeight = depth * Math.tan(Math.toRadians(35));
                assertTrue(Math.abs(x) < halfHeight * 4 / 3, "The near meld corner must remain visible");
                assertTrue(Math.abs(up) < halfHeight);
            }
        double dy = TableGeometry.FELT_Y - settings.cameraHeight;
        double dz = -TableScene.HAND_Z - settings.cameraDistance;
        double depth = -dy * Math.sin(pitch) - dz * Math.cos(pitch);
        double up = dy * Math.cos(pitch) - dz * Math.sin(pitch);
        double top = .5 - up / (2 * depth * Math.tan(Math.toRadians(35)));
        assertTrue(top < .30, "The far hand should use the upper part of the viewport, rather than leaving it to the sky");
    }

    @Test void recordedVoicesHaveNoDeviceSpeechMode() {
        assertEquals(List.of(TableSettings.VoiceSource.RESOURCE_PACK, TableSettings.VoiceSource.OFF),
            List.of(TableSettings.VoiceSource.values()));
        assertEquals(TableSettings.VoiceSource.RESOURCE_PACK, new TableSettings().voiceSource);
    }

    @Test void machineDigitsAreDistinctAndSupportNegativeScores() {
        var patterns = new HashSet<Integer>();
        for (char digit = '0'; digit <= '9'; digit++) assertTrue(patterns.add(TableIndicator.segments(digit)));
        assertEquals(0x7f, TableIndicator.segments('8'));
        assertEquals(0x40, TableIndicator.segments('-'));
        assertThrows(IllegalArgumentException.class, () -> TableIndicator.segments('A'));
    }
}
