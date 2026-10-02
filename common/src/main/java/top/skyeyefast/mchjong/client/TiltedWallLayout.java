package top.skyeyefast.mchjong.client;

import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Seat-local projection only; columns and physical slots belong to the rule's wall layout. */
record TiltedWallLayout(int stacks, double pitch, float angle, double centerX, double centerZ) {
    static final float ANGLE = 12;

    static TiltedWallLayout compact(int stacks, double width, double height) {
        double along = height;
        double angle = Math.toRadians(ANGLE);
        // One automatic-table rail, sized for the longest (18-stack) wall's rotated bounds.
        // Shorter walls change only their tangent span, leaving more room at the corners.
        double halfDepth = 18 * width / 2 * Math.sin(angle) + height / 2 * Math.cos(angle);
        double railZ = TableGeometry.FELT_HALF_WIDTH - .02 - halfDepth;
        double outward = (railZ - Math.sin(angle) * along) / Math.cos(angle);
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
