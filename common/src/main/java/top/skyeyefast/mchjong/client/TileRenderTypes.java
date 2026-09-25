package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Lit tile surfaces: smooth printed faces and crisp low-resolution body materials. */
public final class TileRenderTypes extends RenderType {
    public static final RenderType FACES = faceMaterial(TileMesh.GLYPHS);
    private static final java.util.Map<ResourceLocation, RenderType> FACE_TYPES = new java.util.HashMap<>();
    public static RenderType faces(top.skyeyefast.mchjong.item.TileFacePreset preset) {
        var texture = TileMesh.glyphs(preset);
        if (texture.equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))
            return RenderType.entityCutout(texture);
        return FACE_TYPES.computeIfAbsent(texture, location -> TileFacePresets.dynamicTexture(location)
            ? material("mchjong_tile_faces", new TextureStateShard(location, true, false)) : faceMaterial(location));
    }
    public static final ResourceLocation PLAIN = ResourceLocation.fromNamespaceAndPath("mchjong", "textures/tile/plain.png");
    public static final RenderType BACKS = material("mchjong_tile_backs", PLAIN);
    private static final java.util.Map<ResourceLocation, RenderType> BACK_PATTERNS = new java.util.HashMap<>();
    public static final RenderType BACK_PATTERN = backPattern(TileBackPresets.DEFAULT);
    public static RenderType backPattern(ResourceLocation preset) {
        ResourceLocation texture = TileBackPresets.texture(preset);
        return BACK_PATTERNS.computeIfAbsent(texture, key -> create("mchjong_back_pattern", DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new TextureStateShard(key, true, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL)
                .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false)));
    }
    public static final RenderType STICKS = material("mchjong_point_sticks", FurnitureMesh.STICK_TEXTURE);
    private static final java.util.Map<ResourceLocation, RenderType> GUI = guiTypes();
    private static final java.util.Map<top.skyeyefast.mchjong.item.TileMaterial, RenderType> BODIES =
        new java.util.EnumMap<>(top.skyeyefast.mchjong.item.TileMaterial.class);
    static {
        for (var tile : top.skyeyefast.mchjong.item.TileMaterial.values())
            if (tile != top.skyeyefast.mchjong.item.TileMaterial.GLASS)
                BODIES.put(tile, material("mchjong_tile_" + tile.getSerializedName(), bodyTexture(tile), false));
    }
    public static final RenderType GLASS = create("mchjong_glass_tiles", DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTextureState(new TextureStateShard(bodyTexture(top.skyeyefast.mchjong.item.TileMaterial.GLASS), false, false))
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL)
            .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false));

    private TileRenderTypes() {
        super("mchjong_tiles", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            1536, false, false, () -> {}, () -> {});
    }

    public static ResourceLocation bodyTexture(top.skyeyefast.mchjong.item.TileMaterial material) {
        return ResourceLocation.fromNamespaceAndPath("mchjong", "textures/tile_material/" + material.texture() + ".png");
    }

    public static ResourceLocation backTexture(top.skyeyefast.mchjong.item.TileMaterial material,
                                               net.minecraft.world.item.DyeColor dye) {
        return TileMesh.usesMaterialBack(material, dye) ? bodyTexture(material) : PLAIN;
    }

    public static RenderType body(top.skyeyefast.mchjong.item.TileMaterial material) {
        return material == top.skyeyefast.mchjong.item.TileMaterial.GLASS ? GLASS : BODIES.get(material);
    }

    public static RenderType back(top.skyeyefast.mchjong.item.TileMaterial material,
                                  net.minecraft.world.item.DyeColor dye) {
        return TileMesh.usesMaterialBack(material, dye) ? body(material) : BACKS;
    }

    public static RenderType gui(ResourceLocation texture) {
        return GUI.computeIfAbsent(texture, key -> guiMaterial("mchjong_gui", key, true));
    }
    public static void reload() { FACE_TYPES.clear(); BACK_PATTERNS.clear(); GUI.clear(); GUI.putAll(guiTypes()); }

    private static java.util.Map<ResourceLocation, RenderType> guiTypes() {
        var result = new java.util.HashMap<ResourceLocation, RenderType>();
        result.put(TileMesh.ATLAS, guiMaterial("mchjong_gui_faces", TileMesh.ATLAS, true));
        result.put(TileMesh.BACK, guiMaterial("mchjong_gui_backs", TileMesh.BACK, true));
        for (var material : top.skyeyefast.mchjong.item.TileMaterial.values()) {
            var texture = bodyTexture(material);
            result.put(texture, guiMaterial("mchjong_gui_" + material.getSerializedName(), texture, false));
        }
        return result;
    }

    private static RenderType guiMaterial(String name, ResourceLocation texture, boolean blur) {
        return create(name, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP, VertexFormat.Mode.QUADS, 1536,
            false, false, CompositeState.builder()
                .setShaderState(RENDERTYPE_TEXT_SEE_THROUGH_SHADER)
                .setTextureState(new TextureStateShard(texture, blur, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                .setLightmapState(LIGHTMAP).createCompositeState(false));
    }

    private static RenderType material(String name, ResourceLocation texture) {
        return material(name, texture, true);
    }

    private static RenderType material(String name, ResourceLocation texture, boolean blur) {
        return material(name, new TextureStateShard(texture, blur, false));
    }

    private static RenderType faceMaterial(ResourceLocation texture) {
        return material("mchjong_tile_faces", new FaceTextureState(texture));
    }

    private static RenderType material(String name, TextureStateShard texture) {
        // Vanilla entityCutoutNoCull (including entitySmoothCutout) forces nearest sampling.
        return create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536,
            false, false, CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                .setTextureState(texture)
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
