package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Fixed-size public tiles use the space actually vacated by the physical wall. */
final class ChineseTableLayout {
    static final double HAND_Z = TableGeometry.FELT_HALF_WIDTH - TileMesh.HEIGHT / 2.0 - .015;
    static final double MELD_LEFT = -TableGeometry.FELT_HALF_WIDTH + .015;
    static final double RIVER_Z = TableIndicator.HALF_WIDTH + TileMesh.HEIGHT / 2.0 + .005;
    static final double RIVER_X = TileMesh.WIDTH / 2.0;
    record Bounds(Vec3 center, float yaw, double width, double depth) {}
    private ChineseTableLayout() {}

    static Bounds bounds(Vec3 center, float yaw, boolean flat) {
        return new Bounds(center, yaw, TileMesh.WIDTH, flat ? TileMesh.HEIGHT : TileMesh.DEPTH);
    }

    static List<Vec3> melds(List<MeldLayout> layouts, int seat, List<Bounds> occupied) {
        var origins = new ArrayList<Vec3>();
        double left = MELD_LEFT;
        for (var layout : layouts) {
            double minZ = layout.parts().stream().mapToDouble(p -> p.z() - (p.sideways() ? TileMesh.WIDTH : TileMesh.HEIGHT) / 2).min().orElse(0);
            double maxZ = layout.parts().stream().mapToDouble(p -> p.z() + (p.sideways() ? TileMesh.WIDTH : TileMesh.HEIGHT) / 2).max().orElse(0);
            Vec3 origin = null;
            search: for (int shift = 0; shift < 24; shift++) {
                double x = left + shift * TileMesh.WIDTH;
                for (int row = 0; row < 4; row++) {
                    double z = HAND_Z - row * (TileMesh.HEIGHT + .04);
                    if (clear(box(x + layout.width() / 2, z + (minZ + maxZ) / 2, layout.width(), maxZ - minZ, seat), occupied)) {
                        origin = new Vec3(x, 0, z);
                        break search;
                    }
                }
            }
            if (origin == null) throw new IllegalStateException("No fixed-size meld fits for seat " + seat);
            occupied.add(box(origin.x + layout.width() / 2, origin.z + (minZ + maxZ) / 2, layout.width(), maxZ - minZ, seat));
            origins.add(origin);
            left = origin.x + layout.width() + .02;
        }
        return origins;
    }

    static List<List<Vec3>> rivers(List<Integer> counts, List<Bounds> occupied) {
        var result = new ArrayList<List<Vec3>>();
        for (int seat = 0; seat < 4; seat++) result.add(new ArrayList<>());
        var nextZ = new double[]{RIVER_Z, RIVER_Z, RIVER_Z, RIVER_Z};
        int rows = counts.stream().mapToInt(count -> (count + 5) / 6).max().orElse(0);
        for (int row = 0; row < rows; row++) for (int seat = 0; seat < 4; seat++) {
            int count = Math.min(6, counts.get(seat) - row * 6);
            if (count <= 0) continue;
            double left = RIVER_X - 2.5 * TileMesh.WIDTH;
            double width = count * (double) TileMesh.WIDTH;
            double preferred = left + (count - 1) * TileMesh.WIDTH / 2;
            Vec3 center = null;
            search: for (int depth = 0; depth < 6; depth++) {
                double z = nextZ[seat] + depth * TileMesh.HEIGHT;
                for (int shift = 0; shift <= 24; shift++) {
                    int offset = shift == 0 ? 0 : (shift % 2 == 1 ? -(shift + 1) / 2 : shift / 2);
                    double x = preferred + offset * TileMesh.WIDTH;
                    if (clear(box(x, z, width, TileMesh.HEIGHT, seat), occupied)) {
                        center = new Vec3(x, 0, z);
                        break search;
                    }
                }
            }
            // A long blood-battle river can fill its outward lane. Start another six-column
            // bank in free space rather than scaling tiles or crossing the owner's hand.
            if (center == null) {
                search: for (int depth = 0; depth < 5; depth++) for (int shift = 0; shift <= 24; shift++) {
                    int offset = shift == 0 ? 0 : (shift % 2 == 1 ? -(shift + 1) / 2 : shift / 2);
                    double x = preferred + offset * TileMesh.WIDTH;
                    double z = RIVER_Z + depth * TileMesh.HEIGHT;
                    if (clear(box(x, z, width, TileMesh.HEIGHT, seat), occupied)) {
                        center = new Vec3(x, 0, z);
                        break search;
                    }
                }
            }
            if (center == null) throw new IllegalStateException("No fixed-size river row fits for seat " + seat + ", row " + row);
            occupied.add(box(center.x, center.z, width, TileMesh.HEIGHT, seat));
            nextZ[seat] = center.z + TileMesh.HEIGHT;
            for (int column = 0; column < count; column++)
                result.get(seat).add(new Vec3(center.x + (column - (count - 1) / 2.0) * TileMesh.WIDTH, 0, center.z));
        }
        return result;
    }

    private static Bounds box(double x, double z, double width, double depth, int seat) {
        return new Bounds(TableGeometry.orient(x, 0, z, seat), seat * 90, width, depth);
    }

    private static boolean clear(Bounds candidate, List<Bounds> occupied) {
        double angle = Math.toRadians(candidate.yaw());
        double halfX = (candidate.width() * Math.abs(Math.cos(angle)) + candidate.depth() * Math.abs(Math.sin(angle))) / 2;
        double halfZ = (candidate.width() * Math.abs(Math.sin(angle)) + candidate.depth() * Math.abs(Math.cos(angle))) / 2;
        if (Math.abs(candidate.center().x) + halfX > TableGeometry.FELT_HALF_WIDTH - .005
            || Math.abs(candidate.center().z) + halfZ > TableGeometry.FELT_HALF_WIDTH - .005) return false;
        for (var obstacle : occupied) if (intersects(candidate, obstacle)) return false;
        return true;
    }

    private static boolean intersects(Bounds first, Bounds second) {
        var a = tangent(first.yaw()); var b = tangent(first.yaw() - 90);
        var c = tangent(second.yaw()); var d = tangent(second.yaw() - 90);
        var delta = second.center().subtract(first.center());
        for (var axis : List.of(a, b, c, d)) {
            double radius = (first.width() * Math.abs(a.dot(axis)) + first.depth() * Math.abs(b.dot(axis))
                + second.width() * Math.abs(c.dot(axis)) + second.depth() * Math.abs(d.dot(axis))) / 2;
            if (Math.abs(delta.dot(axis)) >= radius - 1e-7) return false;
        }
        return true;
    }

    private static Vec3 tangent(float yaw) {
        double angle = Math.toRadians(yaw);
        return new Vec3(Math.cos(angle), 0, -Math.sin(angle));
    }
}
