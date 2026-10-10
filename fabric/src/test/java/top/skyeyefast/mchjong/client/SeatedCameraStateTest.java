package top.skyeyefast.mchjong.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class SeatedCameraStateTest {
    @Test void movementUsesSeatLocalAxesAndFreeLookDoesNotMoveTheEye() {
        var camera = new SeatedCameraState(2, 2.2);
        Vec3 before = camera.localEye();
        camera.look(25, -12);
        assertEquals(before, camera.localEye());
        camera.pan(.2, -.1);
        camera.scroll(2);
        for (int seat = 0; seat < 4; seat++) {
            assertEquals(TableGeometry.orient(.2, 2.2, 1.66, seat).x, camera.eye(seat).x, 1e-6);
            assertEquals(TableGeometry.orient(.2, 2.2, 1.66, seat).z, camera.eye(seat).z, 1e-6);
            assertEquals(TableGeometry.yaw(seat) + 25, camera.yaw(seat), 1e-6);
        }
        camera.scroll(100);
        assertEquals(TableSettings.MIN_CAMERA_DISTANCE, camera.distance());
        camera.scroll(-100);
        assertEquals(TableSettings.MAX_CAMERA_DISTANCE, camera.distance());
    }

    @Test void heightAdjustmentPreservesLookAndDistanceAndRespectsLimits() {
        var camera = new SeatedCameraState(2, 2.1);
        camera.look(25, -12);
        float pitch = camera.pitch();
        camera.raise(2);
        assertEquals(2.2, camera.localEye().y, 1e-6);
        assertEquals(2, camera.distance());
        assertEquals(pitch, camera.pitch());
        for (int seat = 0; seat < 4; seat++) {
            assertEquals(2.2, camera.eye(seat).y, 1e-6);
            assertEquals(TableGeometry.yaw(seat) + 25, camera.yaw(seat), 1e-6);
        }
        camera.raise(-100);
        assertEquals(TableSettings.MIN_CAMERA_HEIGHT, camera.localEye().y);
        camera.raise(100);
        assertEquals(TableSettings.MAX_CAMERA_HEIGHT, camera.localEye().y);
        camera.reset(2, 2.1);
        camera.raise(1);
        assertEquals(2.15, camera.localEye().y, 1e-6);
        camera.reset(2, 2.1);
        assertEquals(new Vec3(0, 2.1, 2), camera.localEye());
    }

    @Test void settingsPreserveLookWhileResetRestoresTheWholePose() {
        var camera = new SeatedCameraState(2, 2.2);
        float defaultPitch = camera.pitch();
        camera.look(20, 10);
        camera.pan(.3, .2);
        camera.configure(2.4, 2.3);
        assertEquals(defaultPitch + 10, camera.pitch());
        assertEquals(TableGeometry.yaw(0) + 20, camera.yaw(0));
        camera.reset(2, 2.2);
        assertEquals(new Vec3(0, 2.2, 2), camera.localEye());
        assertEquals(defaultPitch, camera.pitch());
        assertEquals(TableGeometry.yaw(0), camera.yaw(0));
    }
}
