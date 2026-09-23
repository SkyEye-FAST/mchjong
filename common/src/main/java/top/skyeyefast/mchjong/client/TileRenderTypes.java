package top.skyeyefast.mchjong.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Lit tile surfaces built entirely from Minecraft's supported render types. */
public final class TileRenderTypes {
    public static final RenderType FACES = RenderTypes.entityCutout(TileMesh.GLYPHS);
    public static final Identifier PLAIN = Identifier.fromNamespaceAndPath("mchjong", "textures/tile/plain.png");
    public static final RenderType BACKS = RenderTypes.entityCutout(PLAIN);
    public static final RenderType BACK_PATTERN = RenderTypes.entityTranslucent(TileMesh.BACK);
    public static final RenderType STICKS = RenderTypes.entityCutout(FurnitureMesh.STICK_TEXTURE);

    private TileRenderTypes() {}

    public static RenderType faces(top.skyeyefast.mchjong.item.TileFacePreset preset) {
        return RenderTypes.entityCutout(TileMesh.glyphs(preset));
    }

    public static Identifier bodyTexture(top.skyeyefast.mchjong.item.TileMaterial material) {
        return Identifier.fromNamespaceAndPath("mchjong", "textures/tile_material/" + material.getSerializedName() + ".png");
    }

    public static Identifier backTexture(top.skyeyefast.mchjong.item.TileMaterial material,
                                               net.minecraft.world.item.DyeColor dye) {
        return TileMesh.usesMaterialBack(material, dye) ? bodyTexture(material) : PLAIN;
    }

    public static RenderType body(top.skyeyefast.mchjong.item.TileMaterial material) {
        Identifier texture = bodyTexture(material);
        return material == top.skyeyefast.mchjong.item.TileMaterial.GLASS
            ? RenderTypes.entityTranslucent(texture)
            : RenderTypes.entityCutout(texture);
    }

    public static RenderType back(top.skyeyefast.mchjong.item.TileMaterial material,
                                  net.minecraft.world.item.DyeColor dye) {
        return TileMesh.usesMaterialBack(material, dye) ? body(material) : BACKS;
    }

    public static RenderType gui(Identifier texture) {
        return RenderTypes.entityTranslucent(texture);
    }
    public static void reload() {}
}
