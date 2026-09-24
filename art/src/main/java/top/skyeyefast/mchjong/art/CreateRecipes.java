package top.skyeyefast.mchjong.art;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static top.skyeyefast.mchjong.art.SurvivalRecipes.stack;

/** Shared 1.20.1 recipe data; only the loader's mod-presence condition differs. */
final class CreateRecipes {
    private CreateRecipes() {}

    static void generate(GenerateData output, String loader) throws IOException {
        if (!loader.equals("fabric") && !loader.equals("forge")) throw new IllegalArgumentException("Unknown Create loader: " + loader);
        for (var wood : SurvivalRecipes.WOODS) blanks(output, loader, wood, wood + "_planks");
        for (var material : SurvivalRecipes.MATERIALS) blanks(output, loader, material.name(), material.source());
        processing(output, loader, "blank_point_sticks", "cutting", List.of(item("minecraft:bone_block")),
            List.of(stack("point_stick", 32, Map.of("points", 0))));
        processing(output, loader, "mahjong_dye", "mixing", List.of(item("minecraft:black_dye"), item("minecraft:red_dye"),
            item("minecraft:green_dye"), item("minecraft:blue_dye"), item("minecraft:white_dye")), List.of(stack("mahjong_dye", 2, Map.of())));
        processing(output, loader, "red_dora_dye", "mixing", List.of(item("minecraft:red_dye")), List.of(stack("red_dora_dye", 8, Map.of())));
        processing(output, loader, "undo_dye", "mixing", List.of(item("minecraft:black_dye")), List.of(stack("undo_dye", 8, Map.of())));
        for (String preset : List.of("kansai", "kanto")) {
            var plate = new LinkedHashMap<>(stack("mahjong_printing_plate", 1, Map.of("face_preset", "mchjong:" + preset)));
            var nbt = plate.remove("nbt");
            write(output, loader, "printing_plate_" + preset, Map.of("type", "mchjong:shaped", "category", "misc",
                "pattern", List.of(" P ", "III"), "key", Map.of("P", item("minecraft:" + (preset.equals("kansai") ? "paper" : "bamboo")),
                    "I", item("minecraft:iron_ingot")), "result", plate, "result_nbt", nbt));
        }
        var transitional = stack("incomplete_mahjong_box", 1, Map.of());
        var incomplete = item("mchjong:incomplete_mahjong_box");
        write(output, loader, "mahjong_box", Map.of("type", "create:sequenced_assembly", "ingredient", item("minecraft:chest"),
            "transitionalItem", transitional, "loops", 1, "results", List.of(stack("mahjong_box", 1, Map.of())),
            "sequence", List.of(
                Map.of("type", "create:deploying", "ingredients", List.of(incomplete, item("minecraft:leather")), "results", List.of(transitional)),
                Map.of("type", "create:deploying", "ingredients", List.of(incomplete, item("minecraft:iron_nugget")), "results", List.of(transitional)),
                Map.of("type", "create:pressing", "ingredients", List.of(incomplete), "results", List.of(transitional)))));
    }

    private static void blanks(GenerateData output, String loader, String material, String source) throws IOException {
        processing(output, loader, "blanks_" + material, "cutting", List.of(item("minecraft:" + source)),
            List.of(stack("mahjong_tile", 24, Map.of("tile", Map.of("face", -1, "material", material, "red", false)))));
    }

    private static void processing(GenerateData output, String loader, String id, String type, List<?> inputs, List<?> outputs) throws IOException {
        write(output, loader, id, Map.of("type", "create:" + type, "ingredients", inputs, "results", outputs, "processingTime", 100));
    }

    private static void write(GenerateData output, String loader, String id, Map<String, ?> recipe) throws IOException {
        var conditional = new LinkedHashMap<String, Object>(recipe);
        if (loader.equals("fabric")) conditional.put("fabric:load_conditions",
            List.of(Map.of("condition", "fabric:all_mods_loaded", "values", List.of("create"))));
        else conditional.put("conditions", List.of(Map.of("type", "forge:mod_loaded", "modid", "create")));
        output.write("data/mchjong/recipes/create/" + id + ".json", conditional);
    }

    private static Map<String, String> item(String id) { return Map.of("item", id); }
}
