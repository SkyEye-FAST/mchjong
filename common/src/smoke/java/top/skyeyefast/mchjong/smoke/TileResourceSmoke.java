package top.skyeyefast.mchjong.smoke;

import top.skyeyefast.mchjong.platform.ResourceIds;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import top.skyeyefast.mchjong.client.TileMesh;
import top.skyeyefast.mchjong.client.FurnitureMesh;

/** Checks resolved resources and the texture sampling state after real world rendering. */
final class TileResourceSmoke {
    private static final java.util.Map<ResourceLocation, byte[]> originalImages = new java.util.HashMap<>();
    private static final java.util.Map<ResourceLocation, byte[]> originalFurniture = new java.util.HashMap<>();
    private static final java.util.Map<ResourceLocation, byte[]> originalMaterials = new java.util.HashMap<>();
    private TileResourceSmoke() {}

    static void verify(Minecraft client) throws IOException {
        var particleId = ResourceIds.of("mchjong", "furniture/wood_oak");
        var particle = client.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(particleId);
        require(particle.contents().name().equals(particleId), "Furniture particle was not stitched into the block atlas");
        var furniture = new java.util.ArrayList<>(java.util.List.of("felt", "steel", "brass", "edge"));
        for (var wood : top.skyeyefast.mchjong.item.FurnitureWood.values()) furniture.add("wood_" + wood.getSerializedName());
        for (String name : furniture) {
            var texture = ResourceIds.of("mchjong", "textures/furniture/" + name + ".png");
            byte[] bytes;
            try (var stream = client.getResourceManager().open(texture)) { bytes = stream.readAllBytes(); }
            byte[] previous = originalFurniture.putIfAbsent(texture, bytes);
            require(previous == null || Arrays.equals(previous, bytes), "Furniture changed after resource reload: " + name);
            try (var image = NativeImage.read(new ByteArrayInputStream(bytes))) {
                require(image.getWidth() == 16 && image.getHeight() == 16, "Pixel furniture texture missing: " + name);
            }
            client.getTextureManager().getTexture(texture).bind();
            require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER) == GL11.GL_NEAREST,
                "Furniture pixels were blurred: " + name);
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
            for (var type : new net.minecraft.client.renderer.RenderType[]{
                    top.skyeyefast.mchjong.client.TileRenderTypes.body(material),
                    top.skyeyefast.mchjong.client.TileRenderTypes.gui(texture)}) {
                type.setupRenderState();
                client.getTextureManager().getTexture(texture).bind();
                require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER) == GL11.GL_NEAREST,
                    "Tile material pixels were blurred: " + material);
                type.clearRenderState();
            }
        }
        require(client.getResourcePackRepository().getAvailableIds().stream().noneMatch(id -> id.endsWith("patterned_backs")),
            "Retired built-in pack is still registered");
        byte[] backBytes = imageBytes(client, TileMesh.BACK);
        try (var back = NativeImage.read(new ByteArrayInputStream(backBytes))) {
            require(back.getWidth() == 256 && back.getHeight() == 384, "High-resolution back did not reach the client");
            int corner = back.getPixelRGBA(0, 0);
            boolean different = false;
            for (int y = 0; y < back.getHeight(); y++) for (int x = 0; x < back.getWidth(); x++)
                different |= back.getPixelRGBA(x, y) != corner;
            require(!different && corner == 0, "Default back pattern must be transparent");
        }
        var kanto = top.skyeyefast.mchjong.item.TileFacePreset.KANTO;
        var kansai = top.skyeyefast.mchjong.item.TileFacePreset.KANSAI;
        byte[] reference = null;
        for (var preset : java.util.List.of(kansai, kanto)) {
            var source = top.skyeyefast.mchjong.platform.ResourceIds.of("mchjong", "textures/tile_faces/"
                + preset.id().getPath() + "/tiles/1m.png");
            byte[] bytes = imageBytes(client, source);
            if (reference == null) reference = bytes;
            else require(!Arrays.equals(reference, bytes), "Kanto uses the Kansai artwork");
            try (var image = NativeImage.read(new ByteArrayInputStream(bytes))) {
                require(image.getWidth() > 0 && image.getWidth() <= 2048
                    && image.getHeight() > 0 && image.getHeight() <= 2048, "Invalid source tile dimensions");
            }
        }
        var textures = new java.util.ArrayList<ResourceLocation>(java.util.List.of(
            TileMesh.atlas(kansai), TileMesh.BACK, TileMesh.glyphs(kansai), TileMesh.atlas(kanto), TileMesh.glyphs(kanto)));
        for (int points : new int[]{0, 100, 1000, 5000, 10000, -10000}) {
            var texture = FurnitureMesh.stickTexture(points);
            textures.add(texture);
            try (var image = NativeImage.read(new ByteArrayInputStream(imageBytes(client, texture)))) {
                require(image.getWidth() == 384 && image.getHeight() == 32, "Independent point-stick texture dimensions changed");
            }
        }
        for (String icon : java.util.List.of("riichi_stick", "honba_stick")) {
            var id = top.skyeyefast.mchjong.platform.ResourceIds.of("mchjong", "textures/gui/sprites/hud/" + icon + ".png");
            try (var image = NativeImage.read(new ByteArrayInputStream(imageBytes(client, id)))) {
                require(image.getWidth() == 384 && image.getHeight() == 32, "Independent HUD stick texture dimensions changed");
            }
        }
        for (ResourceLocation texture : textures) {
            boolean worldFace = texture.equals(TileMesh.glyphs(kansai)) || texture.equals(TileMesh.glyphs(kanto));
            if (worldFace) {
                var renderType = top.skyeyefast.mchjong.client.TileRenderTypes.faces(texture.equals(TileMesh.glyphs(kansai)) ? kansai : kanto);
                renderType.setupRenderState();
                renderType.clearRenderState();
            }
            client.getTextureManager().getTexture(texture).bind();
            if (worldFace || texture.equals(TileMesh.atlas(kansai)) || texture.equals(TileMesh.atlas(kanto)))
                require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) == 2048
                    && GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) == 4096,
                    "Independent tiles were not composed for rendering: " + texture);
            require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER) == GL11.GL_LINEAR,
                "World renderer disabled linear magnification for " + texture);
            require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER)
                == (worldFace ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR), "Incorrect world/GUI minification for " + texture);
            if (worldFace) require(GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 5, GL11.GL_TEXTURE_WIDTH) == 64,
                "World face mip chain was not uploaded: " + texture);
        }
    }

    private static byte[] imageBytes(Minecraft client, ResourceLocation texture) throws IOException {
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
