package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

final class WallGeometryAssertions {
    private WallGeometryAssertions() {}

    static void riverClearance(List<Vec3> centers, List<Float> yaws, int seat,
                               double width, double height, double riverZ) {
        double riverInner = riverZ - height / 2;
        double requiredRiverDepth = 4 * height;
        double wallRiverGap = .02;
        assertTrue(riverInner > TableIndicator.HALF_WIDTH, "The river must clear the actual center housing");
        assertTrue(6 * width / 2 < riverInner, "Six-column rivers must clear the neighboring seat's river");
        double innerWallBoundary = Double.POSITIVE_INFINITY;
        for (int tile = 0; tile < centers.size(); tile++) {
            var center = centers.get(tile);
            var x = tangent(yaws.get(tile)).scale(width / 2);
            var z = tangent(yaws.get(tile) - 90).scale(height / 2);
            for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
                var corner = center.add(x.scale(sx)).add(z.scale(sz));
                var local = TableGeometry.orient(corner.x, 0, corner.z, (4 - seat) % 4);
                innerWallBoundary = Math.min(innerWallBoundary, local.z);
                assertTrue(Math.max(Math.abs(local.x), Math.abs(local.z)) < TableGeometry.FELT_HALF_WIDTH,
                    "Every rotated wall corner must stay on the felt");
            }
        }
        assertTrue(innerWallBoundary >= riverInner + requiredRiverDepth + wallRiverGap,
            "Even the inward wall end must clear all four six-tile river rows");
        assertTrue(innerWallBoundary >= TableIndicator.HALF_WIDTH + requiredRiverDepth + wallRiverGap,
            "Center device, river depth and wall gap must fit inside the oriented wall boundary");
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
        assertEquals(12, angle, 1e-5, "The whole wall retains its 12-degree tilt");
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
