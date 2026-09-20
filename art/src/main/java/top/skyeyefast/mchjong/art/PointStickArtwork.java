package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/** Colored physical sticks: blank, 100, 1,000, 5,000, 10,000 and -10,000 points, in atlas order. */
final class PointStickArtwork {
    static final int WIDTH = 384, ROW_HEIGHT = 32, ROWS = 6;
    private PointStickArtwork() {}

    static BufferedImage texture() {
        var image = new BufferedImage(WIDTH, ROW_HEIGHT * ROWS, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int[] bodies = {0xf4f4ef, 0xf4f4ef, 0x007cbe, 0xefc400, 0xd81427, 0x202326};
            for (int row = 0; row < ROWS; row++) {
                int y = row * ROW_HEIGHT;
                var body = new Color(bodies[row]);
                g.setColor(body);
                g.fillRect(0, y, WIDTH, ROW_HEIGHT);
                // Molded edge highlights and a quiet, recessed marking panel.
                g.setColor(mix(body, Color.WHITE, .16f));
                g.fillRect(0, y + 1, WIDTH, 2);
                g.setColor(mix(body, Color.BLACK, .10f));
                g.fillRect(0, y + ROW_HEIGHT - 2, WIDTH, 2);
                g.setColor(mix(body, Color.WHITE, .04f));
                g.fillRoundRect(116, y + 5, 152, ROW_HEIGHT - 10, 4, 4);
                g.setColor(new Color(row == 1 ? 0xb5bbba : 0xe9eeed));
                switch (row) {
                    case 1 -> {
                        for (float x : new float[]{-22.5f, -7.5f, 7.5f, 22.5f})
                            for (float z : new float[]{-5.5f, 5.5f}) dot(g, y, x, z, 3.5f);
                    }
                    case 2 -> dot(g, y, 0, 0, 5.5f);
                    case 3 -> {
                        dot(g, y, 0, 0, 5.5f);
                        for (float x : new float[]{-11, 11})
                            for (float z : new float[]{-7, 7}) dot(g, y, x, z, 3.5f);
                    }
                    case 4, 5 -> {
                        dot(g, y, 0, 0, 5.5f);
                        for (int side : new int[]{-1, 1}) {
                            dot(g, y, side * 55, 0, 6.5f);
                            dot(g, y, side * 29, 0, 3.5f);
                            for (float z : new float[]{-7, 7}) dot(g, y, side * 14, z, 3.5f);
                        }
                    }
                    default -> { }
                }
            }
        } finally { g.dispose(); }
        return image;
    }

    private static void dot(Graphics2D g, int rowY, float x, float y, float radius) {
        g.fill(new Ellipse2D.Float(WIDTH / 2f + x - radius, rowY + ROW_HEIGHT / 2f + y - radius,
            radius * 2, radius * 2));
    }

    private static Color mix(Color base, Color shade, float amount) {
        return new Color(Math.round(base.getRed() * (1 - amount) + shade.getRed() * amount),
            Math.round(base.getGreen() * (1 - amount) + shade.getGreen() * amount),
            Math.round(base.getBlue() * (1 - amount) + shade.getBlue() * amount));
    }
}
