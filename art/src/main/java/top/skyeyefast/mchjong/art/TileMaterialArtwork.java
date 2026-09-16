package top.skyeyefast.mchjong.art;

import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Neutral surface relief, multiplied by the physical tile material's color at render time. */
final class TileMaterialArtwork {
    private TileMaterialArtwork() {}

    static Map<String, BufferedImage> textures() {
        var textures = new LinkedHashMap<String, BufferedImage>();
        for (String material : new String[]{"wood", "bone", "quartz", "calcite", "glass", "amethyst"}) {
            var image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                double wave = Math.sin((x + 2 * Math.sin(y * Math.PI / 32)) * Math.PI / 8);
                int grain = Math.floorMod(x * 13 + y * 7, 11);
                int value = switch (material) {
                    case "wood" -> 223 + (int) (20 * wave) - grain;
                    case "bone" -> 245 - (grain == 0 ? 20 : grain / 2) + (int) (5 * wave);
                    case "quartz" -> 238 + (int) (9 * Math.sin((x + y) * Math.PI / 16)) - grain / 3;
                    case "calcite" -> 232 + (int) (17 * Math.sin((x + 7 * Math.sin(y * Math.PI / 16)) * Math.PI / 16)) - grain;
                    case "glass" -> 245 + (int) (9 * Math.sin(x * Math.PI / 32));
                    case "amethyst" -> 210 + Math.floorMod(x / 8 + y / 12, 4) * 12 - grain / 2;
                    default -> throw new IllegalStateException(material);
                };
                int c = Math.clamp(value, 0, 255);
                image.setRGB(x, y, 0xff000000 | c << 16 | c << 8 | c);
            }
            textures.put(material, image);
        }
        return textures;
    }
}
