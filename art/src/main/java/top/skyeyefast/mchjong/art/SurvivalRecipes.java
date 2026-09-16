package top.skyeyefast.mchjong.art;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small declarative recipe families; recipe IDs are variants, item IDs are not. */
final class SurvivalRecipes {
    static final List<String> WOODS = List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
        "mangrove", "cherry", "bamboo", "crimson", "warped");
    static final List<String> COLORS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    private record Material(String name, String source, boolean tag) {}
    private static final List<Material> MATERIALS = List.of(new Material("wood", "planks", true),
        new Material("bone", "bone_block", false), new Material("quartz", "quartz_block", false),
        new Material("calcite", "calcite", false), new Material("glass", "glass", false),
        new Material("amethyst", "amethyst_block", false));
    private SurvivalRecipes() {}

    static void generate(GenerateData output) throws IOException {
        for (String wood : WOODS) {
            var key = Map.of("S", item(wood + "_slab"), "F", item(wood + "_fence"));
            String suffix = wood.equals("oak") ? "" : "_" + wood;
            shaped(output, "mahjong_table" + suffix, List.of("SSS", "S S", "F F"), key,
                stack("mahjong_table", 1, Map.of("mchjong:wood", wood)));
            shaped(output, "mahjong_stool" + suffix, List.of("CC", "SS", "FF"),
                Map.of("S", item(wood + "_slab"), "F", item(wood + "_fence"), "C", item("white_carpet")),
                stack("mahjong_stool", 2, Map.of("mchjong:wood", wood)));
        }
        for (String color : COLORS)
            shaped(output, "table_cloth_" + color, List.of("CCC"), Map.of("C", item(color + "_carpet")),
                stack("table_cloth", 1, Map.of("minecraft:base_color", color)));
        output.write("data/mchjong/recipe/mahjong_box.json", Map.of("type", "minecraft:crafting_shapeless",
            "category", "misc", "ingredients", List.of(item("chest"), item("string")), "result", stack("mahjong_box", 1, Map.of())));
        for (Material material : MATERIALS) {
            var ingredient = Map.of(material.tag ? "tag" : "item", "minecraft:" + material.source);
            output.write("data/mchjong/recipe/blanks_" + material.name + ".json", Map.of(
                "type", "minecraft:stonecutting", "ingredient", ingredient,
                "result", tile(-1, material.name, false, 16)));
        }
        output.write("data/mchjong/recipe/blank_point_sticks.json", Map.of("type", "minecraft:stonecutting",
            "ingredient", item("bone_block"), "result", stack("point_stick", 16, Map.of("mchjong:points", 0))));
        for (int face = 0; face < 37; face++) {
            boolean red = face >= 34;
            int kind = red ? 4 + (face - 34) * 9 : face;
            output.write("data/mchjong/recipe/engrave_tile_" + face + ".json", Map.of(
                "type", "mchjong:engrave_tile", "group", "mchjong:engrave_tile",
                "ingredient", Map.of("item", "mchjong:mahjong_tile"), "result", tile(kind, "bone", red, 1)));
        }
        for (String operation : List.of("dye", "engrave_set", "mark_stick", "upgrade_table"))
            output.write("data/mchjong/recipe/" + operation + ".json", Map.of("type", "mchjong:" + operation, "category", "misc"));
    }

    private static void shaped(GenerateData output, String name, List<String> pattern,
                               Map<String, ?> key, Map<String, ?> result) throws IOException {
        output.write("data/mchjong/recipe/" + name + ".json", Map.of("type", "minecraft:crafting_shaped",
            "category", "misc", "pattern", pattern, "key", key, "result", result));
    }
    private static Map<String, String> item(String id) { return Map.of("item", "minecraft:" + id); }
    private static Map<String, Object> tile(int face, String material, boolean red, int count) {
        return stack("mahjong_tile", count, Map.of("mchjong:tile", Map.of("face", face, "material", material, "red", red),
            "minecraft:base_color", "blue"));
    }
    private static Map<String, Object> stack(String id, int count, Map<String, ?> components) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", "mchjong:" + id);
        result.put("count", count);
        if (!components.isEmpty()) result.put("components", components);
        return result;
    }
}
