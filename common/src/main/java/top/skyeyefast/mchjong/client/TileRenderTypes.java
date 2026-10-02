package top.skyeyefast.mchjong.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Lit tile surfaces built entirely from Minecraft's supported render types. */
public final class TileRenderTypes {
    public static final RenderType FACES = RenderTypes.entityCutout(TileMesh.GLYPHS);
    public static final Identifier PLAIN = Identifier.fromNamespaceAndPath("mchjong", "textures/tile/plain.png");
    public static final RenderType BACKS = RenderTypes.entityCutout(PLAIN);
    public static final RenderType BACK_PATTERN = backPattern(TileBackPresets.DEFAULT);
    public static RenderType backPattern(Identifier preset) { return RenderTypes.entityTranslucent(TileBackPresets.texture(preset)); }
    public static final RenderType STICKS = RenderTypes.entityCutout(FurnitureMesh.STICK_TEXTURE);

    private TileRenderTypes() {}

    public static RenderType faces(top.skyeyefast.mchjong.item.TileFacePreset preset) {
        return RenderTypes.entityCutout(TileMesh.glyphs(preset));
    }

    public static Identifier bodyTexture(top.skyeyefast.mchjong.item.TileMaterial material) {
        return Identifier.fromNamespaceAndPath("mchjong", "textures/tile_material/" + material.texture() + ".png");
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
    private static Identifier clothId(net.minecraft.world.item.DyeColor dye, boolean folded) {
        return Identifier.fromNamespaceAndPath("mchjong", "cloth/" + dye.getName() + (folded ? "/folded" : "/table"));
    }
    public static RenderType cloth(net.minecraft.world.item.DyeColor dye, boolean folded) {
        return RenderTypes.entityCutout(clothId(dye, folded));
    }
    public static void reload() {
        var textures = net.minecraft.client.Minecraft.getInstance().getTextureManager();
        for (var dye : net.minecraft.world.item.DyeColor.values()) for (boolean folded : new boolean[]{false, true}) {
            var id = clothId(dye, folded);
            textures.release(id);
            textures.register(id, new ClothTexture(dye, folded));
        }
    }
}
