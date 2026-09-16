package top.skyeyefast.mchjong.art;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Original, deterministic pixel materials. No Minecraft images are read or composited. */
final class FurnitureArtwork {
    static final int SIZE = 64;
    static final Map<String, Integer> WOODS = Map.ofEntries(
        Map.entry("oak", 0xb38b59), Map.entry("spruce", 0x735037),
        Map.entry("birch", 0xd7c59a), Map.entry("jungle", 0xae7d60),
        Map.entry("acacia", 0xb66543), Map.entry("dark_oak", 0x50392d),
        Map.entry("mangrove", 0x884744), Map.entry("cherry", 0xdca7a5),
        Map.entry("bamboo", 0xc2ac63), Map.entry("crimson", 0x85435f),
        Map.entry("warped", 0x43857f));

    private FurnitureArtwork() {}

    static Map<String, BufferedImage> textures() {
        Map<String, BufferedImage> result = new LinkedHashMap<>();
        WOODS.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(entry -> result.put("wood_" + entry.getKey(), wood(entry.getValue())));
        result.put("felt", fabric());
        result.put("steel", metal(0x65727a));
        result.put("brass", metal(0xc5a46b));
        result.put("edge", edge());
        return result;
    }

    private static BufferedImage wood(int base) {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            // Grain runs along the long face axis. Periodic waves avoid seams when repeating.
            double bend = 1.8 * Math.sin(y * Math.PI / 32) + .7 * Math.sin(y * Math.PI / 16);
            double grain = Math.sin((x + bend) * Math.PI / 8);
            int pore = Math.floorMod(x * 37 + y * 17, 97) == 0 ? -7 : 0;
            int shade = (int) Math.round(5 * grain + 2 * Math.sin((x + bend) * Math.PI / 2)) + pore;
            image.setRGB(x, y, shade(base, shade));
        }
        return image;
    }

    private static BufferedImage fabric() {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int weave = ((x + y) & 1) == 0 ? 2 : -2;
            int thread = x % 4 == 0 ? -3 : y % 4 == 0 ? 1 : 0;
            image.setRGB(x, y, shade(0xf2f2f2, weave + thread));
        }
        return image;
    }

    private static BufferedImage metal(int base) {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            int brushed = Math.floorMod(x * 13, 7) - 3;
            int highlight = (int) Math.round(3 * Math.sin(x * Math.PI / 32));
            image.setRGB(x, y, shade(base, brushed + highlight));
        }
        return image;
    }

    private static BufferedImage edge() {
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++)
            image.setRGB(x, y, shade(0x30383b, ((x + y) & 3) == 0 ? 2 : 0));
        return image;
    }

    private static int shade(int rgb, int amount) {
        return 0xff000000 | clamp((rgb >>> 16 & 255) + amount) << 16
            | clamp((rgb >>> 8 & 255) + amount) << 8 | clamp((rgb & 255) + amount);
    }

    private static int clamp(int value) { return Math.clamp(value, 0, 255); }
}
