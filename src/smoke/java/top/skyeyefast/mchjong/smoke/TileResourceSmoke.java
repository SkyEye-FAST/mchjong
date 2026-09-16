package top.skyeyefast.mchjong.smoke;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import top.skyeyefast.mchjong.client.TileMesh;

/** Checks resolved resources and the texture sampling state after real world rendering. */
final class TileResourceSmoke {
    private static byte[] originalAtlas;
    private static byte[] originalBack;
    private static byte[] originalGlyphs;
    private static final java.util.Map<ResourceLocation, byte[]> originalFurniture = new java.util.HashMap<>();
    private TileResourceSmoke() {}

    static void verify(Minecraft client) throws IOException {
        var particleId = ResourceLocation.fromNamespaceAndPath("mchjong", "furniture/wood_oak");
        var particle = client.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(particleId);
        require(particle.contents().name().equals(particleId), "Furniture particle was not stitched into the block atlas");
        var furniture = new java.util.ArrayList<>(java.util.List.of("felt", "steel", "brass", "edge"));
        for (var wood : top.skyeyefast.mchjong.item.FurnitureWood.values()) furniture.add("wood_" + wood.getSerializedName());
        for (String name : furniture) {
            var texture = ResourceLocation.fromNamespaceAndPath("mchjong", "textures/furniture/" + name + ".png");
            byte[] bytes;
            try (var stream = client.getResourceManager().open(texture)) { bytes = stream.readAllBytes(); }
            byte[] previous = originalFurniture.putIfAbsent(texture, bytes);
            require(previous == null || Arrays.equals(previous, bytes), "Furniture changed after resource reload: " + name);
            try (var image = NativeImage.read(new ByteArrayInputStream(bytes))) {
                require(image.getWidth() == 64 && image.getHeight() == 64, "Original furniture texture missing: " + name);
            }
        }
        require(client.getResourcePackRepository().getAvailableIds().stream().noneMatch(id -> id.endsWith("patterned_backs")),
            "Retired built-in pack is still registered");
        byte[] atlasBytes;
        byte[] backBytes;
        byte[] glyphBytes;
        try (var stream = client.getResourceManager().open(TileMesh.ATLAS)) { atlasBytes = stream.readAllBytes(); }
        try (var stream = client.getResourceManager().open(TileMesh.BACK)) { backBytes = stream.readAllBytes(); }
        try (var stream = client.getResourceManager().open(TileMesh.GLYPHS)) { glyphBytes = stream.readAllBytes(); }
        if (originalAtlas == null) {
            originalAtlas = atlasBytes;
            originalBack = backBytes;
            originalGlyphs = glyphBytes;
        }
        require(Arrays.equals(originalAtlas, atlasBytes), "Back selection changed the face atlas");
        require(Arrays.equals(originalBack, backBytes), "Back bytes changed after reload");
        require(Arrays.equals(originalGlyphs, glyphBytes), "Glyph bytes changed after reload");
        try (var atlas = NativeImage.read(new ByteArrayInputStream(atlasBytes))) {
            require(atlas.getWidth() == 2048 && atlas.getHeight() == 4096, "High-resolution atlas did not reach the client");
            require(TileMesh.TILE_WIDTH == 256 && TileMesh.TILE_HEIGHT == 384 && TileMesh.ATLAS_WIDTH == 2048 && TileMesh.ATLAS_HEIGHT == 4096,
                "Renderer and artwork dimensions disagree");
        }
        try (var back = NativeImage.read(new ByteArrayInputStream(backBytes))) {
            require(back.getWidth() == 256 && back.getHeight() == 384, "High-resolution back did not reach the client");
            int corner = back.getPixelRGBA(0, 0);
            boolean different = false;
            for (int y = 0; y < back.getHeight(); y++) for (int x = 0; x < back.getWidth(); x++)
                different |= back.getPixelRGBA(x, y) != corner;
            require(!different, "Default back must be solid");
        }
        try (var glyphs = NativeImage.read(new ByteArrayInputStream(glyphBytes))) {
            require(glyphs.getWidth() == 2048 && glyphs.getHeight() == 4096, "Glyph atlas dimensions changed");
            require((glyphs.getPixelRGBA(0, 0) >>> 24) == 0, "Glass glyph atlas has an opaque background");
            boolean printed = false;
            for (int y = 0; y < TileMesh.TILE_HEIGHT; y++) for (int x = 0; x < TileMesh.TILE_WIDTH; x++)
                printed |= (glyphs.getPixelRGBA(x, y) >>> 24) > 0;
            require(printed, "First tile lost its printed glyph");
        }
        for (ResourceLocation texture : new ResourceLocation[]{TileMesh.ATLAS, TileMesh.BACK, TileMesh.GLYPHS}) {
            client.getTextureManager().getTexture(texture).bind();
            require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER) == GL11.GL_LINEAR,
                "World renderer disabled linear magnification for " + texture);
            require(GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER) == GL11.GL_LINEAR,
                "World renderer disabled linear minification for " + texture);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
