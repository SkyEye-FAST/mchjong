package top.skyeyefast.mchjong.client;

import net.minecraft.world.phys.Vec3;

/** Seat-local projection only; columns and physical slots belong to the rule's wall layout. */
record TiltedWallLayout(int stacks, double pitch, float angle, double centerX, double centerZ) {
    static final float ANGLE = 12;
    static TiltedWallLayout compact(int stacks, double width, double height) {
        double angle = Math.toRadians(ANGLE);
        double c = Math.cos(angle), s = Math.sin(angle);
        double halfLength = stacks * width / 2;
        // Perpendicular neighboring ends clear by .012 along their own axes.
        double corner = halfLength + height / 2 + .012;
        double limit = top.skyeyefast.mchjong.world.TableGeometry.FELT_HALF_WIDTH - .045;
        double outward = Math.max(TableIndicator.HALF_WIDTH + height * 1.5 + .06,
            (c * (corner + halfLength) + s * height / 2 - limit) / (c + s));
        double along = corner - outward;
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
