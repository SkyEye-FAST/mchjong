package top.skyeyefast.mchjong.art;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Original pixel artwork for the bundled cosmetic presets. */
final class BuiltinPresetArtwork {
    private BuiltinPresetArtwork() {}

    static BufferedImage back(String name) {
        var image = new BufferedImage(256, 384, BufferedImage.TYPE_INT_ARGB);
        var g = graphics(image);
        try {
            if (name.equals("creeper")) {
                // A square face that remains recognizable on dyed and wooden backs.
                fill(g, 40, 104, 176, 176, 0xff234728);
                fill(g, 48, 112, 160, 160, 0xff70ae4a);
                for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++) {
                    int shade = ((x * 13 + y * 7 + x * y) % 5);
                    fill(g, 48 + x * 16, 112 + y * 16, 16, 16,
                        new int[]{0xff79b94d, 0xff5e9b3f, 0xff83bf53, 0xff69a744, 0xff72b04a}[shade]);
                }
                fill(g, 77, 149, 40, 42, 0xff192e21);
                fill(g, 139, 149, 40, 42, 0xff192e21);
                fill(g, 117, 188, 22, 32, 0xff192e21);
                fill(g, 95, 210, 66, 39, 0xff192e21);
                fill(g, 95, 239, 18, 19, 0xff192e21);
                fill(g, 143, 239, 18, 19, 0xff192e21);
            } else if (name.equals("mojang")) {
                fill(g, 36, 82, 184, 220, 0xff8f292d);
                fill(g, 44, 90, 168, 204, 0xffbc373b);
                fill(g, 53, 99, 150, 186, 0xffcb4746);
                // Interlocking angular M emblem, drawn as a compact tile-back mark.
                g.setColor(new Color(0xfff8ebd8));
                g.setStroke(new BasicStroke(17, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
                g.drawPolyline(new int[]{75, 75, 103, 128, 153, 181, 181},
                    new int[]{246, 145, 182, 150, 182, 145, 246}, 7);
                fill(g, 65, 250, 126, 9, 0xfff8ebd8);
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
