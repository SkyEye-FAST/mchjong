package top.skyeyefast.mchjong.art;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server data generation is independent of textures, SVG artwork and client models. */
public final class GenerateData {
    private final Path root;

    private GenerateData(Path root) { this.root = root; }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Expected data output directory");
        new GenerateData(Path.of(args[0])).generate();
    }

    private void generate() throws IOException {
        for (String name : new String[]{"mahjong_table", "mahjong_stool"})
            text("data/mchjong/loot_table/blocks/" + name + ".json", "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"mchjong:" + name + "\"}],\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}");
        text("data/mchjong/recipe/mahjong_table.json", "{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\",\"pattern\":[\"PPP\",\"GIG\",\"P P\"],\"key\":{\"P\":{\"tag\":\"minecraft:planks\"},\"G\":{\"item\":\"minecraft:green_carpet\"},\"I\":{\"item\":\"minecraft:iron_ingot\"}},\"result\":{\"id\":\"mchjong:mahjong_table\",\"count\":1}}");
        text("data/mchjong/recipe/mahjong_stool.json", "{\"type\":\"minecraft:crafting_shaped\",\"category\":\"misc\",\"pattern\":[\"GG\",\"PP\"],\"key\":{\"P\":{\"tag\":\"minecraft:planks\"},\"G\":{\"item\":\"minecraft:green_wool\"}},\"result\":{\"id\":\"mchjong:mahjong_stool\",\"count\":1}}");
        text("data/minecraft/tags/block/mineable/axe.json", "{\"replace\":false,\"values\":[\"mchjong:mahjong_table\",\"mchjong:mahjong_stool\",\"mchjong:table_space\"]}");
    }

    private void text(String relative, String text) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text + "\n", StandardCharsets.UTF_8);
    }
}
