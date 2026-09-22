package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Packs the supplied transparent engravings into the shared runtime atlas
 * layout.
 */
final class TileArtwork {
    static final List<String> PRESETS = List.of("kansai", "kanto");
    static final int WIDTH = 256, HEIGHT = 384, ATLAS_WIDTH = 2048, ATLAS_HEIGHT = 4096;
    static final int FACE_COUNT = 45, BACK = 0;
    static final List<String> FACE_KEYS = List.of(
            "1m", "2m", "3m", "4m", "5m", "6m", "7m", "8m", "9m",
            "1p", "2p", "3p", "4p", "5p", "6p", "7p", "8p", "9p",
            "1s", "2s", "3s", "4s", "5s", "6s", "7s", "8s", "9s",
            "1z", "2z", "3z", "4z", "5z", "6z", "7z",
            "0m", "0p", "0s",
            "1q", "2q", "3q", "4q", "5q", "6q", "7q", "8q");

    private final BufferedImage[] engravings = new BufferedImage[FACE_COUNT];
    private final String notice;

    TileArtwork(Path presets, String preset) throws IOException {
        if (!PRESETS.contains(preset))
            throw new IllegalArgumentException("Unknown face preset: " + preset);
        Path directory = presets.resolve(preset);
        Path tilesDir = directory.resolve("tiles");
        for (int face = 0; face < FACE_COUNT; face++) {
            String key = FACE_KEYS.get(face);
            Path tilePath = tilesDir.resolve(key + ".png");
            if (Files.exists(tilePath)) {
                var img = ImageIO.read(tilePath.toFile());
                if (img != null)
                    engravings[face] = img;
            }
        }
        notice = Files.readString(directory.resolve("theme_metadata.json"));
    }

    static String tileKey(int face) {
        if (face < 0 || face >= FACE_COUNT)
            throw new IllegalArgumentException("Tile face: " + face);
        return FACE_KEYS.get(face);
    }

    BufferedImage face(int face) {
        return render(face, false);
    }

    BufferedImage glyph(int face) {
        return render(face, true);
    }

    private BufferedImage render(int face, boolean transparent) {
        if (face < 0 || face >= FACE_COUNT)
            throw new IllegalArgumentException("Tile face: " + face);
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            if (!transparent) {
                g.setColor(new Color(0xcbd0d4));
                g.fillRect(0, 0, WIDTH, HEIGHT);
                g.setColor(Color.WHITE);
                g.fillRect(4, 4, WIDTH - 8, HEIGHT - 8);
            }
            if (face != 31 && engravings[face] != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                // Preserve the source proportions on the 2:3 tile with a clear margin.
                g.drawImage(engravings[face], 8, 32, 240, 320, null);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    static BufferedImage back() {
        return new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
    }

    String notice() {
        return notice;
    }
}
