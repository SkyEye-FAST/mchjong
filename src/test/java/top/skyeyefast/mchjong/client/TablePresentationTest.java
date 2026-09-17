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

    @Test void defaultViewMatchesStandingAtTheStoolAndAimsAtTheFelt() {
        var settings = new TableSettings();
        assertEquals(TableGeometry.STOOL_DISTANCE, settings.cameraDistance);
        assertEquals(net.minecraft.world.entity.player.Player.DEFAULT_EYE_HEIGHT, settings.cameraHeight);
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
