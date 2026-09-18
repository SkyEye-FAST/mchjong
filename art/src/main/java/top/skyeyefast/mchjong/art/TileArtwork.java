package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;

/** Packs the supplied transparent engravings into the shared runtime atlas layout. */
final class TileArtwork {
    static final List<String> PRESETS = List.of("kansai", "kanto");
    static final int WIDTH = 256, HEIGHT = 384, ATLAS_WIDTH = 2048, ATLAS_HEIGHT = 4096;
    static final int FACE_COUNT = 45, BACK = 0xffffffff;
    private final BufferedImage source;
    private final boolean kanto;
    private final String notice;

    TileArtwork(Path presets, String preset) throws IOException {
        kanto = preset.equals("kanto");
        String folder = switch (preset) {
            case "kansai" -> "kanto_fluffystuff";
            case "kanto" -> "kansai_mizuno";
            default -> throw new IllegalArgumentException("Unknown face preset: " + preset);
        };
        Path directory = presets.resolve(folder);
        // Supplied folder names are reversed; Mizuno with 福禄寿貴 is Kanto.
        source = readAtlas(directory.resolve("atlas/" + (kanto ? "mizuno" : "default") + ".png"));
        notice = Files.readString(directory.resolve("theme_metadata.json"));
    }

    private static BufferedImage readAtlas(Path path) throws IOException {
        var atlas = ImageIO.read(path.toFile());
        if (atlas == null || atlas.getWidth() != 1500 || atlas.getHeight() != 1000)
            throw new IOException("Expected a 1500 x 1000 face atlas: " + path);
        return atlas;
    }

    static int sourceCell(int face, boolean kanto) {
        if (face < 0 || face >= FACE_COUNT) throw new IllegalArgumentException("Tile face: " + face);
        if (face < 27) return face / 9 * 10 + face % 9 + 1;
        if (face < 34) return face + 3;
        if (face < 37) return (face - 34) * 10;
        // The botanical source ends 梅蘭菊竹; public numbering ends 梅蘭竹菊.
        return !kanto && face >= 43 ? 87 - face : face;
    }

    BufferedImage face(int face) { return render(face, false); }
    BufferedImage glyph(int face) { return render(face, true); }

    private BufferedImage render(int face, boolean transparent) {
        if (face < 0 || face >= FACE_COUNT) throw new IllegalArgumentException("Tile face: " + face);
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            if (!transparent) {
                g.setColor(new Color(0xcbd0d4)); g.fillRect(0, 0, WIDTH, HEIGHT);
                g.setColor(Color.WHITE); g.fillRect(4, 4, WIDTH - 8, HEIGHT - 8);
            }
            if (face != 31) {
                int cell = sourceCell(face, kanto), x = cell % 10 * 150, y = cell / 10 * 200;
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                // Preserve the source 3:4 proportions on the 2:3 tile with a clear margin.
                g.drawImage(source.getSubimage(x, y, 150, 200), 16, 43, 224, 298, null);
            }
        } finally { g.dispose(); }
        return image;
    }

    static BufferedImage back() {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { g.setColor(Color.WHITE); g.fillRect(0, 0, WIDTH, HEIGHT); }
        finally { g.dispose(); }
        return image;
    }

    String notice() { return notice; }
}
