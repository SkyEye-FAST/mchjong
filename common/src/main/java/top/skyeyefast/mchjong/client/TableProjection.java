package top.skyeyefast.mchjong.client;

/** One camera for the immersive cloth, tiles, shadows and animation anchors. */
final class TableProjection {
    record Point(float x, float y) {}
    private TableProjection() {}

    static Point project(double x, double z, double height) {
        double scale = scale(z, height);
        return new Point((float) (640 + x * scale), (float) (342 + (.72 * z - .694 * height) * scale));
    }

    static double scale(double z, double height) { return 1 / (1 - (.694 * z + .72 * height) / 1500); }

    static Point seat(int side, double x, double z, double height) {
        return switch (side) {
            case 1 -> project(z, -x, height);
            case 2 -> project(-x, -z, height);
            case 3 -> project(-z, x, height);
            default -> project(x, z, height);
        };
    }
}
