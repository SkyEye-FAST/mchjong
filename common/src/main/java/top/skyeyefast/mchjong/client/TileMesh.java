package top.skyeyefast.mchjong.client;

import top.skyeyefast.mchjong.platform.ResourceIds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.item.TileMaterial;
import net.minecraft.world.item.DyeColor;

/** Material body, optional dyed back and opaque white face share one physical tile envelope. */
public final class TileMesh {
    public static final float WIDTH = .104f;
    public static final float HEIGHT = .160f;
    public static final float DEPTH = .0726f;
    public static final ResourceLocation ATLAS = ResourceIds.of(MahjongContent.MOD_ID, "textures/tiles.png");
    public static final ResourceLocation BACK = ResourceIds.of(MahjongContent.MOD_ID, "textures/tile/back.png");
    public static final ResourceLocation GLYPHS = ResourceIds.of(MahjongContent.MOD_ID, "textures/tile_glyphs.png");
    public static ResourceLocation atlas(top.skyeyefast.mchjong.item.TileFacePreset preset) {
        return TileFacePresets.definition(preset).atlas();
    }
    public static ResourceLocation glyphs(top.skyeyefast.mchjong.item.TileFacePreset preset) {
        return TileFacePresets.definition(preset).glyphs();
    }
    public static final int TILE_WIDTH = 256;
    public static final int TILE_HEIGHT = 384;
    public static final int ATLAS_WIDTH = 2048;
    public static final int ATLAS_HEIGHT = 4096;
    static final float CORE_BACK = -.011f;
    static final float CORE_FRONT = .0255f;
    public static final float SWATCH_U = (ATLAS_WIDTH - 16f) / ATLAS_WIDTH;
    public static final float SWATCH_V = (ATLAS_HEIGHT - 16f) / ATLAS_HEIGHT;
    private static final float[] OUTLINE = outline(WIDTH / 2, HEIGHT / 2, .003f);
    private static final float[] CAP_OUTLINE = outline(WIDTH / 2 - .0015f, HEIGHT / 2 - .0015f, .002f);
    private static final int[][] CAP_QUADS = {{0,1,4,5}, {1,2,3,4}, {5,6,7,0}};
    private TileMesh() {}

    public static int face(int tile) {
        if (tile < 0) throw new IllegalArgumentException("A concealed tile has no printed face");
        int kind = Tile.kind(tile);
        return Tile.red(tile) ? 34 + kind / 9 : kind;
    }

    public static int bodyColor(TileMaterial material, DyeColor dye) {
        return material == TileMaterial.GLASS && dye != null
            ? material.color() & 0xff000000 | dyeColor(dye) : material.color();
    }

    public static int backColor(TileMaterial material, DyeColor dye) {
        return dye != null && material != TileMaterial.GLASS
            ? 0xff000000 | dyeColor(dye) : bodyColor(material, dye);
    }

    private static int dyeColor(DyeColor dye) {
        float[] rgb = dye.getTextureDiffuseColors();
        return (int) (rgb[0] * 255) << 16 | (int) (rgb[1] * 255) << 8 | (int) (rgb[2] * 255);
    }

    public static boolean usesMaterialBack(TileMaterial material, DyeColor dye) {
        return dye == null || material == TileMaterial.GLASS;
    }

    public static void drawBody(PoseStack pose, VertexConsumer vertices, int light, TileMaterial material, DyeColor dye) {
        band(pose, vertices, OUTLINE, CORE_BACK, OUTLINE, CORE_FRONT, bodyColor(material, dye), light, true, SWATCH_U, SWATCH_V);
    }

    public static void drawFace(PoseStack pose, VertexConsumer vertices, int tile, boolean concealed, int light) {
        if (!concealed && tile >= 0) drawArtwork(pose, vertices, face(tile), light);
        else drawWhiteFront(pose, vertices, light);
    }

    public static int artwork(top.skyeyefast.mchjong.item.TileData tile) {
        if (!tile.valid()) throw new IllegalArgumentException("Invalid tile design");
        return tile.flower() ? tile.face() + 3 : tile.red() ? 34 + tile.face() / 9 : tile.face();
    }

    /** Physical item designs include flowers without allocating riichi wall tile IDs to them. */
    public static void drawArtwork(PoseStack pose, VertexConsumer vertices, int face, int light) {
        if (face < 0 || face >= 45) throw new IllegalArgumentException("Invalid tile artwork");
        drawWhiteFront(pose, vertices, light);
        float u0 = (face % 8 * TILE_WIDTH + 0.5f) / ATLAS_WIDTH;
        float v0 = (face / 8 * TILE_HEIGHT + 0.5f) / ATLAS_HEIGHT;
        float u1 = (face % 8 * TILE_WIDTH + TILE_WIDTH - 0.5f) / ATLAS_WIDTH;
        float v1 = (face / 8 * TILE_HEIGHT + TILE_HEIGHT - 0.5f) / ATLAS_HEIGHT;
        texturedFace(pose, vertices, u0, v0, u1, v1, 0.048f, 0.073f, DEPTH / 2, light, false, 0xffffffff);
    }

    private static void drawWhiteFront(PoseStack pose, VertexConsumer vertices, int light) {
        // The opaque face plate has a rear surface visible through a glass body.
        cap(pose, vertices, OUTLINE, CORE_FRONT, true, false, false, 0xffffffff, light);
        band(pose, vertices, OUTLINE, CORE_FRONT, OUTLINE, .0343f, 0xffffffff, light, false, SWATCH_U, SWATCH_V);
        band(pose, vertices, OUTLINE, .0343f, CAP_OUTLINE, .0359f, 0xffffffff, light, false, SWATCH_U, SWATCH_V);
        cap(pose, vertices, CAP_OUTLINE, .0359f, false, false, false, 0xffffffff, light);
    }

    /** Blank fronts use the same material and untinted color as the back. */
    public static void drawBlankFront(PoseStack pose, VertexConsumer vertices, int light, TileMaterial material) {
        int color = bodyColor(material, null);
        band(pose, vertices, OUTLINE, CORE_FRONT, OUTLINE, .0343f, color, light, true, 0, 0);
        band(pose, vertices, OUTLINE, .0343f, CAP_OUTLINE, .0359f, color, light, true, 0, 0);
        cap(pose, vertices, CAP_OUTLINE, .0359f, false, true, false, color, light);
    }

    public static void drawBack(PoseStack pose, VertexConsumer vertices, boolean faceDown, int light,
                                TileMaterial material, DyeColor dye) {
        int color = backColor(material, dye);
        boolean materialBack = usesMaterialBack(material, dye);
        if (materialBack) {
            band(pose, vertices, CAP_OUTLINE, -DEPTH / 2, OUTLINE, -.0343f, color, light, true, 0, 0);
            band(pose, vertices, OUTLINE, -.0343f, OUTLINE, CORE_BACK, color, light, true, 0, 0);
            cap(pose, vertices, CAP_OUTLINE, -DEPTH / 2, true, true, faceDown, color, light);
        } else {
            // The dyed shell stays solid independently of the transparent decorative layer.
            float u = .5f / TILE_WIDTH, v = .5f / TILE_HEIGHT;
            band(pose, vertices, CAP_OUTLINE, -DEPTH / 2, OUTLINE, -.0343f, color, light, false, u, v);
            band(pose, vertices, OUTLINE, -.0343f, OUTLINE, CORE_BACK, color, light, false, u, v);
            cap(pose, vertices, CAP_OUTLINE, -DEPTH / 2, true, true, faceDown, color, light);
        }
    }

    public static void drawBackPattern(PoseStack pose, VertexConsumer vertices, boolean faceDown, int light) {
        cap(pose, vertices, CAP_OUTLINE, -DEPTH / 2 - .0001f, true, true, faceDown, 0xffffffff, light);
    }

    private static float[] outline(float w, float h, float r) {
        return new float[]{-w+r,-h, w-r,-h, w,-h+r, w,h-r, w-r,h, -w+r,h, -w,h-r, -w,-h+r};
    }

    /** Front/back bevel rims and the eight side edges; depth testing hides occluded edges. */
    public static void drawOutline(PoseStack pose, VertexConsumer vertices, int color) {
        pose.pushPose();
        // Lift the lines just clear of the shell without changing picking or tile contact.
        pose.scale(1.012f, 1.008f, 1.02f);
        for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            for (float z : new float[]{-.0343f, .0343f})
                edge(pose, vertices, OUTLINE[2*i], OUTLINE[2*i+1], z,
                    OUTLINE[2*j], OUTLINE[2*j+1], z, color);
            edge(pose, vertices, OUTLINE[2*i], OUTLINE[2*i+1], -.0343f,
                OUTLINE[2*i], OUTLINE[2*i+1], .0343f, color);
            for (float z : new float[]{-DEPTH / 2, .0359f})
                edge(pose, vertices, CAP_OUTLINE[2*i], CAP_OUTLINE[2*i+1], z,
                    CAP_OUTLINE[2*j], CAP_OUTLINE[2*j+1], z, color);
        }
        pose.popPose();
    }

    private static void edge(PoseStack pose, VertexConsumer out, float x0, float y0, float z0,
                             float x1, float y1, float z1, int color) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        var normal = new org.joml.Vector3f(dx / length, dy / length, dz / length).mul(pose.last().normal()).normalize();
        out.vertex(pose.last().pose(), x0, y0, z0).color(color).normal(normal.x, normal.y, normal.z).endVertex();
        out.vertex(pose.last().pose(), x1, y1, z1).color(color).normal(normal.x, normal.y, normal.z).endVertex();
    }

    private static void band(PoseStack pose, VertexConsumer out, float[] a, float z0, float[] b, float z1,
                             int color, int light, boolean texture, float sampleU, float sampleV) {
        float perimeter = 0;
        for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            perimeter += (float) Math.hypot(a[2*j] - a[2*i], a[2*j+1] - a[2*i+1]);
        }
        float distance = 0;
        for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            float dx = a[2*j] - a[2*i], dy = a[2*j+1] - a[2*i+1];
            float nx = dy * (z1 - z0), ny = -dx * (z1 - z0);
            float nz = dx * (b[2*j+1] - a[2*i+1]) - dy * (b[2*j] - a[2*i]);
            float length = (float) Math.sqrt(nx*nx + ny*ny + nz*nz);
            nx /= length; ny /= length; nz /= length;
            float u0 = texture ? distance / perimeter : sampleU;
            distance += (float) Math.hypot(dx, dy);
            float u1 = texture ? distance / perimeter : sampleU;
            float v0 = texture ? 0 : sampleV, v1 = texture ? 1 : sampleV;
            vertex(pose, out, a[2*i], a[2*i+1], z0, u0, v0, color, nx, ny, nz, light);
            vertex(pose, out, a[2*j], a[2*j+1], z0, u1, v0, color, nx, ny, nz, light);
            vertex(pose, out, b[2*j], b[2*j+1], z1, u1, v1, color, nx, ny, nz, light);
            vertex(pose, out, b[2*i], b[2*i+1], z1, u0, v1, color, nx, ny, nz, light);
        }
    }

    private static void cap(PoseStack pose, VertexConsumer out, float[] ring, float z, boolean back,
                            boolean texture, boolean rotateTexture, int color, int light) {
        float w = WIDTH / 2 - .0015f, h = HEIGHT / 2 - .0015f;
        for (int[] corners : CAP_QUADS) for (int i = 0; i < 4; i++) {
            int corner = corners[back ? 3 - i : i];
            float x = ring[2*corner], y = ring[2*corner+1];
            float u = texture ? (w + (rotateTexture ? x : -x)) / (2*w) : SWATCH_U;
            float v = texture ? (h + (rotateTexture ? y : -y)) / (2*h) : SWATCH_V;
            vertex(pose, out, x, y, z, u, v, color, 0, 0, back ? -1 : 1, light);
        }
    }

    private static void texturedFace(PoseStack pose, VertexConsumer out, float u0, float v0, float u1, float v1,
            float w, float h, float z, int light, boolean reverse, int color) {
        float normal = reverse ? -1 : 1;
        float left = reverse ? w : -w, right = -left;
        vertex(pose, out, left, -h, z, u0, v1, color, 0, 0, normal, light);
        vertex(pose, out, right, -h, z, u1, v1, color, 0, 0, normal, light);
        vertex(pose, out, right, h, z, u1, v0, color, 0, 0, normal, light);
        vertex(pose, out, left, h, z, u0, v0, color, 0, 0, normal, light);
    }

    public static void box(PoseStack pose, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
        box(pose, out, x0, y0, z0, x1, y1, z1, color, light, SWATCH_U, SWATCH_V);
    }

    private static void box(PoseStack pose, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1,
            int color, int light, float u, float v) {
        quad(pose, out, color, light, u, v, 0, 0, 1, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1);
        quad(pose, out, color, light, u, v, 0, 0,-1, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0);
        quad(pose, out, color, light, u, v, 0, 1, 0, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0);
        quad(pose, out, color, light, u, v, 0,-1, 0, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
        quad(pose, out, color, light, u, v, 1, 0, 0, x1,y0,z1, x1,y0,z0, x1,y1,z0, x1,y1,z1);
        quad(pose, out, color, light, u, v,-1, 0, 0, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
    }

    private static void quad(PoseStack pose, VertexConsumer out, int color, int light, float u, float v,
            float nx, float ny, float nz, float... points) {
        for (int i = 0; i < 12; i += 3)
            vertex(pose, out, points[i], points[i+1], points[i+2], u, v, color, nx, ny, nz, light);
    }

    private static void vertex(PoseStack pose, VertexConsumer out, float x, float y, float z, float u, float v,
            int color, float nx, float ny, float nz, int light) {
        out.vertex(pose.last().pose(), x, y, z).color(color).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light).normal(pose.last().normal(), nx, ny, nz).endVertex();
    }
}
