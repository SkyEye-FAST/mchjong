package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** A small four-ink pouch; the reusable creative variant has a distinct brass seal. */
final class MahjongDyeArtwork {
    private MahjongDyeArtwork() {}

    static BufferedImage texture(String item) {
        boolean creative = item.equals("creative_mahjong_dye"), red = item.equals("red_dora_dye");
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(creative ? 0x584368 : red ? 0x75242a : 0x493c35));
            g.fillRect(5, 1, 6, 3);
            g.fillPolygon(new int[]{5, 11, 14, 14, 12, 4, 2, 2}, new int[]{4, 4, 7, 13, 15, 15, 13, 7}, 8);
            g.setColor(new Color(creative ? 0xc9b5db : 0xd9c8a6));
            g.fillRect(6, 2, 4, 1);
            g.fillRect(4, 6, 8, 8);
            g.fillRect(3, 8, 10, 5);
            g.setColor(new Color(0xf3eee0));
            g.fillRect(4, 7, 8, 6);
            int[] inks = {0x292931, 0xa72a2e, 0x205431, 0x203e83};
            for (int i = 0; i < inks.length; i++) {
                g.setColor(new Color(red ? 0xa72a2e : inks[i]));
                g.fillRect(5 + i % 2 * 3, 7 + i / 2 * 3, 2, 2);
            }
            g.setColor(new Color(creative ? 0xe3c082 : 0x796447));
            g.fillRect(5, 4, 6, 1);
            if (creative) {
                g.fillRect(11, 1, 3, 1);
                g.fillRect(12, 0, 1, 3);
            }
        } finally { g.dispose(); }
        return image;
    }
}
