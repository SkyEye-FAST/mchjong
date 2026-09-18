package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Lit, outward-facing tile materials which retain linear filtering at draw time. */
public final class TileRenderTypes extends RenderType {
    public static final RenderType FACES = material("mchjong_tile_faces", TileMesh.GLYPHS);
    public static final RenderType BACKS = material("mchjong_tile_backs", TileMesh.BACK);
    public static final RenderType STICKS = material("mchjong_point_sticks", FurnitureMesh.STICK_TEXTURE);
    private static final java.util.Map<top.skyeyefast.mchjong.item.TileMaterial, RenderType> BODIES =
        new java.util.EnumMap<>(top.skyeyefast.mchjong.item.TileMaterial.class);
    static {
        for (var tile : top.skyeyefast.mchjong.item.TileMaterial.values())
            if (tile != top.skyeyefast.mchjong.item.TileMaterial.GLASS)
                BODIES.put(tile, material("mchjong_tile_" + tile.getSerializedName(), bodyTexture(tile)));
    }
    public static final RenderType GLASS = create("mchjong_glass_tiles", DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new TextureStateShard(bodyTexture(top.skyeyefast.mchjong.item.TileMaterial.GLASS), true, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL)
            .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false));

    private TileRenderTypes() {
        super("mchjong_tiles", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            1536, false, false, () -> {}, () -> {});
    }

    private static ResourceLocation bodyTexture(top.skyeyefast.mchjong.item.TileMaterial material) {
        return ResourceLocation.fromNamespaceAndPath("mchjong", "textures/tile_material/" + material.getSerializedName() + ".png");
    }

    public static RenderType body(top.skyeyefast.mchjong.item.TileMaterial material) {
        return material == top.skyeyefast.mchjong.item.TileMaterial.GLASS ? GLASS : BODIES.get(material);
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
