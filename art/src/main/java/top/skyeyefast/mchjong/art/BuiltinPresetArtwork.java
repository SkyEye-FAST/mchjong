package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Deterministic artwork for the bundled cosmetic presets. */
final class BuiltinPresetArtwork {
    // Minecraft 1.21.1 Creeper head front: eyes and mouth on the same 8x8 pixel grid.
    private static final String[] CREEPER_FACE = {
        "........", "........", ".##..##.", ".##..##.",
        "...##...", "..####..", "..####..", "..#..#.."
    };
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
                for (int row = 0; row < CREEPER_FACE.length; row++)
                    for (int col = 0; col < CREEPER_FACE[row].length(); col++)
                        if (CREEPER_FACE[row].charAt(col) == '#')
                            fill(g, 64 + col * 16, 112 + row * 16, 16, 16, 0xffeee5d3);
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
