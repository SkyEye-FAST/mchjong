package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

final class WallGeometryAssertions {
    private WallGeometryAssertions() {}

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
