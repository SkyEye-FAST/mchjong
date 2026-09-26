package top.skyeyefast.mchjong.art;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Original, deterministic pixel materials. No Minecraft images are read or composited. */
final class FurnitureArtwork {
    static final int SIZE = 16;
    static final Map<String, Integer> WOODS = Map.ofEntries(
        Map.entry("oak", 0xb38b59), Map.entry("spruce", 0x735037),
        Map.entry("birch", 0xd7c59a), Map.entry("jungle", 0xae7d60),
        Map.entry("acacia", 0xb66543), Map.entry("dark_oak", 0x50392d),
        Map.entry("mangrove", 0x884744), Map.entry("cherry", 0xdca7a5),
        Map.entry("pale_oak", 0xded8c7),
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
        // Broken growth rings and a small knot, with long quiet areas between them.
        return pixels(base, new int[]{-30, -15, 0, 12, 24},
            "2233212223342212", "2332212223342212", "2332112223322212", "2332122233222112",
            "2322122332221132", "2322123322210342", "2322123222101342", "2332123222101342",
            "2332123322210342", "2232122332221132", "2232212333222132", "2233212233322132",
            "2233212223322132", "2233211223322132", "2233211223342212", "2233212223342212");
    }

    private static BufferedImage fabric() {
        return pixels(0xe6e6e6, new int[]{-8, -3, 0, 3},
            "2232223122322231", "2122122221221222", "3222312232223122", "2212221222122212",
            "2231223222312232", "1222212212222122", "3122322231223222", "2212221222122212",
            "2232223122322231", "2122122221221222", "3222312232223122", "2212221222122212",
            "2231223222312232", "1222212212222122", "3122322231223222", "2212221222122212");
    }

    private static BufferedImage metal(int base) {
        return pixels(base, new int[]{-23, -10, 0, 15, 30},
            "2223332222211222", "2233433222211222", "2334332222211222", "2333322222111222",
            "2333322222111222", "2333322222101222", "2333222222111222", "2333222222112222",
            "2333222221122222", "2333222221122222", "2333222221122222", "2333322221122222",
            "2333432222112222", "2233332222111222", "2233332222211222", "2223332222211222");
    }

    private static BufferedImage edge() {
        return pixels(0x30383b, new int[]{-6, 0, 7},
            "1111111111111111", "1122111111111111", "1112111111111111", "1111111111001111",
            "1111111111101111", "1100111111111111", "1110111122111111", "1111111112111111",
            "1111111111111111", "1111111111111111", "1111111111122111", "1111111111112111",
            "1111221111111111", "1111121110011111", "1111111111011111", "1111111111111111");
    }

    private static BufferedImage pixels(int base, int[] shades, String... rows) {
        if (rows.length != SIZE) throw new IllegalArgumentException("Material height");
        var image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            if (rows[y].length() != SIZE) throw new IllegalArgumentException("Material width");
            for (int x = 0; x < SIZE; x++) image.setRGB(x, y, shade(base, shades[rows[y].charAt(x) - '0']));
        }
        return image;
    }

    private static int shade(int rgb, int amount) {
        return 0xff000000 | clamp((rgb >>> 16 & 255) + amount) << 16
            | clamp((rgb >>> 8 & 255) + amount) << 8 | clamp((rgb & 255) + amount);
    }

    private static int clamp(int value) { return Math.clamp(value, 0, 255); }
}
