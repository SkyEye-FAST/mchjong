package top.skyeyefast.mchjong.art;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** Reproducible build-time textures and models; no game-time downloads or platform fonts. */
public final class GenerateAssets {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path root;

    private GenerateAssets(Path root) { this.root = root; }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Expected resource directory");
        new GenerateAssets(Path.of(args[0])).generate();
    }

    private void generate() throws IOException {
        png("tile/back", resourceTexture("tile/back"));
        for (String name : List.of("creeper", "mojang")) {
            png("preset/back/" + name, resourceTexture("preset/back/" + name));
            text("assets/mchjong/textures/preset/back/" + name + ".png.mcmeta",
                "{\"texture\":{\"blur\":true,\"clamp\":true}}");
        }
        png("tile/plain", resourceTexture("tile/plain"));
        text("assets/mchjong/textures/tile/back.png.mcmeta", "{\"texture\":{\"blur\":true,\"clamp\":true}}");
        png("furniture/cloth_pattern", resourceTexture("furniture/cloth_pattern"));
        for (String name : List.of("brass", "edge", "felt", "steel",
                "wood_acacia", "wood_bamboo", "wood_birch", "wood_cherry",
                "wood_crimson", "wood_dark_oak", "wood_jungle", "wood_mangrove",
                "wood_oak", "wood_spruce", "wood_warped")) {
            png("furniture/" + name, resourceTexture("furniture/" + name));
            text("assets/mchjong/textures/furniture/" + name + ".png.mcmeta",
                "{\"texture\":{\"blur\":false,\"clamp\":false}}");
        }
        for (String name : List.of("amethyst", "bone", "calcite", "glass", "quartz", "wood"))
            png("tile_material/" + name, resourceTexture("tile_material/" + name));
        for (int points : PointStickArtwork.POINTS) {
            png("point_stick/" + points, PointStickArtwork.texture(points));
            text("assets/mchjong/textures/point_stick/" + points + ".png.mcmeta",
                "{\"texture\":{\"blur\":true,\"clamp\":true}}");
        }
        png("item/riichi_stick", PointStickArtwork.texture(1000));
        for (int points : new int[]{0, 1, 8, 10, 50, 100, -500})
            png("scoring_card/" + points, resourceTexture("scoring_card/" + points));
        png("gui/sprites/hud/riichi_stick", PointStickArtwork.texture(1000));
        png("gui/sprites/hud/honba_stick", PointStickArtwork.texture(100));
        text("assets/mchjong/models/item/riichi_stick.json", """
            {"textures":{"stick":"mchjong:item/riichi_stick","particle":"mchjong:item/riichi_stick"},
             "elements":[{"from":[2.4,0,7.52],"to":[13.6,0.4,8.48],"shade":false,"faces":{
              "up":{"texture":"#stick","uv":[0,0,16,16]},"down":{"texture":"#stick","uv":[0,0,16,16]},
              "north":{"texture":"#stick","uv":[0,8,0,8]},"south":{"texture":"#stick","uv":[0,8,0,8]},
              "east":{"texture":"#stick","uv":[0,8,0,8]},"west":{"texture":"#stick","uv":[0,8,0,8]}}}]}
            """);
        models();
        dice();
        png("item/mahjong_manual", resourceTexture("item/mahjong_manual"));
        text("assets/mchjong/models/item/mahjong_manual.json",
            "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mchjong:item/mahjong_manual\"}}");
        for (String name : java.util.List.of("mahjong_dye", "creative_mahjong_dye", "red_dora_dye", "undo_dye")) {
            png("item/" + name, resourceTexture("item/" + name));
            text("assets/mchjong/models/item/" + name + ".json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mchjong:item/" + name + "\"}}");
        }
        for (String name : List.of("mahjong_printing_plate", "incomplete_mahjong_box")) {
            png("item/" + name, resourceTexture("item/" + name));
            text("assets/mchjong/models/item/" + name + ".json",
                "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"mchjong:item/" + name + "\"}}");
        }
    }

    private void dice() throws IOException {
        for (int face = 1; face <= 6; face++) png("item/dice_" + face, DiceArtwork.texture(face));
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

    private void models() throws IOException {
        // Furniture is outside the automatic block/item texture directories. Stitch its particle explicitly.
        text("assets/minecraft/atlases/blocks.json",
            "{\"sources\":[{\"type\":\"minecraft:single\",\"resource\":\"mchjong:furniture/wood_oak\"}]}");
        // Opaque interior cuboids put furniture in terrain shadow passes. The visible, component-aware
        // surfaces stay in FurnitureMesh so wood, cloth and moving tiles keep their exact appearance.
        shadowModel("mahjong_table", tableShadowBoxes(false));
        shadowModel("automatic_mahjong_table", tableShadowBoxes(true));
        shadowModel("mahjong_stool", stoolShadowBoxes());
        text("assets/mchjong/models/block/table_space.json",
            "{\"textures\":{\"particle\":\"mchjong:furniture/wood_oak\"},\"elements\":[]}");
        for (String name : new String[]{"mahjong_table", "automatic_mahjong_table", "mahjong_stool", "table_space"}) {
            text("assets/mchjong/blockstates/" + name + ".json", "{\"variants\":{\"\":{\"model\":\"mchjong:block/" + name + "\"}}}");
        }
        for (String name : new String[]{"mahjong_table", "automatic_mahjong_table", "mahjong_stool", "mahjong_tile", "mahjong_box", "table_cloth", "point_stick", "scoring_card"}) {
            if (name.endsWith("mahjong_table") || name.equals("mahjong_stool")) {
                text("assets/mchjong/models/item/" + name + ".json",
                    "{\"parent\":\"minecraft:builtin/entity\",\"textures\":{\"particle\":\"mchjong:furniture/wood_oak\"},\"gui_light\":\"side\"}");
                continue;
            }
            String rotation = name.equals("scoring_card") ? "[90,0,0]" : name.equals("mahjong_tile") ? "[25,35,0]" : "[30,225,0]";
            String lighting = name.equals("mahjong_tile") || name.equals("scoring_card") ? "front" : "side";
            // Vanilla mirrors X translation and Y/Z rotation for the left hand.
            // Like a held sword, the free end rises outward while the printed surface faces inward.
            String held = switch (name) {
                case "mahjong_tile" -> "{\"rotation\":[-12,-65,-25],\"translation\":[1,6,-3],\"scale\":[0.4,0.4,0.4]}";
                case "point_stick", "scoring_card" -> "{\"rotation\":[65,60,25],\"translation\":[1,6,-2],\"scale\":[0.7,0.7,0.7]}";
                default -> "{\"rotation\":[0,30,0],\"scale\":[0.7,0.7,0.7]}";
            };
            text("assets/mchjong/models/item/" + name + ".json", "{\"parent\":\"minecraft:builtin/entity\","
                + "\"textures\":{\"particle\":\"mchjong:furniture/wood_oak\"},\"gui_light\":\"" + lighting + "\",\"display\":{"
                + "\"gui\":{\"rotation\":" + rotation + (name.equals("scoring_card") ? ",\"scale\":[1.6,1.6,1.6]" : "") + "},"
                + "\"ground\":{\"translation\":[0,2,0],\"scale\":[0.5,0.5,0.5]},"
                + "\"head\":{\"rotation\":[0,180,0],\"translation\":[0,13,7],\"scale\":[1,1,1]},"
                + "\"firstperson_righthand\":" + held + ","
                + "\"firstperson_lefthand\":" + held + ","
                + "\"thirdperson_righthand\":{\"rotation\":[75,45,0],\"scale\":[0.6,0.6,0.6]},"
                + "\"thirdperson_lefthand\":{\"rotation\":[75,-45,0],\"scale\":[0.6,0.6,0.6]}}}");
        }
    }

    private void shadowModel(String name, List<float[]> boxes) throws IOException {
        JsonObject model = new JsonObject();
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "mchjong:furniture/wood_oak");
        model.add("textures", textures);
        JsonArray elements = new JsonArray();
        for (float[] box : boxes) {
            JsonObject element = new JsonObject();
            element.add("from", modelPoint(box[0], box[1], box[2]));
            element.add("to", modelPoint(box[3], box[4], box[5]));
            JsonObject faces = new JsonObject();
            for (String side : List.of("up", "down", "north", "south", "east", "west")) {
                JsonObject face = new JsonObject();
                face.addProperty("texture", "#particle");
                faces.add(side, face);
            }
            element.add("faces", faces);
            elements.add(element);
        }
        model.add("elements", elements);
        text("assets/mchjong/models/block/" + name + ".json", model.toString());
    }

    private static JsonArray modelPoint(float x, float y, float z) {
        JsonArray point = new JsonArray();
        point.add(8 + x * 16);
        point.add(y * 16);
        point.add(8 + z * 16);
        return point;
    }

    private static List<float[]> tableShadowBoxes(boolean automatic) {
        List<float[]> boxes = new ArrayList<>();
        boxes.add(new float[]{-1.355f, .79f, -1.355f, 1.355f, .865f, 1.355f});
        boxes.add(new float[]{-1.31f, .875f, -1.42f, 1.31f, .985f, -1.34f});
        boxes.add(new float[]{-1.31f, .875f, 1.34f, 1.31f, .985f, 1.42f});
        boxes.add(new float[]{-1.42f, .875f, -1.31f, -1.34f, .985f, 1.31f});
        boxes.add(new float[]{1.34f, .875f, -1.31f, 1.42f, .985f, 1.31f});
        boxes.add(new float[]{-1.24f, .665f, -1.30f, 1.24f, .82f, -1.24f});
        boxes.add(new float[]{-1.24f, .665f, 1.24f, 1.24f, .82f, 1.30f});
        boxes.add(new float[]{-1.30f, .665f, -1.24f, -1.24f, .82f, 1.24f});
        boxes.add(new float[]{1.24f, .665f, -1.24f, 1.30f, .82f, 1.24f});
        if (automatic) {
            boxes.add(new float[]{-.68f, .04f, -.68f, .68f, .105f, .68f});
            boxes.add(new float[]{-.30f, .18f, -.30f, .30f, .62f, .30f});
            boxes.add(new float[]{-.41f, .67f, -.41f, .41f, .78f, .41f});
        } else {
            for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
                float cx = x * 1.15f, cz = z * 1.15f;
                boxes.add(new float[]{cx - .048f, .03f, cz - .048f, cx + .048f, .77f, cz + .048f});
            }
        }
        return boxes;
    }

    private static List<float[]> stoolShadowBoxes() {
        List<float[]> boxes = new ArrayList<>();
        boxes.add(new float[]{-.35f, .42f, -.35f, .35f, .49f, .35f});
        boxes.add(new float[]{-.31f, .55f, -.31f, .31f, .59f, .31f});
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            float cx = x * .265625f, cz = z * .265625f;
            boxes.add(new float[]{cx - .04f, .025f, cz - .04f, cx + .04f, .42f, cz + .04f});
        }
        boxes.add(new float[]{-.24f, .15f, -.27f, .24f, .18f, -.25f});
        boxes.add(new float[]{-.24f, .15f, .25f, .24f, .18f, .27f});
        boxes.add(new float[]{-.27f, .15f, -.24f, -.25f, .18f, .24f});
        boxes.add(new float[]{.25f, .15f, -.24f, .27f, .18f, .24f});
        return boxes;
    }

    private static BufferedImage resourceTexture(String relative) throws IOException {
        try (var stream = GenerateAssets.class.getResourceAsStream("/assets/mchjong/textures/" + relative + ".png")) {
            if (stream == null) throw new IOException("Resource not found: " + relative);
            return ImageIO.read(stream);
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
        if (relative.endsWith(".json") || relative.endsWith(".mcmeta"))
            text = JSON.toJson(JsonParser.parseString(text));
        Files.writeString(path, text.stripTrailing() + "\n", StandardCharsets.UTF_8);
    }
}
