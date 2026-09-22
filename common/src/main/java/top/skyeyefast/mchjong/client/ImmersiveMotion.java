package top.skyeyefast.mchjong.client;

/** Screen-space timing shared by the projected discard solid and its regression checks. */
final class ImmersiveMotion {
    private ImmersiveMotion() {}

    static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    static long duration(boolean tsumogiri) { return tsumogiri ? 320 : 500; }

    static TableProjection.Point interpolate(TableProjection.Point start, TableProjection.Point end,
                                             double progress, double fraction, boolean tsumogiri) {
        return new TableProjection.Point((float) (start.x() + (end.x() - start.x()) * progress),
            (float) (start.y() + (end.y() - start.y()) * progress
                - Math.sin(Math.PI * Math.clamp(fraction, 0, 1)) * (tsumogiri ? 14 : 34)));
    }
}
