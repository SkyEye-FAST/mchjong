package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** A polished ivory die with recessed pips, drawn without antialiasing at 32 pixels. */
final class DiceArtwork {
    private DiceArtwork() {}

    static BufferedImage texture(int face) {
        if (face < 1 || face > 6) throw new IllegalArgumentException("Dice face");
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(0xb9b09c)); g.fillRect(0, 0, 32, 32);
            g.setColor(new Color(0xd6cfbb)); g.fillRect(1, 1, 30, 30);
            g.setColor(new Color(0xeee9d9)); g.fillRect(2, 2, 28, 28);
            g.setColor(new Color(0xf8f5e9)); g.fillRect(3, 3, 26, 25);
            g.setColor(new Color(0xfffdf3));
            g.fillRect(3, 2, 25, 1); g.fillRect(2, 3, 1, 24);
            g.setColor(new Color(0xe2dbc9));
            g.fillRect(4, 29, 24, 1); g.fillRect(29, 4, 1, 24);
            boolean red = face == 1 || face == 4;
            if (face % 2 == 1) pip(g, 16, 16, face == 1 ? 5 : 3, red);
            if (face >= 2) { pip(g, 8, 8, 3, red); pip(g, 24, 24, 3, red); }
            if (face >= 4) { pip(g, 24, 8, 3, red); pip(g, 8, 24, 3, red); }
            if (face == 6) { pip(g, 8, 16, 3, red); pip(g, 24, 16, 3, red); }
        } finally { g.dispose(); }
        return image;
    }

    private static void pip(Graphics2D g, int x, int y, int radius, boolean red) {
        // The bright lower lip and dark upper recess make the mark read as inset.
        g.setColor(new Color(0xfffdf3));
        g.fillOval(x - radius, y - radius + 1, radius * 2, radius * 2);
        g.setColor(new Color(red ? 0x75262e : 0x20272d));
        g.fillOval(x - radius, y - radius, radius * 2, radius * 2);
        g.setColor(new Color(red ? 0xba3940 : 0x414b52));
        g.fillOval(x - radius + 1, y - radius + 2, radius * 2 - 2, radius * 2 - 3);
        g.setColor(new Color(red ? 0xdf6157 : 0x677379));
        g.fillRect(x - 1, y + radius - 2, 2, 1);
    }
}
