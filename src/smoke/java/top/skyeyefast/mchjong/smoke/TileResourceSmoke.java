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
    private TileResourceSmoke() {}

    static void verify(Minecraft client) throws IOException {
        require(client.getResourcePackRepository().getAvailableIds().stream().noneMatch(id -> id.endsWith("patterned_backs")),
            "Retired built-in pack is still registered");
        byte[] atlasBytes;
        byte[] backBytes;
        try (var stream = client.getResourceManager().open(TileMesh.ATLAS)) { atlasBytes = stream.readAllBytes(); }
        try (var stream = client.getResourceManager().open(TileMesh.BACK)) { backBytes = stream.readAllBytes(); }
        if (originalAtlas == null) {
            originalAtlas = atlasBytes;
            originalBack = backBytes;
        }
        require(Arrays.equals(originalAtlas, atlasBytes), "Back selection changed the face atlas");
        require(Arrays.equals(originalBack, backBytes), "Back bytes changed after reload");
        try (var atlas = NativeImage.read(new ByteArrayInputStream(atlasBytes))) {
            require(atlas.getWidth() == 2048 && atlas.getHeight() == 2048, "High-resolution atlas did not reach the client");
            require(TileMesh.TILE_WIDTH == 256 && TileMesh.TILE_HEIGHT == 384 && TileMesh.ATLAS_SIZE == 2048,
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
        for (ResourceLocation texture : new ResourceLocation[]{TileMesh.ATLAS, TileMesh.BACK}) {
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
