package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipFile;

/** Pinned Hong Kong engravings, separated from their frame and painted with three tile inks. */
final class FlowerTileArtwork {
    static final String REVISION = "73c507836e6ab30579305257ae3e2a7e1142d066";
    private static final String ROOT = "I.Mahjong-" + REVISION + "/";
    private static final String SHA256 = "3bf09b9786adc8cb689fb5debfa9777e96de2c63856117db5d56855ecd501007";
    static final int RED = 0xa72a2e, GREEN = 0x134e2d, BLUE = 0x203e83;
    // 1q-8q: spring, summer, autumn, winter, plum, orchid, bamboo, chrysanthemum.
    private static final int[] CODE_POINTS = {0x1f026, 0x1f027, 0x1f028, 0x1f029, 0x1f022, 0x1f023, 0x1f024, 0x1f025};
    // Color regions are keyed by the source engravings, independently of the public tile order.
    private static final int[] INK_DESIGNS = {4, 5, 6, 7, 0, 1, 3, 2};
    private final Layers[] flowers = new Layers[8];
    private final String license;

    FlowerTileArtwork(Path archive) throws IOException {
        try {
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)));
            if (!SHA256.equals(actual)) throw new IOException("Flower artwork SHA-256 mismatch: " + actual);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java must provide SHA-256", impossible);
        }
        try (var source = new ZipFile(archive.toFile())) {
            var fontEntry = source.getEntry(ROOT + "I.MahjongHK.otf");
            var licenseEntry = source.getEntry(ROOT + "License.md");
            if (fontEntry == null || licenseEntry == null) throw new IOException("Flower source or license missing");
            Font font;
            try (var stream = source.getInputStream(fontEntry)) {
                font = Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(1000f);
            }
            try (var stream = source.getInputStream(licenseEntry)) {
                license = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            var context = new FontRenderContext(null, true, true);
            for (int flower = 0; flower < flowers.length; flower++) {
                int code = codePoint(flower);
                if (!font.canDisplay(code)) throw new IOException("Missing flower glyph: " + Integer.toHexString(code));
                char[] text = Character.toChars(code);
                Shape outline = font.layoutGlyphVector(context, text, 0, text.length, Font.LAYOUT_LEFT_TO_RIGHT).getOutline();
                flowers[flower] = paint(withoutFrame(outline), INK_DESIGNS[flower]);
            }
        } catch (FontFormatException invalid) {
            throw new IOException("Invalid pinned flower outlines", invalid);
        }
    }

    static int codePoint(int flower) {
        if (flower < 0 || flower >= CODE_POINTS.length) throw new IllegalArgumentException("Unknown flower: " + flower);
        return CODE_POINTS[flower];
    }

    void draw(Graphics2D graphics, int flower, int width, int height) {
        codePoint(flower);
        var g = (Graphics2D) graphics.create();
        try {
            g.scale(width / 256.0, height / 384.0);
            // The source tile has a 3:4 outline; keep its proportions on our 2:3 white plate.
            g.translate(0, 64.0 / 3);
            g.scale(1, 8.0 / 9);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            var layers = flowers[flower];
            g.setColor(new Color(GREEN)); g.fill(layers.green());
            g.setColor(new Color(RED)); g.fill(layers.red());
            g.setColor(new Color(BLUE)); g.fill(layers.blue());
        } finally { g.dispose(); }
    }

    private static Shape withoutFrame(Shape glyph) throws IOException {
        var bounds = glyph.getBounds2D();
        var transform = AffineTransform.getScaleInstance(256 / bounds.getWidth(), 384 / bounds.getHeight());
        transform.translate(-bounds.getX(), -bounds.getY());
        var iterator = glyph.getPathIterator(transform);
        var ink = new Path2D.Double(iterator.getWindingRule());
        var contour = new Path2D.Double(iterator.getWindingRule());
        double[] p = new double[6];
        int frames = 0;
        while (!iterator.isDone()) {
            switch (iterator.currentSegment(p)) {
                case PathIterator.SEG_MOVETO -> { contour = new Path2D.Double(iterator.getWindingRule()); contour.moveTo(p[0], p[1]); }
                case PathIterator.SEG_LINETO -> contour.lineTo(p[0], p[1]);
                case PathIterator.SEG_QUADTO -> contour.quadTo(p[0], p[1], p[2], p[3]);
                case PathIterator.SEG_CUBICTO -> contour.curveTo(p[0], p[1], p[2], p[3], p[4], p[5]);
                case PathIterator.SEG_CLOSE -> {
                    contour.closePath();
                    var box = contour.getBounds2D();
                    if (box.getWidth() > 220 && box.getHeight() > 330) frames++;
                    else ink.append(contour, false);
                }
                default -> throw new IOException("Unknown flower outline segment");
            }
            iterator.next();
        }
        if (frames != 2) throw new IOException("Expected the two contours of the source tile frame, found " + frames);
        return ink;
    }

    private static Layers paint(Shape outline, int flower) {
        var ink = new Area(outline);
        var blue = new Area();
        var red = new Area();
        var contour = new Path2D.Double();
        double[] p = new double[6];
        // Select complete engraved contours rather than cutting petals, leaves or character strokes
        // with painted ellipses. Source coordinates are registered to the checksummed HK artwork.
        for (var iterator = outline.getPathIterator(null); !iterator.isDone(); iterator.next()) {
            switch (iterator.currentSegment(p)) {
                case PathIterator.SEG_MOVETO -> { contour = new Path2D.Double(); contour.moveTo(p[0], p[1]); }
                case PathIterator.SEG_LINETO -> contour.lineTo(p[0], p[1]);
                case PathIterator.SEG_QUADTO -> contour.quadTo(p[0], p[1], p[2], p[3]);
                case PathIterator.SEG_CUBICTO -> contour.curveTo(p[0], p[1], p[2], p[3], p[4], p[5]);
                case PathIterator.SEG_CLOSE -> {
                    contour.closePath();
                    var part = new Area(contour);
                    part.intersect(ink);
                    if (part.isEmpty()) continue;
                    var b = part.getBounds2D();
                    boolean title = flower < 4 ? b.getMaxX() < 125 && b.getMaxY() < 150
                        : b.getMinX() > 130 && b.getMaxY() < 145;
                    boolean number = flower < 4 ? b.getMinX() > 190 && b.getMaxY() < 96
                        : b.getMaxX() < 84 && b.getMaxY() < 80;
                    if (title) (flower < 4 ? blue : red).add(part);
                    else if (number) (flower < 4 ? red : blue).add(part);
                    else if (blossom(part, b, flower)) red.add(part);
                }
                default -> throw new IllegalArgumentException("Unknown flower outline segment");
            }
        }
        red.subtract(blue);
        var green = new Area(ink);
        green.subtract(red);
        green.subtract(blue);
        return new Layers(green, red, blue);
    }

    private static boolean blossom(Area part, Rectangle2D b, int flower) {
        if (flower == 7) {
            // Narcissus heads share contours with their stems. End the red ink at each flower's neck;
            // clipping this contour alone leaves the neighboring blades completely green.
            double neck = b.getMinY() > 95 && b.getMinY() < 110 && b.getWidth() > 80 && b.getMinX() < 40 ? 195
                : b.getMinY() > 190 && b.getMinY() < 205 && b.getMinX() < 30 ? 275
                : b.getMinY() > 165 && b.getMinY() < 180 && b.getMinX() > 150 ? 260 : 0;
            if (neck == 0) return false;
            part.intersect(new Area(new Rectangle2D.Double(0, 0, 256, neck)));
            return true;
        }
        return switch (flower) {
            case 0 -> within(b, 65, 190, 180, 320);
            case 1 -> b.getMinY() > 220 && b.getMaxY() < 300 && b.getMaxX() < 215;
            case 2 -> within(b, 75, 190, 190, 324);
            case 3 -> false;
            case 4 -> within(b, 50, 130, 200, 260);
            case 5 -> b.getWidth() > 200 && b.getMinY() > 140 && b.getMaxY() < 320;
            case 6 -> within(b, 80, 140, 192, 280);
            default -> throw new IllegalArgumentException("Unknown flower");
        };
    }

    private static boolean within(Rectangle2D bounds, double x0, double y0, double x1, double y1) {
        return bounds.getMinX() >= x0 && bounds.getMinY() >= y0 && bounds.getMaxX() <= x1 && bounds.getMaxY() <= y1;
    }

    String license() { return license; }

    static String notice() {
        return """
            Flower tile outlines: I.Mahjong HK by Ichiro (SyaoranHinata), based on
            GL-MahjongTile by Gutenberg Labo. Distributed under the M+ FONT LICENSE.
            Source: https://github.com/SyaoranHinata/I.Mahjong
            Revision: %s
            Archive SHA-256: %s
            MCjhong adaptation: extracted eight Hong Kong flower/season outlines,
            removed the tile frame, preserved the engraving proportions and negative
            space, and applied red, green and blue ink to the labels and botanical motifs.
            Build-time outlines are rasterized into the shared tile atlases.
            """.formatted(REVISION, SHA256);
    }

    private record Layers(Area green, Area red, Area blue) {}
}
