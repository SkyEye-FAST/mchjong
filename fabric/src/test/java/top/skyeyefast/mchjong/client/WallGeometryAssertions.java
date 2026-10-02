package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

final class WallGeometryAssertions {
    private WallGeometryAssertions() {}

    static void corner(Vec3 first, Vec3 last, Vec3 adjacentEnd, float yaw, double width, double height) {
        var along = tangent(yaw);
        var outward = tangent(yaw - 90);
        double gap = first.subtract(adjacentEnd).dot(outward) - (height + width) / 2;
        assertEquals(width / 8, gap, 1e-8, "Each short end nearly meets the neighboring wall");
        double contact = adjacentEnd.subtract(last).dot(along);
        assertTrue(contact > 0 && contact < first.distanceTo(last), "The corner must meet the wall's side, not float beyond its end");
    }

    static void clearCenter(Vec3 position, float yaw, double width, double height, double depth) {
        var box = new net.minecraft.world.phys.AABB(-width / 2, -depth / 2, -height / 2,
            width / 2, depth / 2, height / 2);
        var camera = new SeatedCameraState(TableGeometry.STOOL_DISTANCE, 2.10);
        for (int seat = 0; seat < 4; seat++) {
            var eye = local(camera.eye(seat).subtract(position), yaw);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                var target = new Vec3(x * .1325, TableGeometry.FELT_Y + .04, z * .1325);
                assertTrue(box.clip(eye, local(target.subtract(position), yaw)).isEmpty(),
                    "A wall tile hides the center panel from the default seated camera");
            }
        }
    }

    private static Vec3 local(Vec3 vector, float yaw) {
        return new Vec3(vector.dot(tangent(yaw)), vector.y, vector.dot(tangent(yaw - 90)));
    }

    static void tiltedSide(List<Vec3> centers, List<Float> yaws, int seat, Vec3 firstSideDirection, double width) {
        var direction = centers.getFirst().subtract(centers.getLast()).normalize();
        var local = TableGeometry.orient(direction.x, 0, direction.z, (4 - seat) % 4);
        assertTrue(local.x > .9 && local.z > .15, "Both local axes must change with the same handedness");
        double angle = Math.toDegrees(Math.atan2(local.z, local.x));
        assertTrue(angle >= 10 && angle <= 15, "The whole wall must have a mild, visible tilt");
        var expected = TableGeometry.orient(firstSideDirection.x, 0, firstSideDirection.z, seat);
        assertEquals(0, direction.distanceTo(expected), 1e-8, "Sides rotate by successive 90-degree turns");
        double pitch = centers.get(0).distanceTo(centers.get(1));
        assertEquals(width, pitch, 1e-8, "Neighboring stacks touch along the wall tangent");
        for (int column = 0; column < centers.size(); column++) {
            var expectedCenter = centers.getFirst().subtract(direction.scale(column * pitch));
            assertEquals(0, expectedCenter.distanceTo(centers.get(column)), 1e-8, "Every stack stays on one straight line");
            double yaw = Math.toRadians(yaws.get(column));
            assertEquals(direction.x, Math.cos(yaw), 1e-8, "Tile width follows the wall tangent");
            assertEquals(direction.z, -Math.sin(yaw), 1e-8);
        }
    }

    static boolean intersects(Vec3 first, float firstYaw, Vec3 second, float secondYaw,
                              double width, double height, double depth) {
        if (Math.abs(first.y - second.y) >= depth - 1e-7) return false;
        var delta = second.subtract(first);
        var firstX = tangent(firstYaw);
        var firstZ = tangent(firstYaw - 90);
        var secondX = tangent(secondYaw);
        var secondZ = tangent(secondYaw - 90);
        for (var axis : List.of(firstX, firstZ, secondX, secondZ)) {
            double radius = (width * (Math.abs(firstX.dot(axis)) + Math.abs(secondX.dot(axis)))
                + height * (Math.abs(firstZ.dot(axis)) + Math.abs(secondZ.dot(axis)))) / 2;
            if (Math.abs(delta.dot(axis)) >= radius - 1e-7) return false;
        }
        return true;
    }

    private static Vec3 tangent(float yaw) {
        double angle = Math.toRadians(yaw);
        return new Vec3(Math.cos(angle), 0, -Math.sin(angle));
    }
}
