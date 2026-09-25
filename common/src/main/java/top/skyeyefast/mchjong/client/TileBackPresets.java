package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.config.PresetArchives;
import top.skyeyefast.mchjong.config.BuiltinPresets;

/** The back ID travels with physical tiles; missing artwork resolves to the default pattern. */
public final class TileBackPresets {
    public static final ResourceLocation DEFAULT = ResourceLocation.fromNamespaceAndPath("mchjong", "default");
    private static Map<ResourceLocation, ResourceLocation> local = Map.of(), server = Map.of();
    private static Map<ResourceLocation, String> localNames = Map.of(), serverNames = Map.of();
    private static Set<ResourceLocation> localTextures = Set.of(), serverTextures = Set.of();
    private static List<ResourceLocation> choices = java.util.stream.Stream.concat(
        java.util.stream.Stream.of(DEFAULT), BuiltinPresets.BACKS.stream()).toList();
    private TileBackPresets() {}

    public static List<ResourceLocation> choices() { return choices; }
    public static Component label(ResourceLocation id) {
        if (DEFAULT.equals(id)) return Component.translatable("preset.mchjong.default_back");
        if (id.getPath().equals("creeper") && BuiltinPresets.BACKS.contains(id))
            return Component.translatable("entity.minecraft.creeper");
        if (BuiltinPresets.BACKS.contains(id)) return Component.translatable("preset.mchjong.back.mojang");
        String name = serverNames.getOrDefault(id, localNames.get(id));
        return name == null ? Component.literal(id.toString()) : Component.literal(name);
    }
    public static ResourceLocation texture(ResourceLocation id) {
        if (BuiltinPresets.BACKS.contains(id)) return ResourceLocation.fromNamespaceAndPath("mchjong",
            "textures/preset/back/" + id.getPath() + ".png");
        return server.getOrDefault(id, local.getOrDefault(id, TileMesh.BACK));
    }
    public static void installLocal(Map<ResourceLocation, PresetArchives.Back> backs) throws IOException {
        local = install(backs, "local_backs", localTextures);
        localNames = names(backs);
        localTextures = Set.copyOf(local.values());
        update();
    }
    public static void installServer(Map<ResourceLocation, PresetArchives.Back> backs) throws IOException {
        server = install(backs, "server_backs", serverTextures);
        serverNames = names(backs);
        serverTextures = Set.copyOf(server.values());
        update();
    }
    public static void clearServer() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (var id : serverTextures) textures.release(id);
        server = Map.of(); serverNames = Map.of(); serverTextures = Set.of();
        update();
    }

    private static Map<ResourceLocation, ResourceLocation> install(Map<ResourceLocation, PresetArchives.Back> backs,
            String prefix, Set<ResourceLocation> previous) throws IOException {
        var textures = Minecraft.getInstance().getTextureManager();
        var loaded = new HashMap<ResourceLocation, ResourceLocation>();
        var newTextures = new HashSet<ResourceLocation>();
        var images = new HashMap<ResourceLocation, NativeImage>();
        try {
            for (var entry : backs.entrySet()) {
                ResourceLocation id = entry.getKey();
                if (DEFAULT.equals(id) || BuiltinPresets.BACKS.contains(id))
                    throw new IOException("Reserved back preset ID: " + id);
                var image = NativeImage.read(new ByteArrayInputStream(entry.getValue().image()));
                if (image.getWidth() != 256 || image.getHeight() != 384) {
                    image.close();
                    throw new IOException("Invalid tile back dimensions: " + id);
                }
                images.put(id, image);
            }
            for (var entry : images.entrySet()) {
                ResourceLocation id = entry.getKey();
                ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("mchjong",
                    prefix + "/" + id.getNamespace() + "/" + id.getPath());
                textures.register(texture, new DynamicTexture(entry.getValue()));
                textures.getTexture(texture).setFilter(true, false);
                loaded.put(id, texture);
                newTextures.add(texture);
            }
        } catch (IOException | RuntimeException failure) {
            for (var entry : images.entrySet()) if (!loaded.containsKey(entry.getKey())) entry.getValue().close();
            throw failure;
        }
        for (var old : previous) if (!newTextures.contains(old)) textures.release(old);
        return Map.copyOf(loaded);
    }
    private static Map<ResourceLocation, String> names(Map<ResourceLocation, PresetArchives.Back> backs) {
        var result = new HashMap<ResourceLocation, String>();
        backs.forEach((id, back) -> result.put(id, back.name()));
        return Map.copyOf(result);
    }
    private static void update() {
        var ids = new HashSet<>(local.keySet());
        ids.addAll(server.keySet());
        choices = java.util.stream.Stream.concat(java.util.stream.Stream.of(DEFAULT),
            java.util.stream.Stream.concat(BuiltinPresets.BACKS.stream(),
                ids.stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString)))).toList();
        TileRenderTypes.reload();
    }
}
