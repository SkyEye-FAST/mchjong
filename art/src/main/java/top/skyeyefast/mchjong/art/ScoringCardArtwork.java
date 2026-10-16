package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** Flat score cards with reproducible pixel numerals, independent of installed fonts. */
final class ScoringCardArtwork {
    static final int[] POINTS = {0, 1, 8, 10, 50, 100, -500};
    private static final String[] DIGITS = {
        "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
        "111100111001111", "111100111101111", "111001001001001", "111101111101111", "111101111001111"
    };
    private ScoringCardArtwork() {}

    static BufferedImage texture(int points) {
        int color = switch (points) {
            case 0, 1 -> 0xeee9da;
            case 8 -> 0x247b47;
            case 10 -> 0x1767b0;
            case 50 -> 0xefc43a;
            case 100 -> 0xba2634;
            case -500 -> 0x24272b;
            default -> throw new IllegalArgumentException("Unknown scoring-card denomination");
        };
        var image = new BufferedImage(64, 40, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(color)); g.fillRect(0, 0, 64, 40);
            g.setColor(new Color(0xf8f1dc)); g.fillRect(3, 3, 58, 34);
            if (points == 0) return image;
            String text = Integer.toString(points);
            int scale = 4, gap = points < 0 ? 2 : 4;
            int x = (64 - text.length() * 3 * scale - (text.length() - 1) * gap) / 2;
            int y = (40 - 5 * scale) / 2;
            g.setColor(new Color(points == 1 || points == 50 ? 0x292c2b : color));
            for (char character : text.toCharArray()) {
                String pixels = character == '-' ? "000000111000000" : DIGITS[character - '0'];
                for (int i = 0; i < 15; i++) if (pixels.charAt(i) == '1')
                    g.fillRect(x + i % 3 * scale, y + i / 3 * scale, scale, scale);
                x += 3 * scale + gap;
            }
        } finally { g.dispose(); }
        return image;
    }
}
