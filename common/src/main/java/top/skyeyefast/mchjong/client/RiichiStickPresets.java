package top.skyeyefast.mchjong.client;

import top.skyeyefast.mchjong.platform.ResourceIds;
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

/** Local rendering choices for a player's riichi deposit; selections remain personal. */
public final class RiichiStickPresets {
    public static final ResourceLocation DEFAULT = ResourceIds.of("mchjong", "default");
    public record Definition(String name, ResourceLocation texture, float length, float width, float height) {}
    private static Map<ResourceLocation, Definition> local = Map.of(), server = Map.of();
    private static final Map<String, ResourceLocation> appearances = new HashMap<>();
    private static Set<ResourceLocation> localTextures = Set.of(), serverTextures = Set.of();
    private static List<ResourceLocation> choices = java.util.stream.Stream.concat(
        java.util.stream.Stream.of(DEFAULT), BuiltinPresets.STICKS.stream()).toList();
    private RiichiStickPresets() {}

    public static List<ResourceLocation> choices() { return choices; }
    public static Definition definition(ResourceLocation id) {
        return server.getOrDefault(id, local.get(id));
    }
    public static Component label(ResourceLocation id) {
        if (DEFAULT.equals(id)) return Component.translatable("preset.mchjong.default_stick");
        if (BuiltinPresets.STICKS.contains(id)) return Component.translatable("block.minecraft." + id.getPath());
        var definition = definition(id);
        return definition == null ? Component.literal(id.toString()) : Component.literal(definition.name());
    }
    public static ResourceLocation texture(ResourceLocation id) {
        var definition = definition(id);
        return definition == null ? RiichiStickModel.TEXTURE : definition.texture();
    }
    public static ResourceLocation forPlayer(String name) {
        return appearances.getOrDefault(name, DEFAULT);
    }
    public static void receive(top.skyeyefast.mchjong.network.StickAppearancePayload payload) {
        appearances.put(payload.playerName(), payload.preset());
    }
    public static void sendChoice() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.send(top.skyeyefast.mchjong.network.PayloadPackets.serverbound(
            new top.skyeyefast.mchjong.network.StickChoicePayload(TableSettings.get().riichiStickPreset)));
    }
    public static void installLocal(Map<ResourceLocation, PresetArchives.Stick> presets) throws IOException {
        local = install(presets, "local_sticks", localTextures);
        localTextures = textures(local);
        update();
    }
    public static void installServer(Map<ResourceLocation, PresetArchives.Stick> presets) throws IOException {
        server = install(presets, "server_sticks", serverTextures);
        serverTextures = textures(server);
        update();
        sendChoice();
    }
    public static void clearServer() {
        for (var id : serverTextures) Minecraft.getInstance().getTextureManager().release(id);
        server = Map.of(); serverTextures = Set.of(); appearances.clear(); update();
    }
    private static Map<ResourceLocation, Definition> install(Map<ResourceLocation, PresetArchives.Stick> presets,
            String prefix, Set<ResourceLocation> previous) throws IOException {
        var manager = Minecraft.getInstance().getTextureManager();
        var images = new HashMap<ResourceLocation, NativeImage>();
        var result = new HashMap<ResourceLocation, Definition>();
        try {
            for (var entry : presets.entrySet()) {
                if (DEFAULT.equals(entry.getKey()) || BuiltinPresets.STICKS.contains(entry.getKey()))
                    throw new IOException("Reserved riichi stick preset ID");
                var image = NativeImage.read(new ByteArrayInputStream(entry.getValue().image()));
                if (image.getWidth() != 384 || image.getHeight() != 32) {
                    image.close();
                    throw new IOException("Invalid riichi stick texture: " + entry.getKey());
                }
                images.put(entry.getKey(), image);
            }
            for (var entry : presets.entrySet()) {
                var id = entry.getKey();
                var stick = entry.getValue();
                var texture = ResourceIds.of("mchjong",
                    prefix + "/" + id.getNamespace() + "/" + id.getPath());
                manager.register(texture, new DynamicTexture(images.get(id)));
                manager.getTexture(texture).setFilter(true, false);
                result.put(id, new Definition(stick.name(), texture, stick.length(), stick.width(), stick.height()));
            }
        } catch (IOException | RuntimeException failure) {
            for (var entry : images.entrySet()) if (!result.containsKey(entry.getKey())) entry.getValue().close();
            throw failure;
        }
        var current = textures(result);
        for (var id : previous) if (!current.contains(id)) manager.release(id);
        return Map.copyOf(result);
    }
    private static Set<ResourceLocation> textures(Map<ResourceLocation, Definition> definitions) {
        var result = new HashSet<ResourceLocation>();
        definitions.values().forEach(definition -> result.add(definition.texture()));
        return Set.copyOf(result);
    }
    private static void update() {
        var ids = new HashSet<>(local.keySet()); ids.addAll(server.keySet());
        choices = java.util.stream.Stream.concat(java.util.stream.Stream.of(DEFAULT),
            java.util.stream.Stream.concat(BuiltinPresets.STICKS.stream(),
                ids.stream().sorted(java.util.Comparator.comparing(ResourceLocation::toString)))).toList();
    }
}
