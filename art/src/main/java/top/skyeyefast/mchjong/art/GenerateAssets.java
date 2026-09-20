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
        if (args.length != 2) throw new IllegalArgumentException("Expected resource directory and native preset directory");
        new GenerateAssets(Path.of(args[0])).generate(Path.of(args[1]));
    }

    private void generate(Path artwork) throws IOException {
        for (String preset : TileArtwork.PRESETS) tiles(artwork, preset);
        text("META-INF/licenses/tile-face-presets-NOTICE.md", Files.readString(artwork.resolve("NOTICE.md")));
        png("tile/back", TileArtwork.back());
        text("assets/mchjong/textures/tile/back.png.mcmeta", "{\"texture\":{\"blur\":true,\"clamp\":true}}");
        for (var texture : FurnitureArtwork.textures().entrySet()) {
            png("furniture/" + texture.getKey(), texture.getValue());
            text("assets/mchjong/textures/furniture/" + texture.getKey() + ".png.mcmeta",
                "{\"texture\":{\"blur\":false,\"clamp\":false}}");
        }
        for (var texture : TileMaterialArtwork.textures().entrySet())
            png("tile_material/" + texture.getKey(), texture.getValue());
        png("point_sticks", PointStickArtwork.texture());
        text("assets/mchjong/textures/point_sticks.png.mcmeta", "{\"texture\":{\"blur\":true,\"clamp\":true}}");
        models();
        dice();
        for (String name : java.util.List.of("mahjong_dye", "creative_mahjong_dye", "red_dora_dye", "undo_dye")) {
            png("item/" + name, MahjongDyeArtwork.texture(name));
            text("assets/mchjong/models/item/" + name + ".json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mchjong:item/" + name + "\"}}");
        }
    }

    private void dice() throws IOException {
        for (int face = 1; face <= 6; face++) {
            var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            var g = image.createGraphics();
            try {
                g.setColor(new Color(0xbcb6a6)); g.fillRect(0, 0, 16, 16);
                g.setColor(new Color(0xf8f5e9)); g.fillRect(1, 1, 14, 14);
                g.setColor(new Color(face == 1 || face == 4 ? 0xb52d32 : 0x293238));
                if (face % 2 == 1) g.fillOval(6, 6, 4, 4);
                if (face >= 2) { g.fillOval(3, 3, 3, 3); g.fillOval(10, 10, 3, 3); }
                if (face >= 4) { g.fillOval(10, 3, 3, 3); g.fillOval(3, 10, 3, 3); }
                if (face == 6) { g.fillOval(3, 6, 3, 3); g.fillOval(10, 6, 3, 3); }
            } finally { g.dispose(); }
            png("item/dice_" + face, image);
        }
        text("assets/mchjong/models/item/dice.json", """
            {"parent":"minecraft:block/block","textures":{
              "particle":"mchjong:item/dice_1","1":"mchjong:item/dice_1","2":"mchjong:item/dice_2",
              "3":"mchjong:item/dice_3","4":"mchjong:item/dice_4","5":"mchjong:item/dice_5","6":"mchjong:item/dice_6"},
             "elements":[{"from":[3,3,3],"to":[13,13,13],"faces":{
              "up":{"texture":"#1","uv":[0,0,16,16]},"down":{"texture":"#6","uv":[0,0,16,16]},
              "north":{"texture":"#2","uv":[0,0,16,16]},"south":{"texture":"#5","uv":[0,0,16,16]},
              "east":{"texture":"#3","uv":[0,0,16,16]},"west":{"texture":"#4","uv":[0,0,16,16]}}}]}
            """);
    }

    private void tiles(Path presets, String preset) throws IOException {
        BufferedImage atlas = new BufferedImage(TileArtwork.ATLAS_WIDTH, TileArtwork.ATLAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glyphs = new BufferedImage(TileArtwork.ATLAS_WIDTH, TileArtwork.ATLAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        Graphics2D glyphGraphics = glyphs.createGraphics();
        try {
            TileArtwork artwork = new TileArtwork(presets, preset);
            for (int face = 0; face < TileArtwork.FACE_COUNT; face++) {
                BufferedImage image = artwork.face(face);
                g.drawImage(image, face % 8 * TileArtwork.WIDTH, face / 8 * TileArtwork.HEIGHT, null);
                glyphGraphics.drawImage(artwork.glyph(face), face % 8 * TileArtwork.WIDTH, face / 8 * TileArtwork.HEIGHT, null);
            }
            // Dedicated neutral swatch: body colors must not depend on a player's white-dragon art.
            g.setColor(Color.WHITE);
            g.fillRect(TileArtwork.ATLAS_WIDTH - 32, TileArtwork.ATLAS_HEIGHT - 32, 32, 32);
            glyphGraphics.setColor(Color.WHITE);
            glyphGraphics.fillRect(TileArtwork.ATLAS_WIDTH - 32, TileArtwork.ATLAS_HEIGHT - 32, 32, 32);
            text("META-INF/licenses/" + preset + "-source.json", artwork.notice());
        } finally {
            g.dispose();
            glyphGraphics.dispose();
        }
        String path = preset.equals("kanto") ? "kanto/" : "";
        png(path + "tiles", atlas);
        png(path + "tile_glyphs", glyphs);
        String filtering = "{\"texture\":{\"blur\":true,\"clamp\":true}}";
        text("assets/mchjong/textures/" + path + "tiles.png.mcmeta", filtering);
        text("assets/mchjong/textures/" + path + "tile_glyphs.png.mcmeta", filtering);
    }

    private void models() throws IOException {
        // Furniture is outside the automatic block/item texture directories. Stitch its particle explicitly.
        text("assets/minecraft/atlases/blocks.json",
            "{\"sources\":[{\"type\":\"minecraft:single\",\"resource\":\"mchjong:furniture/wood_oak\"}]}");
        // Component-aware geometry lives once in FurnitureMesh/TileMesh, shared by items and blocks.
        for (String name : new String[]{"mahjong_table", "automatic_mahjong_table", "mahjong_stool", "table_space"}) {
            text("assets/mchjong/models/block/" + name + ".json",
                "{\"textures\":{\"particle\":\"mchjong:furniture/wood_oak\"},\"elements\":[]}");
            text("assets/mchjong/blockstates/" + name + ".json", "{\"variants\":{\"\":{\"model\":\"mchjong:block/" + name + "\"}}}");
        }
        for (String name : new String[]{"mahjong_table", "automatic_mahjong_table", "mahjong_stool", "mahjong_tile", "mahjong_box", "table_cloth", "point_stick"}) {
            String rotation = name.equals("mahjong_tile") ? "[0,0,0]"
                : name.endsWith("mahjong_table") ? "[15,225,0]" : "[30,225,0]";
            String lighting = name.equals("mahjong_tile") ? "front" : "side";
            // Vanilla mirrors X translation and Y/Z rotation for the left hand.
            // Like a held sword, the free end rises outward while the printed surface faces inward.
            String held = switch (name) {
                case "mahjong_tile" -> "{\"rotation\":[-12,-65,-25],\"translation\":[1,6,-3],\"scale\":[0.4,0.4,0.4]}";
                case "point_stick" -> "{\"rotation\":[65,60,25],\"translation\":[1,6,-2],\"scale\":[0.7,0.7,0.7]}";
                default -> "{\"rotation\":[0,30,0],\"scale\":[0.7,0.7,0.7]}";
            };
            text("assets/mchjong/models/item/" + name + ".json", "{\"parent\":\"minecraft:builtin/entity\","
                + "\"textures\":{\"particle\":\"mchjong:furniture/wood_oak\"},\"gui_light\":\"" + lighting + "\",\"display\":{"
                + "\"gui\":{\"rotation\":" + rotation + "},"
                + "\"ground\":{\"translation\":[0,2,0],\"scale\":[0.5,0.5,0.5]},"
                + "\"firstperson_righthand\":" + held + ","
                + "\"firstperson_lefthand\":" + held + ","
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
