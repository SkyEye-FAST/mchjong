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
        texture("block/felt", 32, 32, 0x235d53, 0x2a695d);
        texture("block/wood", 32, 32, 0x493b32, 0x59483b);
        texture("block/brass", 16, 16, 0xa88850, 0xc5a567);
        texture("block/cushion", 32, 32, 0x294b49, 0x355e57);
        tiles(artwork);
        models();
    }

    private void texture(String name, int width, int height, int first, int second) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int hash = Integer.rotateLeft(x * 73471 ^ y * 19349663, 7);
            image.setRGB(x, y, 0xff000000 | ((hash & 7) == 0 ? second : first));
        }
        png(name, image);
    }

    private void tiles(Path archive) throws IOException {
        BufferedImage atlas = new BufferedImage(TileArtwork.ATLAS_SIZE, TileArtwork.ATLAS_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        try (TileArtwork artwork = new TileArtwork(archive)) {
            for (int face = 0; face < TileArtwork.FACE_COUNT; face++) {
                BufferedImage image = artwork.face(face);
                png("tile/" + face, image);
                g.drawImage(image, face % 8 * TileArtwork.WIDTH, face / 8 * TileArtwork.HEIGHT, null);
            }
            // Dedicated neutral swatch: body colors must not depend on a player's white-dragon art.
            g.setColor(Color.WHITE);
            g.fillRect(TileArtwork.ATLAS_SIZE - 32, TileArtwork.ATLAS_SIZE - 32, 32, 32);
            text("META-INF/licenses/riichi-mahjong-tiles-LICENSE.txt", artwork.license());
        } finally {
            g.dispose();
        }
        png("tiles", atlas);
        png("tile/back", TileArtwork.back(false));
        String filtering = "{\"texture\":{\"blur\":true,\"clamp\":true}}";
        text("assets/mchjong/textures/tiles.png.mcmeta", filtering);
        text("assets/mchjong/textures/tile/back.png.mcmeta", filtering);

        String pack = "resourcepacks/patterned_backs/";
        BufferedImage pattern = TileArtwork.back(true);
        writePng(pack + "assets/mchjong/textures/tile/back.png", pattern);
        text(pack + "pack.mcmeta", "{\"pack\":{\"pack_format\":34,\"description\":{\"translate\":\"resourcePack.mchjong.patterned_backs.description\"}}}");
        BufferedImage icon = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        g = icon.createGraphics();
        g.setColor(new Color(0x182a2d)); g.fillRect(0, 0, 128, 128);
        g.setColor(new Color(TileArtwork.IVORY, true)); g.fillRect(29, 13, 70, 102);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(pattern, 32, 16, 64, 96, null);
        g.dispose();
        writePng(pack + "pack.png", icon);

        BufferedImage panel = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        g = panel.createGraphics();
        g.setColor(new Color(0x182a2d)); g.fillRect(0, 0, 32, 32);
        g.setColor(new Color(0xbca16c)); g.drawRect(1, 1, 29, 29);
        g.setColor(new Color(0x78b4a0)); g.drawRect(3, 3, 25, 25);
        g.dispose();
        png("gui/callout", panel);
    }

    private static String box(double x1, double y1, double z1, double x2, double y2, double z2, String texture) {
        String face = "{\"texture\":\"#" + texture + "\",\"uv\":[0,0,16,16]}";
        return "{\"from\":[" + x1 + "," + y1 + "," + z1 + "],\"to\":[" + x2 + "," + y2 + "," + z2
            + "],\"faces\":{\"up\":" + face + ",\"down\":" + face + ",\"north\":" + face
            + ",\"south\":" + face + ",\"west\":" + face + ",\"east\":" + face + "}}";
    }

    private void models() throws IOException {
        String textures = "\"textures\":{\"particle\":\"mchjong:block/wood\",\"wood\":\"mchjong:block/wood\","
            + "\"felt\":\"mchjong:block/felt\",\"brass\":\"mchjong:block/brass\",\"cushion\":\"mchjong:block/cushion\"}";
        String table = String.join(",",
            box(-15, 12, -15, 31, 14, 31, "wood"), box(-13, 14, -13, 29, 15, 29, "felt"),
            box(-15, 14, -15, -13, 16, 31, "wood"), box(29, 14, -15, 31, 16, 31, "wood"),
            box(-13, 14, -15, 29, 16, -13, "wood"), box(-13, 14, 29, 29, 16, 31, "wood"),
            box(-14, 15.5, -14, -13.5, 16.1, 30, "brass"), box(29.5, 15.5, -14, 30, 16.1, 30, "brass"),
            box(-14, 15.5, -14, 30, 16.1, -13.5, "brass"), box(-14, 15.5, 29.5, 30, 16.1, 30, "brass"),
            box(2, 2, 2, 14, 12, 14, "wood"), box(-6, 0, 4, 22, 3, 12, "wood"),
            box(4, 0, -6, 12, 3, 22, "wood"), box(3, 3, 3, 13, 4, 13, "brass"));
        String display = "\"display\":{\"gui\":{\"rotation\":[30,225,0],\"scale\":[0.35,0.35,0.35]},"
            + "\"ground\":{\"translation\":[0,3,0],\"scale\":[0.2,0.2,0.2]},"
            + "\"firstperson_righthand\":{\"rotation\":[0,45,0],\"translation\":[0,2,0],\"scale\":[0.25,0.25,0.25]},"
            + "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"translation\":[0,2.5,0],\"scale\":[0.2,0.2,0.2]}}";
        text("assets/mchjong/models/block/mahjong_table.json", "{" + textures + ",\"elements\":[" + table + "]," + display + "}");
        String stool = String.join(",", box(2, 6, 2, 14, 8, 14, "wood"), box(2, 8, 2, 14, 10, 14, "cushion"),
            box(3, 0, 3, 5, 6, 5, "wood"), box(11, 0, 3, 13, 6, 5, "wood"),
            box(3, 0, 11, 5, 6, 13, "wood"), box(11, 0, 11, 13, 6, 13, "wood"),
            box(3, 2, 4, 13, 3, 5, "brass"), box(3, 2, 11, 13, 3, 12, "brass"));
        text("assets/mchjong/models/block/mahjong_stool.json", "{\"parent\":\"minecraft:block/block\"," + textures + ",\"elements\":[" + stool + "]}");
        for (String name : new String[]{"mahjong_table", "mahjong_stool"})
            text("assets/mchjong/models/item/" + name + ".json", "{\"parent\":\"mchjong:block/" + name + "\"}");
        text("assets/mchjong/models/block/table_space.json", "{\"textures\":{\"particle\":\"mchjong:block/wood\"},\"elements\":[]}");
        for (String name : new String[]{"mahjong_table", "mahjong_stool", "table_space"})
            text("assets/mchjong/blockstates/" + name + ".json", "{\"variants\":{\"\":{\"model\":\"mchjong:block/" + name + "\"}}}");
        for (String name : new String[]{"mahjong_table", "mahjong_stool"})
            text("data/mchjong/loot_table/blocks/" + name + ".json", "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mchjong:" + name + "\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}");
        text("data/mchjong/recipe/mahjong_table.json", "{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\",\"pattern\":[\"PPP\",\"GIG\",\"P P\"],\"key\":{\"P\":{\"tag\":\"minecraft:planks\"},\"G\":{\"item\":\"minecraft:green_carpet\"},\"I\":{\"item\":\"minecraft:iron_ingot\"}},\"result\":{\"id\":\"mchjong:mahjong_table\",\"count\":1}}");
        text("data/mchjong/recipe/mahjong_stool.json", "{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\",\"pattern\":[\"GG\",\"PP\"],\"key\":{\"P\":{\"tag\":\"minecraft:planks\"},\"G\":{\"item\":\"minecraft:green_wool\"}},\"result\":{\"id\":\"mchjong:mahjong_stool\",\"count\":1}}");
        text("data/minecraft/tags/block/mineable/axe.json", "{\"replace\":false,\"values\":[\"mchjong:mahjong_table\",\"mchjong:mahjong_stool\",\"mchjong:table_space\"]}");
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
