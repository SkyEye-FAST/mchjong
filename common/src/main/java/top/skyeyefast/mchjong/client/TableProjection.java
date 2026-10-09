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

    /** Local font axes on the table plane, including the camera's shear and foreshortening. */
    static org.joml.Matrix4f surface(int side, double x, double z, double height) {
        var origin = seat(side, x, z, height);
        var left = seat(side, x - 1, z, height);
        var right = seat(side, x + 1, z, height);
        var top = seat(side, x, z - 1, height);
        var bottom = seat(side, x, z + 1, height);
        return new org.joml.Matrix4f()
            .m00((right.x() - left.x()) / 2).m01((right.y() - left.y()) / 2)
            .m10((bottom.x() - top.x()) / 2).m11((bottom.y() - top.y()) / 2)
            .m30(origin.x()).m31(origin.y());
    }

    static Point seat(int side, double x, double z, double height) {
        return switch (side) {
            case 1 -> project(z, -x, height);
            case 2 -> project(-x, -z, height);
            case 3 -> project(-z, x, height);
            default -> project(x, z, height);
        };
    }
}
