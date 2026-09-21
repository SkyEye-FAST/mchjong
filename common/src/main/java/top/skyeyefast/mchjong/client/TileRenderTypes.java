package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Lit, outward-facing tile materials which retain linear filtering at draw time. */
public final class TileRenderTypes extends RenderType {
    public static final RenderType FACES = material("mchjong_tile_faces", TileMesh.GLYPHS);
    private static final RenderType KANTO_FACES = material("mchjong_kanto_faces", TileMesh.glyphs(top.skyeyefast.mchjong.item.TileFacePreset.KANTO));
    public static RenderType faces(top.skyeyefast.mchjong.item.TileFacePreset preset) {
        return preset == top.skyeyefast.mchjong.item.TileFacePreset.KANTO ? KANTO_FACES : FACES;
    }
    public static final RenderType BACKS = material("mchjong_tile_backs", TileMesh.BACK);
    public static final RenderType STICKS = material("mchjong_point_sticks", FurnitureMesh.STICK_TEXTURE);
    private static final java.util.Map<ResourceLocation, RenderType> GUI = java.util.Map.of(
        TileMesh.ATLAS, guiMaterial("mchjong_gui_faces", TileMesh.ATLAS),
        TileMesh.atlas(top.skyeyefast.mchjong.item.TileFacePreset.KANTO),
            guiMaterial("mchjong_gui_kanto", TileMesh.atlas(top.skyeyefast.mchjong.item.TileFacePreset.KANTO)),
        TileMesh.BACK, guiMaterial("mchjong_gui_backs", TileMesh.BACK));
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

    public static RenderType gui(ResourceLocation texture) { return GUI.get(texture); }

    private static RenderType guiMaterial(String name, ResourceLocation texture) {
        return create(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 1536,
            false, false, CompositeState.builder()
                .setShaderState(RENDERTYPE_TEXT_SEE_THROUGH_SHADER)
                .setTextureState(new TextureStateShard(texture, true, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                .setLightmapState(LIGHTMAP).createCompositeState(false));
    }

    private static RenderType material(String name, ResourceLocation texture) {
        // Vanilla entityCutoutNoCull (including entitySmoothCutout) forces nearest sampling.
        return create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536,
            false, false, CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                .setTextureState(texture.equals(TileMesh.GLYPHS)
                    || texture.equals(TileMesh.glyphs(top.skyeyefast.mchjong.item.TileFacePreset.KANTO))
                    ? new FaceTextureState(texture) : new TextureStateShard(texture, true, false))
                .setCullState(CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(false));
    }

    private static final class FaceTextureState extends TextureStateShard {
        private final ResourceLocation location;
        FaceTextureState(ResourceLocation location) { super(location, true, true); this.location = location; }
        @Override public void setupRenderState() {
            var textures = net.minecraft.client.Minecraft.getInstance().getTextureManager();
            if (!(textures.getTexture(location) instanceof TileFaceTexture)) textures.register(location, new TileFaceTexture(location));
            super.setupRenderState();
        }
    }
}
