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
    record Material(String name, String source) {}
    static final List<Material> MATERIALS = List.of(new Material("bone", "bone_block"),
        new Material("quartz", "quartz_block"), new Material("calcite", "calcite"),
        new Material("glass", "glass"), new Material("amethyst", "amethyst_block"));
    private SurvivalRecipes() {}

    static void generate(GenerateData output) throws IOException {
        output.write("data/mchjong/recipes/dice.json", Map.of("type", "minecraft:crafting_shapeless",
            "category", "misc", "ingredients", List.of(item("bone_meal"), item("white_dye"), item("black_dye")),
            "result", stack("dice", 2, Map.of())));
        for (String wood : WOODS) {
            var key = Map.of("S", item(wood + "_slab"), "F", item(wood + "_fence"));
            String suffix = wood.equals("oak") ? "" : "_" + wood;
            shaped(output, "mahjong_table" + suffix, List.of("SSS", "S S", "F F"), key,
                stack("mahjong_table", 1, Map.of("wood", wood)));
            shaped(output, "mahjong_stool" + suffix, List.of("CC", "SS", "FF"),
                Map.of("S", item(wood + "_slab"), "F", item(wood + "_fence"), "C", item("white_carpet")),
                stack("mahjong_stool", 2, Map.of("wood", wood)));
        }
        for (String color : COLORS)
            shaped(output, "table_cloth_" + color, List.of("CCC"), Map.of("C", item(color + "_carpet")),
                stack("table_cloth", 1, Map.of("color", COLORS.indexOf(color))));
        shaped(output, "mahjong_box", List.of("LSL", "SCS", " I "),
            Map.of("S", Map.of("tag", "minecraft:wooden_slabs"), "L", item("leather"),
                "C", item("chest"), "I", item("iron_nugget")), stack("mahjong_box", 1, Map.of()));
        for (String wood : WOODS)
            stonecutting(output, "blanks_" + wood, item(wood + "_planks"), tile(-1, wood, false, 16));
        for (Material material : MATERIALS) {
            stonecutting(output, "blanks_" + material.name, item(material.source), tile(-1, material.name, false, 16));
        }
        stonecutting(output, "blank_point_sticks", item("bone_block"), stack("point_stick", 24, Map.of()));
        output.write("data/mchjong/recipes/mahjong_dye.json", Map.of("type", "minecraft:crafting_shapeless",
            "category", "misc", "ingredients", List.of(item("black_dye"), item("red_dye"), item("green_dye"), item("blue_dye"), item("white_dye")),
            "result", stack("mahjong_dye", 1, Map.of())));
        output.write("data/mchjong/recipes/red_dora_dye.json", Map.of("type", "minecraft:crafting_shapeless",
            "category", "misc", "ingredients", List.of(item("red_dye")), "result", stack("red_dora_dye", 4, Map.of())));
        output.write("data/mchjong/recipes/undo_dye.json", Map.of("type", "minecraft:crafting_shapeless",
            "category", "misc", "ingredients", List.of(item("black_dye")), "result", stack("undo_dye", 4, Map.of())));
        for (String operation : List.of("dye", "red_five", "undo_red_five", "mark_stick", "upgrade_table"))
            output.write("data/mchjong/recipes/" + operation + ".json", Map.of("type", "mchjong:" + operation, "category", "misc"));
    }

    private static void shaped(GenerateData output, String name, List<String> pattern,
                               Map<String, ?> key, Map<String, ?> result) throws IOException {
        var stack = new LinkedHashMap<>(result);
        Object nbt = stack.remove("nbt");
        var recipe = new LinkedHashMap<String, Object>(Map.of("type", "mchjong:shaped",
            "category", "misc", "pattern", pattern, "key", key, "result", stack));
        if (nbt != null) recipe.put("result_nbt", nbt);
        output.write("data/mchjong/recipes/" + name + ".json", recipe);
    }
    private static void stonecutting(GenerateData output, String name, Map<String, ?> ingredient,
                                    Map<String, ?> result) throws IOException {
        var recipe = new LinkedHashMap<String, Object>(Map.of("type", "mchjong:stonecutting",
            "ingredient", ingredient, "result", result.get("item"), "count", result.get("count")));
        if (result.containsKey("nbt")) recipe.put("result_nbt", result.get("nbt"));
        output.write("data/mchjong/recipes/" + name + ".json", recipe);
    }
    private static Map<String, String> item(String id) { return Map.of("item", "minecraft:" + id); }
    private static Map<String, Object> tile(int face, String material, boolean red, int count) {
        return stack("mahjong_tile", count, Map.of("tile", new java.util.TreeMap<>(Map.of("face", face, "material", material, "red", red))));
    }
    static Map<String, Object> stack(String id, int count, Map<String, ?> components) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("item", "mchjong:" + id);
        result.put("count", count);
        if (!components.isEmpty()) result.put("nbt", new com.google.gson.Gson().toJson(Map.of("mchjong", new java.util.TreeMap<>(components))));
        return result;
    }
}
