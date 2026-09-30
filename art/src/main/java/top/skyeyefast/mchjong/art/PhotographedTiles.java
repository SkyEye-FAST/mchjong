package top.skyeyefast.mchjong.art;

import java.awt.image.BufferedImage;

/** Extracts 42 printed faces from nine-column photographs and derives three red fives. */
final class PhotographedTiles {
    private PhotographedTiles() {}

    static BufferedImage extract(BufferedImage source, int face) {
        // Photo rows: circles, bamboo, characters, honors, flowers. The last
        // two honor cells and last flower cell are backs or jokers, never faces.
        int ordinary = face >= 34 && face <= 36 ? (face - 34) * 9 + 4 : face;
        int row = ordinary < 27 ? new int[]{2, 0, 1}[ordinary / 9] : ordinary < 34 ? 3 : 4;
        int column = ordinary < 27 ? ordinary % 9 : ordinary < 34
            ? new int[]{0, 1, 2, 3, 6, 5, 4}[ordinary - 27] : ordinary - 37;
        int left = column * source.getWidth() / 9 + 14;
        int top = row * source.getHeight() / 5 + 16;
        int width = source.getWidth() / 9 - 28;
        int height = source.getHeight() / 5 - 32;
        var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int pixel = source.getRGB(left + x, top + y);
            int red = pixel >> 16 & 255, green = pixel >> 8 & 255, blue = pixel & 255;
            // White tile bodies and neutral photographic shading become transparent;
            // the original ink colors and soft engraving edges remain.
            int darkest = Math.min(red, Math.min(green, blue));
            int lightest = Math.max(red, Math.max(green, blue));
            int alpha = Math.clamp((225 - darkest) * 255 / 45, 0, 255);
            if (lightest - darkest < 35)
                alpha = Math.clamp((170 - lightest) * 255 / 50, 0, 255);
            if (face >= 34 && face <= 36) {
                red = 170; green = 22; blue = 32;
            }
            result.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
        }
        return result;
    }
}
