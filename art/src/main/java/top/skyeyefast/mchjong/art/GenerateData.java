package top.skyeyefast.mchjong.art;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server data has its own entry point and output, independent of SVG artwork. */
public final class GenerateData {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path root;

    private GenerateData(Path root) { this.root = root; }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Expected data output directory");
        GenerateData output = new GenerateData(Path.of(args[0]));
        SurvivalRecipes.generate(output);
        FurnitureData.generate(output);
    }

    void write(String relative, Object value) throws IOException {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJson(ordered(value)) + "\n", StandardCharsets.UTF_8);
    }

    private static Object ordered(Object value) {
        if (value instanceof java.util.Map<?, ?> map) {
            var sorted = new java.util.TreeMap<String, Object>();
            map.forEach((key, item) -> sorted.put(key.toString(), ordered(item)));
            return sorted;
        }
        if (value instanceof java.util.List<?> list) return list.stream().map(GenerateData::ordered).toList();
        return value;
    }
}
