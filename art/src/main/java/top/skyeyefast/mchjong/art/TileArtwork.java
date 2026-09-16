package top.skyeyefast.mchjong.art;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipFile;

/** Rasterizes FluffyStuff's CC0 glyphs; JSVG and the source archive never ship in the mod. */
final class TileArtwork implements AutoCloseable {
    static final int WIDTH = 256;
    static final int HEIGHT = 384;
    static final int ATLAS_SIZE = 2048;
    static final int FACE_COUNT = 37;
    static final int IVORY = 0xfff4eedb;
    static final int BACK = 0xffffffff;
    private static final String SOURCE_ROOT = "riichi-mahjong-tiles-26e127ba2117f45cdce5ea0225748cc0cfad3169/";
    private static final String SHA256 = "79f892bfde6e9450b359cabe939db69a4217ff539518018967a30947c295e276";
    private static final String[] SUITS = {"Man", "Pin", "Sou"};
    private static final String[] HONORS = {"Ton", "Nan", "Shaa", "Pei", "Haku", "Hatsu", "Chun"};
    private final Path archive;
    private final ZipFile source;
    private final SVGLoader loader = new SVGLoader();

    TileArtwork(Path archive) throws IOException {
        try {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)));
            if (!SHA256.equals(actual)) throw new IOException("Tile artwork SHA-256 mismatch: " + actual);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java must provide SHA-256", impossible);
        }
        this.archive = archive.toAbsolutePath();
        source = new ZipFile(archive.toFile());
    }

    static String sourceName(int face) {
        if (face < 0 || face >= FACE_COUNT) throw new IllegalArgumentException("Tile face: " + face);
        if (face >= 34) return SUITS[face - 34] + "5-Dora";
        return face < 27 ? SUITS[face / 9] + (face % 9 + 1) : HONORS[face - 27];
    }

    BufferedImage face(int face) throws IOException {
        return renderFace(face, false);
    }

    BufferedImage glyph(int face) throws IOException {
        return renderFace(face, true);
    }

    private BufferedImage renderFace(int face, boolean transparent) throws IOException {
        String name = sourceName(face);
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            int border = WIDTH / 64;
            if (!transparent) {
                g.setColor(new Color(0xc9c0a6)); g.fillRect(0, 0, WIDTH, HEIGHT);
                g.setColor(new Color(IVORY, true)); g.fillRect(border, border, WIDTH - 2 * border, HEIGHT - 2 * border);
                g.setColor(new Color(0xfffbef));
                g.drawLine(2 * border, border, WIDTH - 3 * border, border);
                g.drawLine(border, 2 * border, border, HEIGHT - 3 * border);
            }
            // White dragons remain genuinely blank; no imported tile frame or lettering.
            if (face != 31) {
                String entry = SOURCE_ROOT + "Regular/" + name + ".svg";
                if (source.getEntry(entry) == null) throw new IOException("Missing artwork: " + entry);
                SVGDocument document = loader.load(URI.create("jar:" + archive.toUri() + "!/" + entry).toURL());
                if (document == null) throw new IOException("Could not parse artwork: " + entry);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                document.render(null, g, new ViewBox(WIDTH / 16f, HEIGHT / 12f, WIDTH * 7f / 8f, HEIGHT * 5f / 6f));
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    static BufferedImage back() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
            image.setRGB(x, y, BACK);
        }
        return image;
    }

    String license() throws IOException {
        var entry = source.getEntry(SOURCE_ROOT + "LICENSE.md");
        if (entry == null) throw new IOException("Tile artwork license missing");
        try (var stream = source.getInputStream(entry)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override public void close() throws IOException { source.close(); }
}
