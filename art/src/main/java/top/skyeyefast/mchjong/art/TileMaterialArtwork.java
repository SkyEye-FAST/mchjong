package top.skyeyefast.mchjong.art;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Original 16-pixel relief; runtime material colors tint these neutral palettes. */
final class TileMaterialArtwork {
    private TileMaterialArtwork() {}

    static Map<String, BufferedImage> textures() {
        var textures = new LinkedHashMap<String, BufferedImage>();
        // Close lengthwise grain and a small polished knot.
        textures.put("wood", pixels(new int[]{183, 207, 227, 241, 250},
            "2232212332221232", "2332212332221232", "2332123322221232", "2332123222212232",
            "2322123222112332", "2322123221032332", "2322123221032332", "2332123222112332",
            "2332123322212332", "2232122332212232", "2232212332212232", "2233212332221232",
            "2233212332221232", "2233212332221232", "2232212332221232", "2232212332221232"));
        // Smooth bone with sparse elongated pores, rather than stone cracks.
        textures.put("bone", pixels(new int[]{214, 233, 244, 250, 255},
            "2223333222223332", "2223333222223332", "2223333221223332", "2223333220123332",
            "2223333221223332", "2223333222223332", "2223332222223332", "2233332222223332",
            "2233332222233332", "2233332222233332", "2233332222233332", "2233332222233332",
            "2213332222233332", "2201332222233332", "2213333222233332", "2223333222223332"));
        // Stepped crystal planes and a narrow bright seam.
        textures.put("quartz", pixels(new int[]{211, 227, 241, 249, 255},
            "2222234333322222", "2222343333322222", "2223433333222222", "2234333332222222",
            "2343333322222222", "3433333222222223", "4333332222222234", "3333322222222343",
            "3333222222223433", "3332222222234333", "3322222222343333", "3222222223433333",
            "2222222234333333", "2222222343333332", "2222223433333322", "2222234333333222"));
        // Chalky islands and short mineral veins.
        textures.put("calcite", pixels(new int[]{197, 219, 237, 247, 255},
            "2233332211222332", "2334433221222332", "2334433222112222", "2233332222011222",
            "1223322222211332", "1122222332222331", "2112223343222332", "2211223344322222",
            "2220122333322222", "3322112233222333", "3432221122223344", "3332222112223343",
            "3322222201222333", "2222332211222222", "2223443221222222", "2223332211222332"));
        // Quiet clear glass, crossed by two reflected light streaks.
        textures.put("glass", pixels(new int[]{226, 237, 240, 252, 255},
            "2222222222222222", "2222222222233222", "2222222222342222", "2222222223422222",
            "2222222234222222", "2222222342222222", "2222223422232222", "2222234222322222",
            "2222342223222222", "2223422232222222", "2234222322222222", "2232223222222222",
            "2222232222222222", "2222222222222222", "2222222222222222", "2222222222222222"));
        // Interlocking facets, bright crystal tips and dark clefts.
        textures.put("amethyst", pixels(new int[]{171, 196, 220, 239, 255},
            "1122332211233321", "1223443212334432", "2234443223444432", "2344433223443321",
            "2344332112333221", "1233321101222210", "1122210122111101", "2211101234322122",
            "3322012344432233", "4432123444332344", "4432123443322344", "3321012333221233",
            "2210122232210122", "1101234321101011", "1012344321012210", "1122333210123321"));
        return textures;
    }

    private static BufferedImage pixels(int[] palette, String... rows) {
        if (rows.length != 16) throw new IllegalArgumentException("Material height");
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            if (rows[y].length() != 16) throw new IllegalArgumentException("Material width");
            for (int x = 0; x < 16; x++) {
                int value = palette[rows[y].charAt(x) - '0'];
                image.setRGB(x, y, 0xff000000 | value << 16 | value << 8 | value);
            }
        }
        return image;
    }
}
