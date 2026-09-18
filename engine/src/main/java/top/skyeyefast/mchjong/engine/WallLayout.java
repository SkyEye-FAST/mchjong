package top.skyeyefast.mchjong.engine;

/** Public wall positions only: drawing moves clockwise, opposite to the seat order. */
public final class WallLayout {
    private WallLayout() {}

    public static int stack(int index, int breakOffset, int size) {
        return Math.floorMod(breakOffset / 2 - index / 2, size / 2);
    }

    public static int side(int index, int breakOffset, int size, int players) {
        return stack(index, breakOffset, size) / (size / (2 * players));
    }
}
