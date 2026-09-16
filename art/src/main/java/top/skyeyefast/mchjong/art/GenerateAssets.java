package top.skyeyefast.mchjong.art;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Reproducible build-time textures and models; no game-time downloads or platform fonts. */
public final class GenerateAssets {
    private final Path root;

    private GenerateAssets(Path root) { this.root = root; }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IllegalArgumentException("Expected resource directory and source artwork archive");
        new GenerateAssets(Path.of(args[0])).generate(Path.of(args[1]));
    }

    private void generate(Path artwork) throws IOException {
        tiles(artwork);
        models();
    }

    private void tiles(Path archive) throws IOException {
        BufferedImage atlas = new BufferedImage(TileArtwork.ATLAS_SIZE, TileArtwork.ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glyphs = new BufferedImage(TileArtwork.ATLAS_SIZE, TileArtwork.ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        Graphics2D glyphGraphics = glyphs.createGraphics();
        try (TileArtwork artwork = new TileArtwork(archive)) {
            for (int face = 0; face < TileArtwork.FACE_COUNT; face++) {
                BufferedImage image = artwork.face(face);
                g.drawImage(image, face % 8 * TileArtwork.WIDTH, face / 8 * TileArtwork.HEIGHT, null);
                glyphGraphics.drawImage(artwork.glyph(face), face % 8 * TileArtwork.WIDTH, face / 8 * TileArtwork.HEIGHT, null);
            }
            // Dedicated neutral swatch: body colors must not depend on a player's white-dragon art.
            g.setColor(Color.WHITE);
            g.fillRect(TileArtwork.ATLAS_SIZE - 32, TileArtwork.ATLAS_SIZE - 32, 32, 32);
            glyphGraphics.setColor(Color.WHITE);
            glyphGraphics.fillRect(TileArtwork.ATLAS_SIZE - 32, TileArtwork.ATLAS_SIZE - 32, 32, 32);
            text("META-INF/licenses/riichi-mahjong-tiles-LICENSE.txt", artwork.license());
        } finally {
            g.dispose();
            glyphGraphics.dispose();
        }
        png("tiles", atlas);
        png("tile_glyphs", glyphs);
        png("tile/back", TileArtwork.back());
        String filtering = "{\"texture\":{\"blur\":true,\"clamp\":true}}";
        text("assets/mchjong/textures/tiles.png.mcmeta", filtering);
        text("assets/mchjong/textures/tile_glyphs.png.mcmeta", filtering);
        text("assets/mchjong/textures/tile/back.png.mcmeta", filtering);
    }

    private void models() throws IOException {
        // Component-aware geometry lives once in FurnitureMesh/TileMesh, shared by items and blocks.
        for (String name : new String[]{"mahjong_table", "automatic_mahjong_table", "mahjong_stool", "table_space"}) {
            text("assets/mchjong/models/block/" + name + ".json",
                "{\"textures\":{\"particle\":\"minecraft:block/oak_planks\"},\"elements\":[]}");
            text("assets/mchjong/blockstates/" + name + ".json", "{\"variants\":{\"\":{\"model\":\"mchjong:block/" + name + "\"}}}");
        }
        for (String name : new String[]{"mahjong_table", "automatic_mahjong_table", "mahjong_stool", "mahjong_tile", "mahjong_box", "table_cloth", "point_stick"}) {
            String rotation = name.equals("mahjong_tile") ? "[0,0,0]" : "[30,225,0]";
            text("assets/mchjong/models/item/" + name + ".json", "{\"parent\":\"minecraft:builtin/entity\","
                + "\"textures\":{\"particle\":\"minecraft:block/oak_planks\"},\"gui_light\":\"side\",\"display\":{"
                + "\"gui\":{\"rotation\":" + rotation + "},"
                + "\"ground\":{\"translation\":[0,2,0],\"scale\":[0.5,0.5,0.5]},"
                + "\"firstperson_righthand\":{\"rotation\":[0,30,0],\"scale\":[0.7,0.7,0.7]},"
                + "\"firstperson_lefthand\":{\"rotation\":[0,-30,0],\"scale\":[0.7,0.7,0.7]},"
                + "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"scale\":[0.6,0.6,0.6]},"
                + "\"thirdperson_lefthand\":{\"rotation\":[75,-45,0],\"scale\":[0.6,0.6,0.6]}}}");
        }
    }

    private void png(String name, BufferedImage image) throws IOException {
        writePng("assets/mchjong/textures/" + name + ".png", image);
    }

    private void writePng(String relative, BufferedImage image) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("PNG encoder unavailable");
    }

    private void text(String relative, String text) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text + "\n", StandardCharsets.UTF_8);
    }
}
