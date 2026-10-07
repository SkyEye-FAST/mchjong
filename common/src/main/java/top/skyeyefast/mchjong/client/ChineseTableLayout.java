package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Fixed-size public tiles use the space actually vacated by the physical wall. */
final class ChineseTableLayout {
    static final double MELD_LEFT = -TableGeometry.FELT_HALF_WIDTH + .015;
    record Bounds(Vec3 center, float yaw, double width, double depth) {}
    record PublicArea(List<Vec3> melds, List<Vec3> flowers, double handLeft) {}
    private ChineseTableLayout() {}

    static double handZ(TileDimensions size) { return TableGeometry.FELT_HALF_WIDTH - size.height() / 2.0 - .015; }
    static Bounds bounds(Vec3 center, float yaw, boolean flat, TileDimensions size) {
        return new Bounds(center, yaw, size.width(), flat ? size.height() : size.depth());
    }

    /** Pack present groups and individual flowers along one left-hand baseline, then wrap. */
    static PublicArea publicArea(List<MeldLayout> layouts, int flowers, int handCount, boolean drawn,
                                 int seat, List<Bounds> occupied, TileDimensions size) {
        double w = size.width(), h = size.height(), baseline = handZ(size);
        double handWidth = handCount * w + (drawn ? RiichiTableScene.DRAW_GAP : 0);
        double handLeft = -(handWidth - w) / 2;
        double right = baseline - h / 2 - .025;
        double leftEdge = -right;
        double cursor = leftEdge;
        int row = 0;
        var meldOrigins = new ArrayList<Vec3>();
        var flowerOrigins = new ArrayList<Vec3>();
        for (int group = 0; group < layouts.size() + flowers; group++) {
            boolean flower = group >= layouts.size();
            var layout = flower ? null : layouts.get(group);
            double width = flower ? w : layout.width();
            double minZ = flower ? -h / 2 : layout.parts().stream()
                .mapToDouble(part -> part.z() - (part.sideways() ? w : h) / 2).min().orElseThrow();
            double maxZ = h / 2;
            Vec3 origin = null;
            for (; row < 5; row++, cursor = leftEdge) {
                double z = baseline - row * (h + .04);
                double end = row == 0 && handCount > 0 ? right - handWidth - .04 : .45;
                for (; cursor + width <= end + 1e-7; cursor += w / 4) {
                    var bound = box(cursor + width / 2, z + (minZ + maxZ) / 2, width, maxZ - minZ, seat);
                    if (!clear(bound, occupied)) continue;
                    origin = new Vec3(cursor, 0, z);
                    occupied.add(bound);
                    break;
                }
                if (origin != null) break;
            }
            if (origin == null) throw new IllegalStateException("No public group fits for seat " + seat);
            if (row == 0 && handCount > 0) handLeft = Math.max(handLeft, cursor + width + .04 + w / 2);
            if (flower) flowerOrigins.add(origin.add(w / 2, 0, 0));
            else meldOrigins.add(origin);
            cursor += width + (flower ? 0 : .02);
        }
        return new PublicArea(List.copyOf(meldOrigins), List.copyOf(flowerOrigins), handLeft);
    }

    static List<List<Vec3>> rivers(List<Integer> counts, List<Bounds> occupied, TileDimensions size) {
        double riverZ = TableIndicator.HALF_WIDTH + size.height() / 2.0 + .005;
        var result = new ArrayList<List<Vec3>>();
        for (int seat = 0; seat < 4; seat++) result.add(new ArrayList<>());
        var nextZ = new double[]{riverZ, riverZ, riverZ, riverZ};
        int rows = counts.stream().mapToInt(count -> (count + 5) / 6).max().orElse(0);
        for (int row = 0; row < rows; row++) for (int seat = 0; seat < 4; seat++) {
            int count = Math.min(6, counts.get(seat) - row * 6);
            if (count <= 0) continue;
            double left = (size.width() / 2.0) - 2.5 * size.width();
            double width = count * (double) size.width();
            double preferred = left + (count - 1) * size.width() / 2;
            Vec3 center = null;
            search: for (int depth = 0; depth < 6; depth++) {
                double z = nextZ[seat] + depth * size.height();
                for (int shift = 0; shift <= 24; shift++) {
                    int offset = shift == 0 ? 0 : (shift % 2 == 1 ? -(shift + 1) / 2 : shift / 2);
                    double x = preferred + offset * size.width();
                    if (clear(box(x, z, width, size.height(), seat), occupied)) {
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
                    double x = preferred + offset * size.width();
                    double z = riverZ + depth * size.height();
                    if (clear(box(x, z, width, size.height(), seat), occupied)) {
                        center = new Vec3(x, 0, z);
                        break search;
                    }
                }
            }
            if (center == null) throw new IllegalStateException("No fixed-size river row fits for seat " + seat + ", row " + row);
            occupied.add(box(center.x, center.z, width, size.height(), seat));
            nextZ[seat] = center.z + size.height();
            for (int column = 0; column < count; column++)
                result.get(seat).add(new Vec3(center.x + (column - (count - 1) / 2.0) * size.width(), 0, center.z));
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
