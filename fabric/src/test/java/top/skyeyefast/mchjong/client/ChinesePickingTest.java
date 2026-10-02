package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.fixture.ChineseGameplayFixtures;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class ChinesePickingTest {
    @Test void dealtMidgameAndLateMcrHandsRemainVisibleAndPickableFromEverySeat() {
        for (int viewer = 0; viewer < 4; viewer++) for (int remaining : new int[]{91, 40, 4}) {
            var scene = McrTableScene.build(ChineseGameplayFixtures.mcr(711, remaining, viewer, ignored -> {}));
            var camera = camera(viewer);
            for (var piece : scene) if (piece.area() == McrTableScene.Area.HAND && piece.seat() == viewer) {
                var pointer = pointer(camera, piece.position());
                double hit = TilePicking.distanceSquared(piece, pointer.origin(), pointer.ray(), false);
                assertTrue(Double.isFinite(hit));
                for (var other : scene) assertTrue(hit <= TilePicking.distanceSquared(other, pointer.origin(), pointer.ray(), false) + 1e-7,
                    "MCR hand center is obscured: " + piece + " by " + other);
            }
        }
    }

    @Test void bothSichuanAssignmentsKeepRealHandsPickableAsWallsEmpty() {
        for (boolean eastWest : List.of(true, false)) for (int viewer = 0; viewer < 4; viewer++)
            for (int remaining : new int[]{55, 25, 4}) {
                var scene = SichuanTableScene.build(ChineseGameplayFixtures.sichuan(711, eastWest, remaining, viewer, false, ignored -> {}));
                var camera = camera(viewer);
                for (var piece : scene) if (piece.area() == SichuanTableScene.Area.HAND && piece.seat() == viewer) {
                    var pointer = pointer(camera, piece.position());
                    double hit = TilePicking.distanceSquared(piece, pointer.origin(), pointer.ray(), false);
                    assertTrue(Double.isFinite(hit));
                    for (var other : scene) assertTrue(hit <= TilePicking.distanceSquared(other, pointer.origin(), pointer.ray(), false) + 1e-7,
                        "Sichuan hand center is obscured: " + piece + " by " + other);
                }
            }
    }

    private static SeatedTableProjection camera(int seat) {
        var origin = TableGeometry.orient(0, 2.10, TableGeometry.STOOL_DISTANCE, seat);
        var target = TableGeometry.orient(0, TableGeometry.FELT_Y, .20, seat);
        var forward = target.subtract(origin).normalize();
        var right = new Vec3(-forward.z, 0, forward.x).normalize();
        return new SeatedTableProjection(origin, forward, right, 240 / (2 * Math.tan(Math.toRadians(80) / 2)), 320, 240);
    }

    private static SeatedTableProjection.Pointer pointer(SeatedTableProjection camera, Vec3 tile) {
        var point = camera.project(tile, .01);
        assertNotNull(point);
        assertTrue(point.x() >= 0 && point.x() < 320 && point.y() >= 0 && point.y() < 240,
            "Physical hand center must stay inside the minimum viewport");
        return camera.pointer(point.x(), point.y());
    }
}
