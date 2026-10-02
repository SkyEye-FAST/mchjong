package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

/** The single immersive layout and its reversible, uniformly letterboxed screen transform. */
public record TableCanvas(int screenWidth, int screenHeight, boolean immersive) {
    public static final int WIDTH = 1280;
    public static final int HEIGHT = 800;

    int width() { return immersive ? WIDTH : screenWidth; }
    int height() { return immersive ? HEIGHT : screenHeight; }
    double scale() { return immersive ? Math.min(screenWidth / (double) WIDTH, screenHeight / (double) HEIGHT) : 1; }
    double x() { return (screenWidth - width() * scale()) / 2; }
    double y() { return (screenHeight - height() * scale()) / 2; }
    double localX(double x) { return (x - x()) / scale(); }
    double localY(double y) { return (y - y()) / scale(); }
    boolean contains(double x, double y) {
        return scale() > 0 && x >= x() && x < x() + width() * scale()
            && y >= y() && y < y() + height() * scale();
    }

    void begin(GuiGraphicsExtractor graphics) {
        if (!immersive) return;
        graphics.fill(0, 0, screenWidth, screenHeight, 0xff000000);
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x(), (float) y());
        graphics.pose().scale((float) scale(), (float) scale());
    }

    void end(GuiGraphicsExtractor graphics) { if (immersive) graphics.pose().popMatrix(); }

    static ScreenRectangle card(int side) {
        return switch (side) {
            case 1 -> new ScreenRectangle(1106, 108, 166, 48);
            case 3 -> new ScreenRectangle(8, 108, 166, 48);
            case 2 -> new ScreenRectangle(557, 8, 166, 48);
            default -> new ScreenRectangle(12, 722, 180, 48);
        };
    }
}
