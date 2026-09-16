package top.skyeyefast.mchjong.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** The same seat orientation is used by blocks, riders, meshes and pointer picking. */
public final class TableGeometry {
    public static final double FELT_Y = 15.0 / 16.0;
    public static final Direction[] SIDES = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};
    private TableGeometry() {}

    public static BlockPos stool(BlockPos center, int seat) { return center.relative(SIDES[seat], 2); }
    public static float yaw(int seat) { return 180 - seat * 90; }

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
}
