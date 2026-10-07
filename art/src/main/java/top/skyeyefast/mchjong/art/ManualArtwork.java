package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** Original teal cloth cover with brass binding and an ivory tile emblem. */
final class ManualArtwork {
    private ManualArtwork() {}

    /** Handbook examples reuse the same prepared faces as the playing tiles. */
    static java.util.Map<String, BufferedImage> examples(java.nio.file.Path presets) throws java.io.IOException {
        var artwork = new TileArtwork(presets, "kansai");
        var hands = java.util.Map.of(
            "regular", new String[]{"123m", "456m", "789p", "234s", "55p"},
            "taiwan", new String[]{"123m", "456m", "789p", "234s", "678s", "55p"},
            "pairs", new String[]{"11m", "33m", "55p", "77p", "22s", "44s", "66z"},
            "knitted", new String[]{"147m", "258p", "369s", "111z", "55z"},
            "sichuan", new String[]{"1111m", "22m", "33m", "44p", "55p", "66p"},
            "kongs", new String[]{"1111m", "5555p", "234s", "678s", "22p"});
        var result = new java.util.TreeMap<String, BufferedImage>();
        for (var hand : hands.entrySet()) {
            var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            try {
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                for (int group = 0; group < hand.getValue().length; group++) {
                    String tiles = hand.getValue()[group];
                    int base = switch (tiles.charAt(tiles.length() - 1)) { case 'm' -> 0; case 'p' -> 9; case 's' -> 18; default -> 27; };
                    int x = 4 + group % 2 * 100;
                    int y = 8 + group / 2 * 46;
                    for (int i = 0; i < tiles.length() - 1; i++)
                        g.drawImage(artwork.face(base + tiles.charAt(i) - '1'), x + i * 23, y, 23, 35, null);
                }
            } finally { g.dispose(); }
            result.put(hand.getKey(), image);
        }
        return result;
    }

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
