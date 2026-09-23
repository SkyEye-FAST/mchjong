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
            if (face % 2 == 1) pip(image, 16, 16, face == 1 ? 5 : 3, red);
            if (face >= 2) { pip(image, 8, 8, 3, red); pip(image, 24, 24, 3, red); }
            if (face >= 4) { pip(image, 24, 8, 3, red); pip(image, 8, 24, 3, red); }
            if (face == 6) { pip(image, 8, 16, 3, red); pip(image, 24, 16, 3, red); }
        } finally { g.dispose(); }
        return image;
    }

    private static void pip(BufferedImage image, int cx, int cy, int radius, boolean red) {
        int rOuter = radius == 5 ? 4 : 2;
        int thOuter = radius == 5 ? 20 : 5;
        int rInner = radius == 5 ? 3 : 1;
        int thInner = radius == 5 ? 12 : 2;

        int cWhite = 0xfffffdf3;
        int cDark = 0xff000000 | (red ? 0x75262e : 0x20272d);
        int cMid = 0xff000000 | (red ? 0xba3940 : 0x414b52);
        int cHigh = 0xff000000 | (red ? 0xdf6157 : 0x677379);

        for (int dy = -rOuter; dy <= rOuter; dy++) {
            for (int dx = -rOuter; dx <= rOuter; dx++) {
                if (dx * dx + dy * dy <= thOuter) {
                    image.setRGB(cx + dx, cy + dy + 1, cWhite);
                }
            }
        }
        for (int dy = -rOuter; dy <= rOuter; dy++) {
            for (int dx = -rOuter; dx <= rOuter; dx++) {
                if (dx * dx + dy * dy <= thOuter) {
                    image.setRGB(cx + dx, cy + dy, cDark);
                }
            }
        }
        for (int dy = -rInner; dy <= rInner; dy++) {
            for (int dx = -rInner; dx <= rInner; dx++) {
                if (dx * dx + dy * dy <= thInner) {
                    image.setRGB(cx + dx, cy + dy + (radius == 5 ? 1 : 0), cMid);
                }
            }
        }
        image.setRGB(cx - 1, cy + radius - 2, cHigh);
        image.setRGB(cx, cy + radius - 2, cHigh);
    }
}
