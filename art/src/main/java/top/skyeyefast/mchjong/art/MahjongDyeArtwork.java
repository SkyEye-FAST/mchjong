package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** A small four-ink pouch; the reusable creative variant has a distinct brass seal. */
final class MahjongDyeArtwork {
    private MahjongDyeArtwork() {}

    static BufferedImage texture(String item) {
        boolean creative = item.equals("creative_mahjong_dye"), red = item.equals("red_dora_dye");
        boolean undo = item.equals("undo_dye");
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(creative ? 0x584368 : red ? 0x75242a : undo ? 0x292931 : 0x493c35));
            g.fillRect(5, 1, 6, 3);
            g.fillPolygon(new int[]{5, 11, 14, 14, 12, 4, 2, 2}, new int[]{4, 4, 7, 13, 15, 15, 13, 7}, 8);
            // Cloth volume: lit left fold, broad middle, and a dark gathered hem.
            g.setColor(new Color(creative ? 0x9678ac : red ? 0xb84d4f : undo ? 0x80868a : 0xb29871));
            g.fillRect(6, 2, 4, 2);
            g.fillRect(5, 5, 6, 9);
            g.fillRect(4, 6, 8, 7);
            g.fillRect(3, 8, 10, 4);
            g.setColor(new Color(creative ? 0xc9b5db : red ? 0xe88a76 : undo ? 0xb5bfbc : 0xe1cd9b));
            g.fillRect(6, 2, 1, 2);
            g.fillRect(4, 6, 2, 2);
            g.fillRect(3, 8, 1, 3);
            g.fillRect(5, 13, 3, 1);
            g.setColor(new Color(creative ? 0x735886 : red ? 0x92323b : undo ? 0x505e64 : 0x82674e));
            g.fillRect(10, 5, 1, 2);
            g.fillRect(12, 8, 1, 4);
            g.fillRect(9, 13, 3, 1);
            // Sewn paper label stays readable at the native inventory pixel size.
            g.setColor(new Color(0xa3947b));
            g.fillRect(4, 7, 8, 6);
            g.setColor(new Color(0xf3eee0));
            g.fillRect(4, 7, 7, 5);
            int[] inks = {0x292931, 0xa72a2e, 0x205431, 0x203e83};
            for (int i = 0; i < inks.length; i++) {
                g.setColor(new Color(undo ? 0x292931 : red ? 0xa72a2e : inks[i]));
                g.fillRect(5 + i % 2 * 3, 7 + i / 2 * 3, 2, 2);
            }
            if (undo) {
                g.setColor(new Color(0xf3eee0));
                g.fillRect(6, 8, 4, 2);
            }
            g.setColor(new Color(creative ? 0xe3c082 : 0x796447));
            g.fillRect(5, 4, 6, 1);
            g.setColor(new Color(creative ? 0xffdfa0 : 0xc7ac76));
            g.fillRect(5, 4, 3, 1);
            g.fillRect(10, 5, 2, 1);
            if (creative) {
                g.setColor(new Color(0xe3c082));
                g.fillRect(11, 1, 3, 1);
                g.fillRect(12, 0, 1, 3);
            }
        } finally { g.dispose(); }
        return image;
    }
}
