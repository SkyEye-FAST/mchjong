package top.skyeyefast.mchjong.client;

import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import top.skyeyefast.mchjong.config.PresetArchives;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.network.PresetBundlePayload;

/** Client resource definitions use Kansai artwork for unavailable IDs. */
public final class TileFacePresets {
    public record Definition(ResourceLocation atlas, ResourceLocation glyphs) {}
    private static Map<TileFacePreset, Definition> definitions = Map.of();
    private static Map<TileFacePreset, Definition> local = Map.of();
    private static Map<TileFacePreset, String> localNames = Map.of();
    private static Map<TileFacePreset, Definition> server = Map.of();
    private static Map<TileFacePreset, String> serverNames = Map.of();
    private static Set<ResourceLocation> serverTextures = Set.of();
    private static Set<ResourceLocation> localTextures = Set.of();
    private static List<TileFacePreset> choices = List.of();
    private static net.minecraft.client.multiplayer.ClientPacketListener connection;
    private static UUID transfer;
    private static PresetArchives.Kind transferKind;
    private static int nextPart, parts;
    private static final ByteArrayOutputStream received = new ByteArrayOutputStream();
    private static final Definition MISSING = new Definition(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation(),
        net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation());
    private TileFacePresets() {}

    public static List<TileFacePreset> choices() { return choices; }
    public static net.minecraft.network.chat.Component label(TileFacePreset preset) {
        String name = serverNames.getOrDefault(preset, localNames.get(preset));
        return name == null ? net.minecraft.network.chat.Component.translatable(preset.translationKey())
            : net.minecraft.network.chat.Component.literal(name);
    }
    public static boolean dynamicTexture(ResourceLocation texture) {
        return serverTextures.contains(texture) || localTextures.contains(texture);
    }
    public static Definition definition(TileFacePreset preset) {
        return definitions.getOrDefault(preset, definitions.getOrDefault(TileFacePreset.KANSAI, MISSING));
    }

    public static void reload(ResourceManager resources) {
        var loaded = new java.util.HashMap<TileFacePreset, Definition>();
        var dynamic = new HashSet<ResourceLocation>();
        resources.listResources("tile_face_presets", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
            try (var reader = resource.openAsReader()) {
                String name = path.getPath().substring("tile_face_presets/".length(), path.getPath().length() - 5);
                var id = new TileFacePreset(ResourceLocation.fromNamespaceAndPath(path.getNamespace(), name));
                if (!id.equals(TileFacePreset.KANSAI) && !id.equals(TileFacePreset.KANTO)) return;
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                Definition definition = new Definition(ResourceLocation.parse(json.get("atlas").getAsString()),
                    ResourceLocation.parse(json.get("glyphs").getAsString()));
                if (resources.getResource(definition.atlas()).isEmpty() || resources.getResource(definition.glyphs()).isEmpty())
                    throw new IllegalArgumentException("Missing atlas or glyph texture");
                loaded.put(id, definition);
            } catch (java.io.IOException | RuntimeException error) {
                com.mojang.logging.LogUtils.getLogger().error("Cannot load tile face preset {}", path, error);
            }
        });
        var names = new HashMap<TileFacePreset, String>();
        try {
            var root = Minecraft.getInstance().gameDirectory.toPath().resolve("config/mchjong/presets");
            var archives = PresetArchives.loadDirectory(root.resolve("faces"), PresetArchives.Kind.FACE);
            for (var entry : archives.faces().entrySet()) {
                loaded.put(entry.getKey(), register(entry.getKey(), entry.getValue().tiles(), "local_faces", dynamic));
                names.put(entry.getKey(), entry.getValue().name());
            }
        } catch (IOException | RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Cannot load local tile face presets", failure);
        }
        try {
            var root = Minecraft.getInstance().gameDirectory.toPath().resolve("config/mchjong/presets/backs");
            TileBackPresets.installLocal(PresetArchives.loadDirectory(root, PresetArchives.Kind.BACK).backs());
        } catch (IOException | RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Cannot load local tile back presets", failure);
        }
        try {
            var root = Minecraft.getInstance().gameDirectory.toPath().resolve("config/mchjong/presets/sticks");
            RiichiStickPresets.installLocal(PresetArchives.loadDirectory(root, PresetArchives.Kind.STICK).sticks());
        } catch (IOException | RuntimeException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Cannot load local riichi stick presets", failure);
        }
        for (var old : localTextures) if (!dynamic.contains(old)) Minecraft.getInstance().getTextureManager().release(old);
        localTextures = Set.copyOf(dynamic);
        local = Map.copyOf(loaded);
        localNames = Map.copyOf(names);
        update();
    }

    public static void tick() {
        if (connection != null && connection != Minecraft.getInstance().getConnection()) clearServer();
    }

    public static void receive(PresetBundlePayload chunk) {
        var current = Minecraft.getInstance().getConnection();
        if (current == null) return;
        if (chunk.part() == 0) {
            transfer = chunk.transfer(); transferKind = chunk.kind(); nextPart = 0; parts = chunk.parts(); received.reset();
            connection = current;
        }
        if (!chunk.transfer().equals(transfer) || chunk.kind() != transferKind || chunk.part() != nextPart || chunk.parts() != parts
            || received.size() + chunk.data().length > PresetArchives.MAX_ARCHIVE_BYTES) {
            transfer = null; transferKind = null; received.reset();
            throw new IllegalArgumentException("Preset bundle chunks arrived out of sequence");
        }
        received.writeBytes(chunk.data());
        if (++nextPart == parts) {
            try {
                var archive = PresetArchives.read(new ByteArrayInputStream(received.toByteArray()), chunk.kind());
                switch (chunk.kind()) {
                    case FACE -> install(archive.faces());
                    case BACK -> TileBackPresets.installServer(archive.backs());
                    case STICK -> RiichiStickPresets.installServer(archive.sticks());
                }
            }
            catch (IOException | RuntimeException failure) { com.mojang.logging.LogUtils.getLogger().error("Cannot load server tile faces", failure); }
            finally { transfer = null; transferKind = null; received.reset(); }
        }
    }

    private static void install(Map<TileFacePreset, PresetArchives.Images> images) throws IOException {
        var textures = Minecraft.getInstance().getTextureManager();
        var loaded = new HashMap<TileFacePreset, Definition>();
        var names = new HashMap<TileFacePreset, String>();
        var locations = new HashSet<ResourceLocation>();
        var composed = new HashMap<TileFacePreset, TileFaceImages.Pair>();
        try {
            for (var entry : images.entrySet()) composed.put(entry.getKey(), TileFaceImages.compose(entry.getValue().tiles()));
        } catch (IOException | RuntimeException failure) {
            for (var pair : composed.values()) { pair.atlas().close(); pair.glyphs().close(); }
            throw failure;
        }
        for (var entry : images.entrySet()) {
            var preset = entry.getKey();
            loaded.put(preset, register(preset, composed.get(preset), "server_faces", locations));
            names.put(preset, entry.getValue().name());
        }
        for (var old : serverTextures) if (!locations.contains(old)) textures.release(old);
        serverTextures = Set.copyOf(locations);
        server = Map.copyOf(loaded);
        serverNames = Map.copyOf(names);
        update();
    }

    private static Definition register(TileFacePreset preset, Map<String, byte[]> images, String prefix,
                                       Set<ResourceLocation> locations) throws IOException {
        return register(preset, TileFaceImages.compose(images), prefix, locations);
    }

    private static Definition register(TileFacePreset preset, TileFaceImages.Pair pair, String prefix,
                                       Set<ResourceLocation> locations) {
        String path = prefix + "/" + preset.id().getNamespace() + "/" + preset.id().getPath();
        var atlasId = ResourceLocation.fromNamespaceAndPath("mchjong", path + "/tiles");
        var glyphsId = ResourceLocation.fromNamespaceAndPath("mchjong", path + "/glyphs");
        var textures = Minecraft.getInstance().getTextureManager();
        textures.register(atlasId, new DynamicTexture(pair.atlas()));
        textures.register(glyphsId, new DynamicTexture(pair.glyphs()));
        textures.getTexture(atlasId).setFilter(true, false);
        textures.getTexture(glyphsId).setFilter(true, false);
        locations.add(atlasId); locations.add(glyphsId);
        return new Definition(atlasId, glyphsId);
    }

    private static void clearServer() {
        for (var location : serverTextures) Minecraft.getInstance().getTextureManager().release(location);
        serverTextures = Set.of(); server = Map.of(); serverNames = Map.of(); connection = null;
        TileBackPresets.clearServer();
        RiichiStickPresets.clearServer();
        transfer = null; transferKind = null; received.reset();
        update();
    }

    private static void update() {
        var merged = new HashMap<>(local);
        merged.putAll(server);
        definitions = Map.copyOf(merged);
        choices = merged.keySet().stream().sorted(java.util.Comparator.comparing(TileFacePreset::getSerializedName)).toList();
        TileRenderTypes.reload();
    }
}
