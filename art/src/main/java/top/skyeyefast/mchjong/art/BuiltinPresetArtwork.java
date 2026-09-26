package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Deterministic artwork for the bundled cosmetic presets. */
final class BuiltinPresetArtwork {
    private static final String[] MOJANG_BANNER_MARK = {
        "............#.....",
        "............##....",
        "...####.....##....",
        ".#######..#.##....",
        "############......",
        "###############...",
        "#####......####...",
        "####........###...",
        "####.........###..",
        "####..........##..",
        "####..........#...",
        "####..............",
        "#####.............",
        "######............",
        "########......##..",
        ".###############..",
        "...###########...."
    };
    private BuiltinPresetArtwork() {}

    static BufferedImage back(String name) {
        var image = new BufferedImage(256, 384, BufferedImage.TYPE_INT_ARGB);
        var g = graphics(image);
        try {
            if (name.equals("creeper")) {
                int ink = 0xffeee5d3;
                fill(g, 66, 127, 46, 44, ink);
                fill(g, 144, 127, 46, 44, ink);
                fill(g, 106, 174, 44, 32, ink);
                fill(g, 82, 202, 92, 46, ink);
                fill(g, 82, 238, 24, 24, ink);
                fill(g, 150, 238, 24, 24, ink);
            } else if (name.equals("mojang")) {
                // The banner texture stores the front and mirrored back side by side; draw one mark.
                for (int row = 0; row < MOJANG_BANNER_MARK.length; row++) {
                    String pixels = MOJANG_BANNER_MARK[row];
                    for (int col = 0; col < pixels.length(); col++)
                        if (pixels.charAt(col) == '#') fill(g, 64 + col * 8, 124 + row * 8, 8, 8, 0xffeee5d3);
                }
            } else throw new IllegalArgumentException(name);
        } finally { g.dispose(); }
        return image;
    }

    private static Graphics2D graphics(BufferedImage image) {
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

    private static void fill(Graphics2D g, int x, int y, int width, int height, int color) {
        g.setColor(new Color(color, true));
        g.fillRect(x, y, width, height);
    }
}
