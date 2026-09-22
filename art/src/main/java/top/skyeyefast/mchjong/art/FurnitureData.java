package top.skyeyefast.mchjong.art;

import java.io.IOException;
import java.util.List;
import java.util.Map;

final class FurnitureData {
    private FurnitureData() {}

    static void generate(GenerateData output) throws IOException {
        for (String furniture : List.of("mahjong_table", "automatic_mahjong_table", "mahjong_stool")) {
            var copies = new java.util.ArrayList<Map<String, String>>();
            copies.add(Map.of("source", "wood", "target", "mchjong.wood", "op", "replace"));
            if (furniture.equals("mahjong_stool")) copies.add(Map.of("source", "color", "target", "mchjong.color", "op", "replace"));
            output.write("data/mchjong/loot_tables/blocks/" + furniture + ".json", Map.of(
                "type", "minecraft:block", "pools", List.of(Map.of("rolls", 1,
                    "entries", List.of(Map.of("type", "minecraft:item", "name", "mchjong:" + furniture,
                        "functions", List.of(Map.of("function", "minecraft:copy_nbt", "source", "block_entity", "ops", copies)))),
                    "conditions", List.of(Map.of("condition", "minecraft:survives_explosion"))))));
        }
        output.write("data/minecraft/tags/blocks/mineable/axe.json", Map.of("replace", false,
            "values", List.of("mchjong:mahjong_table", "mchjong:mahjong_stool", "mchjong:table_space")));
        output.write("data/minecraft/tags/blocks/mineable/pickaxe.json", Map.of("replace", false,
            "values", List.of("mchjong:automatic_mahjong_table")));
    }
}
