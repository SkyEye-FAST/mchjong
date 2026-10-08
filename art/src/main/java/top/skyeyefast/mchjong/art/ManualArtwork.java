package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** Original teal cloth cover with brass binding and an ivory tile emblem. */
final class ManualArtwork {
    private ManualArtwork() {}

    /** Handbook examples reuse the same prepared faces as the playing tiles. */
    static Map<String, BufferedImage> examples(Path presets) throws IOException {
        var result = new TreeMap<String, BufferedImage>();
        for (var preset : ManualExamples.HANDS.entrySet()) {
            var artwork = new TileArtwork(presets, preset.getKey());
            var faces = new BufferedImage[TileArtwork.FACE_COUNT];
            for (int face = 0; face < faces.length; face++) {
                faces[face] = new BufferedImage(23, 35, BufferedImage.TYPE_INT_ARGB);
                var g = faces[face].createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.drawImage(artwork.face(face), 0, 0, 23, 35, null);
                } finally { g.dispose(); }
            }
            for (var hand : preset.getValue().entrySet())
                result.put(hand.getKey(), example(faces, hand.getValue()));
        }
        return result;
    }

    /** Keep complete groups together, wrapping long special hands within Patchouli's 200-pixel crop. */
    private static BufferedImage example(BufferedImage[] faces, String notation) {
        var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(0x365851));
            int x = 4, y = 8;
            for (String group : notation.replace(";", " ; ").split("\s+")) {
                if (group.equals("/") || group.equals(";")) {
                    x = 4;
                    y += 46;
                }
                if (group.equals("+") || group.equals("/") || group.equals(";")) {
                    // Keep a winning-tile marker on the same row as its single tile.
                    if (x + (group.equals("+") ? 47 : 16) > 196) { x = 4; y += 46; }
                    if (y + 35 > 200) throw new IllegalArgumentException("Example exceeds page: " + notation);
                    if (group.equals("+")) {
                        g.fillRect(x + 1, y + 16, 14, 2);
                        g.fillRect(x + 7, y + 10, 2, 14);
                    } else if (group.equals("/")) {
                        for (int i = 0; i < 14; i++) g.fillRect(x + 13 - i, y + 10 + i, 2, 2);
                    } else {
                        g.fillRect(x + 7, y + 10, 3, 3);
                        g.fillRect(x + 7, y + 20, 3, 3);
                        g.fillRect(x + 6, y + 23, 2, 3);
                    }
                    x += 24;
                    continue;
                }
                if (!group.matches("[0-9]+[mpszq]"))
                    throw new IllegalArgumentException("Invalid example group: " + group);
                char suit = group.charAt(group.length() - 1);
                for (int start = 0; start < group.length() - 1; start += 8) {
                    int end = Math.min(start + 8, group.length() - 1);
                    int width = (end - start) * 23;
                    if (x + width > 196) { x = 4; y += 46; }
                    if (y + 35 > 200) throw new IllegalArgumentException("Example exceeds page: " + notation);
                    for (int i = start; i < end; i++) {
                        int face = TileArtwork.FACE_KEYS.indexOf("" + group.charAt(i) + suit);
                        if (face < 0) throw new IllegalArgumentException("Invalid example tile: " + group);
                        g.drawImage(faces[face], x + (i - start) * 23, y, null);
                    }
                    x += width + 8;
                }
            }
        } finally { g.dispose(); }
        return image;
    }

    static BufferedImage texture() {
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try {
            g.setColor(new Color(0x14272b));
            g.fillRect(2, 1, 12, 14);
            g.setColor(new Color(0xe4dbc5));
            g.fillRect(4, 12, 9, 2);
            g.setColor(new Color(0x365851));
            g.fillRect(3, 1, 11, 11);
            g.setColor(new Color(0x22383d));
            g.fillRect(3, 2, 2, 10);
            g.setColor(new Color(0xe3c082));
            g.fillRect(3, 2, 1, 2);
            g.fillRect(3, 9, 1, 2);
            g.fillRect(12, 2, 1, 1);
            g.fillRect(12, 10, 1, 1);
            g.setColor(new Color(0xc5bea8));
            g.fillRect(7, 4, 4, 6);
            g.setColor(new Color(0xfff1ee));
            g.fillRect(7, 3, 4, 6);
            g.setColor(new Color(0x9c3635));
            g.fillRect(8, 4, 2, 1);
            g.fillRect(8, 6, 2, 1);
            g.setColor(new Color(0xb99454));
            g.fillRect(11, 12, 1, 3);
        } finally {
            g.dispose();
        }
        return image;
    }
}
