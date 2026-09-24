package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Small original sprites for the reusable engraved plate and leather-wrapped workpiece. */
final class CreateArtwork {
    private CreateArtwork() {}

    static BufferedImage texture(String item) {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            if (item.equals("mahjong_printing_plate")) {
                rect(g, 0x455454, 2, 2, 12, 13);
                rect(g, 0xc4ceca, 3, 2, 10, 11);
                rect(g, 0xf0f0dc, 3, 2, 9, 1);
                rect(g, 0x869693, 3, 12, 10, 2);
                for (int y = 4; y <= 8; y += 4) for (int x = 4; x <= 10; x += 3) {
                    rect(g, 0x3d5551, x, y, 2, 3);
                    rect(g, 0x9baea6, x + 1, y, 1, 1);
                }
                rect(g, 0xdbba74, 2, 2, 1, 1);
                rect(g, 0xdbba74, 13, 2, 1, 1);
                rect(g, 0xdbba74, 2, 13, 1, 1);
                rect(g, 0xdbba74, 13, 13, 1, 1);
            } else {
                rect(g, 0x503727, 1, 4, 14, 10);
                rect(g, 0x8b6340, 2, 6, 12, 7);
                rect(g, 0xc39961, 2, 4, 12, 3);
                rect(g, 0xe0bc79, 3, 4, 10, 1);
                rect(g, 0x6a4732, 2, 9, 12, 1);
                rect(g, 0x4b3930, 4, 5, 2, 8);
                rect(g, 0x4b3930, 10, 5, 2, 8);
                rect(g, 0x927255, 4, 5, 1, 7);
                rect(g, 0x927255, 10, 5, 1, 7);
                rect(g, 0xc3cbc1, 7, 8, 2, 3);
                rect(g, 0xf0f0dc, 7, 8, 2, 1);
            }
        } finally { g.dispose(); }
        return image;
    }

    private static void rect(Graphics2D g, int rgb, int x, int y, int width, int height) {
        g.setColor(new Color(rgb));
        g.fillRect(x, y, width, height);
    }
}
