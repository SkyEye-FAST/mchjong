package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Lit, outward-facing tile materials which retain linear filtering at draw time. */
public final class TileRenderTypes extends RenderType {
    public static final RenderType FACES = material("mchjong_tile_faces", TileMesh.GLYPHS);
    public static final RenderType BACKS = material("mchjong_tile_backs", TileMesh.BACK);
    public static final RenderType GLASS = create("mchjong_glass_tiles", DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new TextureStateShard(TileMesh.GLYPHS, true, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL)
            .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false));

    private TileRenderTypes() {
        super("mchjong_tiles", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            1536, false, false, () -> {}, () -> {});
    }

    private static RenderType material(String name, ResourceLocation texture) {
        // Vanilla entityCutoutNoCull (including entitySmoothCutout) forces nearest sampling.
        return create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536,
            false, false, CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                .setTextureState(new TextureStateShard(texture, true, false))
                .setCullState(CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(false));
    }
}
