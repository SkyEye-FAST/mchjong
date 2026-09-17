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
        // Broad, broken grain clusters. Each pixel occupies 1/16 of a world block.
        return pixels(base, new int[]{-24, -12, 0, 10, 20},
            "2233221222332212", "2233221222332212", "2332211222333212", "2332212222333212",
            "2332212232333212", "2232212232332212", "2232212332322212", "2233212332222212",
            "2233212332221212", "2233212332221212", "2232212332221222", "2232212322221222",
            "2232212223321222", "2232211223321222", "2233211223322212", "2233221222332212");
    }

    private static BufferedImage fabric() {
        return pixels(0xe8e8e8, new int[]{-16, -7, 0, 8},
            "2222122223222221", "2232221122222322", "2222222222122222", "2122322222222112",
            "2222221223222222", "2322122222223222", "2222222232122222", "2212221222222232",
            "2223222222212222", "2122222322222212", "2222122222232222", "2232222221222222",
            "2222212322222122", "2223222222122222", "2212221222222322", "2222222223222222");
    }

    private static BufferedImage metal(int base) {
        return pixels(base, new int[]{-20, -9, 0, 12, 24},
            "2233222222221122", "2233222222221122", "2333222222221122", "2332222222221122",
            "2332222222221222", "2332222222221222", "2332222222221222", "2232222222221222",
            "2232222222221222", "2232222222221222", "2232222222211222", "2232222222211222",
            "2233222222211222", "2233222222211222", "2233222222221122", "2233222222221122");
    }

    private static BufferedImage edge() {
        return pixels(0x30383b, new int[]{-4, 0, 6},
            "1111111111111111", "1111111111111111", "1111211111111111", "1111111111100111",
            "1111111111111111", "1101111111111111", "1111111121111111", "1111111111111111",
            "1111111111111111", "1111111111111111", "1111111111112111", "1111111111111111",
            "1111121111111111", "1111111110111111", "1111111111111111", "1111111111111111");
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
