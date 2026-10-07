package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

final class WallGeometryAssertions {
    private WallGeometryAssertions() {}

    static void liftRail(List<Vec3> centers, int seat, double height) {
        var normal = new Vec3(-Math.sin(Math.toRadians(12)), 0, Math.cos(Math.toRadians(12)));
        for (var center : centers) {
            var local = TableGeometry.orient(center.x, 0, center.z, (4 - seat) % 4);
            double innerEdge = local.dot(normal) - height / 2;
            assertTrue(innerEdge >= TableIndicator.HALF_WIDTH + height,
                "Leave the housing and a river row clear along the wall normal");
        }
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
        return intersects(new Solid(first, firstYaw, width, depth, height),
            new Solid(second, secondYaw, width, depth, height));
    }

    record Solid(Vec3 position, float yaw, double width, double height, double depth) {}

    static boolean intersects(Solid first, Solid second) {
        if (Math.abs(first.position().y - second.position().y) >= (first.height() + second.height()) / 2 - 1e-7) return false;
        var delta = second.position().subtract(first.position());
        var firstX = tangent(first.yaw());
        var firstZ = tangent(first.yaw() - 90);
        var secondX = tangent(second.yaw());
        var secondZ = tangent(second.yaw() - 90);
        for (var axis : List.of(firstX, firstZ, secondX, secondZ)) {
            double radius = (first.width() * Math.abs(firstX.dot(axis)) + second.width() * Math.abs(secondX.dot(axis))
                + first.depth() * Math.abs(firstZ.dot(axis)) + second.depth() * Math.abs(secondZ.dot(axis))) / 2;
            if (Math.abs(delta.dot(axis)) >= radius - 1e-7) return false;
        }
        return true;
    }

    static Solid solid(McrTableScene.Piece piece) {
        return new Solid(piece.position(), piece.yaw(), TileDimensions.LARGE.width(),
            (piece.flat() ? TileDimensions.LARGE.depth() : TileDimensions.LARGE.height()),
            (piece.flat() ? TileDimensions.LARGE.height() : TileDimensions.LARGE.depth()));
    }

    static Solid solid(SichuanTableScene.Piece piece) {
        return new Solid(piece.position(), piece.yaw(), TileDimensions.LARGE.width(),
            (piece.flat() ? TileDimensions.LARGE.depth() : TileDimensions.LARGE.height()),
            (piece.flat() ? TileDimensions.LARGE.height() : TileDimensions.LARGE.depth()));
    }

    record PublicPiece(Solid solid, int seat, boolean hand, int group) {}

    static void leftMeldsAndMinimalHandShift(List<PublicPiece> pieces, List<Double> handWidths, double handZ) {
        for (int seat = 0; seat < 4; seat++) {
            int owner = seat;
            var own = pieces.stream().filter(p -> p.seat() == owner).toList();
            var hand = own.stream().filter(PublicPiece::hand).toList();
            double expectedHandEdge = -handWidths.get(seat) / 2;
            double previousRight = Double.NEGATIVE_INFINITY, previousZ = Double.POSITIVE_INFINITY;
            for (int group = 0; group < 12; group++) {
                int number = group;
                var meld = own.stream().filter(p -> p.group() == number).toList();
                if (meld.isEmpty()) continue;
                double left = Double.POSITIVE_INFINITY, right = Double.NEGATIVE_INFINITY;
                for (var part : meld) {
                    var solid = part.solid();
                    var local = TableGeometry.orient(solid.position().x, 0, solid.position().z, (4 - seat) % 4);
                    double angle = Math.toRadians(solid.yaw() - seat * 90);
                    double halfX = (solid.width() * Math.abs(Math.cos(angle)) + solid.depth() * Math.abs(Math.sin(angle))) / 2;
                    double halfZ = (solid.width() * Math.abs(Math.sin(angle)) + solid.depth() * Math.abs(Math.cos(angle))) / 2;
                    left = Math.min(left, local.x - halfX); right = Math.max(right, local.x + halfX);
                    if (!hand.isEmpty() && Math.abs(local.z - handZ) < halfZ + hand.getFirst().solid().depth() / 2 - 1e-8)
                        expectedHandEdge = Math.max(expectedHandEdge, local.x + halfX + .04);
                }
                if (group == 0) assertTrue((left + right) / 2 < 0, "First Chinese meld belongs on the owner's left");
                var first = meld.getFirst().solid().position();
                double rowZ = TableGeometry.orient(first.x, 0, first.z, (4 - seat) % 4).z;
                if (Math.abs(rowZ - previousZ) < .05) assertTrue(left >= previousRight - 1e-7, "Groups extend right within a row");
                previousZ = rowZ;
                previousRight = right;
            }
            if (!hand.isEmpty()) {
                double actual = hand.stream().mapToDouble(p -> {
                    var local = TableGeometry.orient(p.solid().position().x, 0, p.solid().position().z, (4 - owner) % 4);
                    return local.x - p.solid().width() / 2;
                }).min().orElseThrow();
                assertEquals(expectedHandEdge, actual, 1e-8, "Hand moves right only by the actual missing meld clearance");
            }
        }
    }

    static void onFelt(Solid solid) {
        var housing = new Solid(new Vec3(0, TableGeometry.FELT_Y + .0175, 0), 0,
            2 * TableIndicator.HALF_WIDTH, .035, 2 * TableIndicator.HALF_WIDTH);
        assertFalse(intersects(solid, housing), "Physical tiles must clear the actual center device");
        var x = tangent(solid.yaw()).scale(solid.width() / 2);
        var z = tangent(solid.yaw() - 90).scale(solid.depth() / 2);
        for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
            var corner = solid.position().add(x.scale(sx)).add(z.scale(sz));
            assertTrue(Math.max(Math.abs(corner.x), Math.abs(corner.z)) <= TableGeometry.FELT_HALF_WIDTH + 1e-8,
                "Physical piece leaves felt: " + solid);
        }
    }

    static void noIntersections(List<Solid> solids) {
        for (int i = 0; i < solids.size(); i++) for (int j = i + 1; j < solids.size(); j++)
            assertFalse(intersects(solids.get(i), solids.get(j)), "Scene pieces intersect: " + i + ", " + j);
    }

    private static Vec3 tangent(float yaw) {
        double angle = Math.toRadians(yaw);
        return new Vec3(Math.cos(angle), 0, -Math.sin(angle));
    }
}
