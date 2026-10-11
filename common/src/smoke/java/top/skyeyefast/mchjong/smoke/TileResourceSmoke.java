package top.skyeyefast.mchjong.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import top.skyeyefast.mchjong.client.TileMesh;
import top.skyeyefast.mchjong.client.FurnitureMesh;

/** Checks the resolved artwork and resource reload stability. */
final class TileResourceSmoke {
    private static final java.util.Map<Identifier, byte[]> originalImages = new java.util.HashMap<>();
    private static final java.util.Map<Identifier, byte[]> originalFurniture = new java.util.HashMap<>();
    private static final java.util.Map<Identifier, byte[]> originalMaterials = new java.util.HashMap<>();
    private TileResourceSmoke() {}

    static void verify(Minecraft client) throws IOException {
        var furniture = new java.util.ArrayList<>(java.util.List.of("felt", "steel", "brass", "edge"));
        for (var wood : top.skyeyefast.mchjong.item.FurnitureWood.values()) furniture.add("wood_" + wood.getSerializedName());
        for (String name : furniture) {
            var texture = Identifier.fromNamespaceAndPath("mchjong", "textures/furniture/" + name + ".png");
            byte[] bytes;
            try (var stream = client.getResourceManager().open(texture)) { bytes = stream.readAllBytes(); }
            byte[] previous = originalFurniture.putIfAbsent(texture, bytes);
            require(previous == null || Arrays.equals(previous, bytes), "Furniture changed after resource reload: " + name);
            try (var image = NativeImage.read(new ByteArrayInputStream(bytes))) {
                require(image.getWidth() == 16 && image.getHeight() == 16, "Pixel furniture texture missing: " + name);
            }
        }
        for (var material : top.skyeyefast.mchjong.item.TileMaterial.values()) {
            var texture = top.skyeyefast.mchjong.client.TileRenderTypes.bodyTexture(material);
            byte[] bytes;
            try (var stream = client.getResourceManager().open(texture)) { bytes = stream.readAllBytes(); }
            byte[] previous = originalMaterials.putIfAbsent(texture, bytes);
            require(previous == null || Arrays.equals(previous, bytes), "Tile material changed after reload: " + material);
            try (var image = NativeImage.read(new ByteArrayInputStream(bytes))) {
                require(image.getWidth() == 16 && image.getHeight() == 16, "Low-resolution tile material missing: " + material);
            }
        }
        require(client.getResourcePackRepository().getAvailableIds().stream().noneMatch(id -> id.endsWith("patterned_backs")),
            "Retired built-in pack is still registered");
        byte[] backBytes = imageBytes(client, TileMesh.BACK);
        try (var back = NativeImage.read(new ByteArrayInputStream(backBytes))) {
            require(back.getWidth() == 256 && back.getHeight() == 384, "High-resolution back did not reach the client");
            int corner = back.getPixel(0, 0);
            boolean different = false;
            for (int y = 0; y < back.getHeight(); y++) for (int x = 0; x < back.getWidth(); x++)
                different |= back.getPixel(x, y) != corner;
            require(!different && corner == 0, "Default back pattern must be transparent");
        }
        var kanto = top.skyeyefast.mchjong.item.TileFacePreset.KANTO;
        var kansai = top.skyeyefast.mchjong.item.TileFacePreset.KANSAI;
        byte[] reference = null;
        for (var preset : java.util.List.of(kansai, kanto)) {
            var source = Identifier.fromNamespaceAndPath("mchjong", "textures/tile_faces/"
                + preset.id().getPath() + "/tiles/1m.png");
            byte[] bytes = imageBytes(client, source);
            if (reference == null) reference = bytes;
            else require(!Arrays.equals(reference, bytes), "Kanto uses the Kansai artwork");
            try (var image = NativeImage.read(new ByteArrayInputStream(bytes))) {
                require(image.getWidth() > 0 && image.getWidth() <= 2048
                    && image.getHeight() > 0 && image.getHeight() <= 2048, "Invalid source tile dimensions");
            }
        }
        for (int points : new int[]{0, 100, 1000, 5000, 10000, -10000}) {
            try (var image = NativeImage.read(new ByteArrayInputStream(imageBytes(client, FurnitureMesh.stickTexture(points))))) {
                require(image.getWidth() == 384 && image.getHeight() == 32, "Independent point-stick texture dimensions changed");
            }
        }
        for (var preset : java.util.List.of(kansai, kanto)) {
            for (var id : java.util.List.of(TileMesh.atlas(preset), TileMesh.glyphs(preset))) {
                var texture = client.getTextureManager().getTexture(id);
                require(texture.getTexture().getWidth(0) == 2048 && texture.getTexture().getHeight(0) == 4096,
                    "Independent tiles were not composed for rendering: " + id);
                require(texture.getSampler().getMagFilter() == com.mojang.blaze3d.textures.FilterMode.LINEAR
                    && texture.getSampler().getMinFilter() == com.mojang.blaze3d.textures.FilterMode.LINEAR,
                    "Composed tile sampling changed: " + id);
            }
        }
    }

    private static byte[] imageBytes(Minecraft client, Identifier texture) throws IOException {
        byte[] bytes;
        try (var stream = client.getResourceManager().open(texture)) { bytes = stream.readAllBytes(); }
        byte[] previous = originalImages.putIfAbsent(texture, bytes);
        require(previous == null || Arrays.equals(previous, bytes), "Resource changed after reload: " + texture);
        return bytes;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
