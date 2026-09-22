package top.skyeyefast.mchjong.client;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** The winning pack supplies each definition, just like any other Minecraft resource. */
public final class TileFacePresets {
    public record Definition(ResourceLocation atlas, ResourceLocation glyphs) {}
    private static Map<TileFacePreset, Definition> definitions = Map.of();
    private static List<TileFacePreset> choices = List.of();
    private static final Definition MISSING = new Definition(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation(),
        net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation());
    private TileFacePresets() {}

    public static List<TileFacePreset> choices() { return choices; }
    public static Definition definition(TileFacePreset preset) {
        return definitions.getOrDefault(preset, MISSING);
    }

    public static void reload(ResourceManager resources) {
        var loaded = new java.util.HashMap<TileFacePreset, Definition>();
        resources.listResources("tile_face_presets", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            try (var reader = resource.openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                String name = path.getPath().substring("tile_face_presets/".length(), path.getPath().length() - 5);
                var id = new TileFacePreset(new ResourceLocation(path.getNamespace(), name));
                var definition = new Definition(new ResourceLocation(json.get("atlas").getAsString()),
                    new ResourceLocation(json.get("glyphs").getAsString()));
                if (resources.getResource(definition.atlas()).isEmpty() || resources.getResource(definition.glyphs()).isEmpty())
                    throw new IllegalArgumentException("Missing atlas or glyph texture");
                loaded.put(id, definition);
            } catch (java.io.IOException | RuntimeException error) {
                com.mojang.logging.LogUtils.getLogger().error("Cannot load tile face preset {}", path, error);
            }
        });
        definitions = Map.copyOf(loaded);
        choices = loaded.keySet().stream().sorted(java.util.Comparator.comparing(TileFacePreset::getSerializedName)).toList();
        TileRenderTypes.reload();
    }
}
