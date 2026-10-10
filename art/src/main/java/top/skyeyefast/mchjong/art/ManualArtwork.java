package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** Original teal cloth cover with brass binding and an ivory tile emblem. */
final class ManualArtwork {
    private ManualArtwork() {}

    static BufferedImage texture() {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(0x14272b));
            g.fillRect(2, 1, 12, 14);
            g.setColor(new Color(0xe4dbc5));
            g.fillRect(4, 12, 9, 2);
            g.setColor(new Color(0x365851));
            g.fillRect(3, 1, 11, 11);
            g.setColor(new Color(0x22383d));
            g.fillRect(3, 2, 2, 10);
            g.setColor(new Color(0xe3c082));
            g.fillRect(3, 2, 1, 2);
            g.fillRect(3, 9, 1, 2);
            g.fillRect(12, 2, 1, 1);
            g.fillRect(12, 10, 1, 1);
            g.setColor(new Color(0xc5bea8));
            g.fillRect(7, 4, 4, 6);
            g.setColor(new Color(0xfff1ee));
            g.fillRect(7, 3, 4, 6);
            g.setColor(new Color(0x9c3635));
            g.fillRect(8, 4, 2, 1);
            g.fillRect(8, 6, 2, 1);
            g.setColor(new Color(0xb99454));
            g.fillRect(11, 12, 1, 3);
        } finally {
            g.dispose();
        }
        return image;
    }
}
