package top.skyeyefast.mchjong.client;

import net.minecraft.world.phys.Vec3;

/** Seat-local projection only; columns and physical slots belong to the rule's wall layout. */
record TiltedWallLayout(int stacks, double pitch, float angle, double centerX, double centerZ) {
    static final float ANGLE = 12;

    static TiltedWallLayout compact(int stacks, int adjacentStacks, double width, double height) {
        double along = height;
        // The adjacent wall's short end stops just inside this wall's inner edge.
        double outward = adjacentStacks * width / 2 - along + height / 2 + width / 8;
        double angle = Math.toRadians(ANGLE);
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
