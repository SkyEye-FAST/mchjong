package top.skyeyefast.mchjong.client;

import net.minecraft.world.phys.Vec3;

/** Seat-local projection only; columns and physical slots belong to the rule's wall layout. */
record TiltedWallLayout(int stacks, double pitch, float angle, double centerX, double centerZ) {
    static final float ANGLE = 12;
    static final double INNER_EDGE_SPACING = 2 * top.skyeyefast.mchjong.world.TableGeometry.FELT_HALF_WIDTH * 31.4 / 85;

    static TiltedWallLayout compact(int stacks, double width, double height) {
        double angle = Math.toRadians(ANGLE);
        // CN107233724B's 30.5 cm wall gap on an 85 cm table is interpreted here
        // as the clear distance between opposite inner faces, along the wall normal.
        // Use 31.4 cm, within its stated +/-1 cm tolerance, for unscaled 50 mm tiles.
        // Centerline spacing includes one full tile height; never measure local z.
        double outward = INNER_EDGE_SPACING / 2 + height / 2;
        // Fixed lift-mouth offset along the tangent, independent of the installed stock.
        double along = .50;
        return new TiltedWallLayout(stacks, width, ANGLE,
            Math.cos(angle) * along - Math.sin(angle) * outward,
            Math.sin(angle) * along + Math.cos(angle) * outward);
    }

    Vec3 position(int column) {
        double offset = ((stacks - 1) / 2.0 - column) * pitch;
        double radians = Math.toRadians(angle);
        return new Vec3(centerX + Math.cos(radians) * offset, 0,
            centerZ + Math.sin(radians) * offset);
    }

    // Minecraft's positive Y rotation sends local +x toward -z.
    float yaw() { return -angle; }
}
