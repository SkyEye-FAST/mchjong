package top.skyeyefast.mchjong.art;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Static Create recipes only; all component-dependent operations use MahjongSupplies at runtime. */
final class CreateRecipes {
    private CreateRecipes() {}

    static void generate(GenerateData output) throws IOException {
        for (var wood : SurvivalRecipes.WOODS) blanks(output, wood, wood + "_planks");
        for (var material : SurvivalRecipes.MATERIALS) blanks(output, material.name(), material.source());
        processing(output, "blank_point_sticks", "cutting", List.of(item("minecraft:bone_block")),
            List.of(stack("point_stick", 32, Map.of("mchjong:points", 0))));
        processing(output, "mahjong_dye", "mixing", List.of(item("minecraft:black_dye"), item("minecraft:red_dye"),
            item("minecraft:green_dye"), item("minecraft:blue_dye"), item("minecraft:white_dye")), List.of(stack("mahjong_dye", 2, Map.of())));
        processing(output, "red_dora_dye", "mixing", List.of(item("minecraft:red_dye")), List.of(stack("red_dora_dye", 8, Map.of())));
        processing(output, "undo_dye", "mixing", List.of(item("minecraft:black_dye")), List.of(stack("undo_dye", 8, Map.of())));
        write(output, "printing_plate", Map.of("type", "minecraft:crafting_shaped", "category", "misc",
            "pattern", List.of(" P ", "III"), "key", Map.of("P", item("minecraft:paper"),
                "I", item("minecraft:iron_ingot")), "result", stack("mahjong_printing_plate", 1, Map.of())));
        var transitional = stack("incomplete_mahjong_box", 1, Map.of());
        var incomplete = item("mchjong:incomplete_mahjong_box");
        write(output, "mahjong_box", Map.of("type", "create:sequenced_assembly", "ingredient", item("minecraft:chest"),
            "transitional_item", transitional, "loops", 1, "results", List.of(stack("mahjong_box", 1, Map.of())),
            "sequence", List.of(
                Map.of("type", "create:deploying", "ingredients", List.of(incomplete, item("minecraft:leather")), "results", List.of(transitional)),
                Map.of("type", "create:deploying", "ingredients", List.of(incomplete, item("minecraft:iron_nugget")), "results", List.of(transitional)),
                Map.of("type", "create:pressing", "ingredients", List.of(incomplete), "results", List.of(transitional)))));
    }

    private static void blanks(GenerateData output, String material, String source) throws IOException {
        processing(output, "blanks_" + material, "cutting", List.of(item("minecraft:" + source)),
            List.of(stack("mahjong_tile", 24, Map.of("mchjong:tile", Map.of("face", -1, "material", material, "red", false)))));
    }

    private static void processing(GenerateData output, String id, String type, List<?> inputs, List<?> outputs) throws IOException {
        write(output, id, Map.of("type", "create:" + type, "ingredients", inputs, "results", outputs, "processing_time", 100));
    }

    private static void write(GenerateData output, String id, Map<String, ?> recipe) throws IOException {
        var conditional = new LinkedHashMap<String, Object>(recipe);
        conditional.put("neoforge:conditions", List.of(Map.of("type", "neoforge:mod_loaded", "modid", "create")));
        output.write("data/mchjong/recipe/create/" + id + ".json", conditional);
    }

    private static Map<String, String> item(String id) { return Map.of("item", id); }
    private static Map<String, Object> stack(String id, int count, Map<String, ?> components) {
        return components.isEmpty() ? Map.of("id", "mchjong:" + id, "count", count)
            : Map.of("id", "mchjong:" + id, "count", count, "components", components);
    }
}
