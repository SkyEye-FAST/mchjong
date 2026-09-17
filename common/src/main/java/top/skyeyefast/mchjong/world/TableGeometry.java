package top.skyeyefast.mchjong.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** The same seat orientation is used by blocks, riders, meshes and pointer picking. */
public final class TableGeometry {
    public static final int FOOTPRINT_RADIUS = 1;
    public static final int STOOL_DISTANCE = FOOTPRINT_RADIUS + 1;
    public static final double STOOL_HEIGHT = 4.0 / 16.0;
    public static final double FELT_HALF_WIDTH = 21.0 / 16.0;
    public static final double OUTER_HALF_WIDTH = FELT_HALF_WIDTH + 2.0 / 16.0;
    public static final double FELT_Y = 15.0 / 16.0;
    public static final AABB STICK_DRAWER = new AABB(-.48, .65, FELT_HALF_WIDTH + .03,
        .48, .835, OUTER_HALF_WIDTH);
    public static final Direction[] SIDES = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
    private TableGeometry() {}

    public static BlockPos stool(BlockPos center, int seat) { return center.relative(SIDES[seat], STOOL_DISTANCE); }
    public static float yaw(int seat) { return 180 - seat * 90; }

    public static int nearestSide(Vec3 relative) {
        return Math.abs(relative.x) > Math.abs(relative.z) ? relative.x > 0 ? 1 : 3 : relative.z >= 0 ? 0 : 2;
    }

    public static Vec3 orient(double x, double y, double z, int seat) {
        return switch (seat) {
            case 0 -> new Vec3(x, y, z);
            case 1 -> new Vec3(z, y, -x);
            case 2 -> new Vec3(-x, y, -z);
            case 3 -> new Vec3(-z, y, x);
            default -> throw new IllegalArgumentException("Invalid table side");
        };
    }

    public static Vec3 world(BlockPos center, Vec3 relative) {
        return relative.add(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
    }

    public static AABB drawerBounds(int side) {
        return new AABB(orient(STICK_DRAWER.minX, STICK_DRAWER.minY, STICK_DRAWER.minZ, side),
            orient(STICK_DRAWER.maxX, STICK_DRAWER.maxY, STICK_DRAWER.maxZ, side));
    }

    public static int drawerSide(Vec3 relative) {
        for (int side = 0; side < 4; side++) if (drawerBounds(side).inflate(.002).contains(relative)) return side;
        return -1;
    }
}
