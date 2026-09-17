package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.item.TileMaterial;
import net.minecraft.world.item.DyeColor;

/** A dyed back, material body and opaque white face share one physical tile envelope. */
public final class TileMesh {
    public static final float WIDTH = .104f;
    public static final float HEIGHT = .160f;
    public static final float DEPTH = .0726f;
    public static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(MahjongContent.MOD_ID, "textures/tiles.png");
    public static final ResourceLocation BACK = ResourceLocation.fromNamespaceAndPath(MahjongContent.MOD_ID, "textures/tile/back.png");
    public static final ResourceLocation GLYPHS = ResourceLocation.fromNamespaceAndPath(MahjongContent.MOD_ID, "textures/tile_glyphs.png");
    public static final int TILE_WIDTH = 256;
    public static final int TILE_HEIGHT = 384;
    public static final int ATLAS_WIDTH = 2048;
    public static final int ATLAS_HEIGHT = 4096;
    private static final float CORE_BACK = -.011f;
    private static final float CORE_FRONT = .0255f;
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

    public static void drawBody(PoseStack pose, VertexConsumer vertices, int light, TileMaterial material) {
        band(pose, vertices, OUTLINE, CORE_BACK, OUTLINE, CORE_FRONT, material.color(), light, true, SWATCH_U, SWATCH_V);
    }

    public static void drawFace(PoseStack pose, VertexConsumer vertices, int tile, boolean concealed, int light) {
        drawArtwork(pose, vertices, concealed || tile < 0 ? -1 : face(tile), light);
    }

    public static int artwork(top.skyeyefast.mchjong.item.TileData tile) {
        if (!tile.valid()) throw new IllegalArgumentException("Invalid tile design");
        return tile.flower() ? tile.face() + 3 : tile.red() ? 34 + tile.face() / 9 : tile.face();
    }

    /** Physical item designs include flowers without allocating riichi wall tile IDs to them. */
    public static void drawArtwork(PoseStack pose, VertexConsumer vertices, int face, int light) {
        if (face < -1 || face >= 45) throw new IllegalArgumentException("Invalid tile artwork");
        // The three shells meet edge-to-edge. There are no overlapping side faces or internal caps.
        band(pose, vertices, OUTLINE, CORE_FRONT, OUTLINE, .0343f, 0xffffffff, light, false, SWATCH_U, SWATCH_V);
        band(pose, vertices, OUTLINE, .0343f, CAP_OUTLINE, .0359f, 0xffffffff, light, false, SWATCH_U, SWATCH_V);
        cap(pose, vertices, CAP_OUTLINE, .0359f, false, false, 0xffffffff, light);
        if (face >= 0) {
            float u0 = (face % 8 * TILE_WIDTH + 0.5f) / ATLAS_WIDTH;
            float v0 = (face / 8 * TILE_HEIGHT + 0.5f) / ATLAS_HEIGHT;
            float u1 = (face % 8 * TILE_WIDTH + TILE_WIDTH - 0.5f) / ATLAS_WIDTH;
            float v1 = (face / 8 * TILE_HEIGHT + TILE_HEIGHT - 0.5f) / ATLAS_HEIGHT;
            texturedFace(pose, vertices, u0, v0, u1, v1, 0.048f, 0.073f, DEPTH / 2, light, false, 0xffffffff);
        }
    }

    public static void drawBack(PoseStack pose, VertexConsumer vertices, boolean concealed, int light) {
        drawBack(pose, vertices, concealed, light, DyeColor.BLUE);
    }

    public static void drawBack(PoseStack pose, VertexConsumer vertices, boolean concealed, int light, DyeColor dye) {
        int color = 0xff000000 | dye.getTextureDiffuseColor();
        // Back artwork wraps the outer cap; edge colors sample its corner rather than a glyph atlas.
        float u = .5f / TILE_WIDTH, v = .5f / TILE_HEIGHT;
        band(pose, vertices, CAP_OUTLINE, -DEPTH / 2, OUTLINE, -.0343f, color, light, false, u, v);
        band(pose, vertices, OUTLINE, -.0343f, OUTLINE, CORE_BACK, color, light, false, u, v);
        cap(pose, vertices, CAP_OUTLINE, -DEPTH / 2, true, true, color, light);
        if (concealed) texturedFace(pose, vertices, 0, 0, 1, 1, 0.048f, 0.073f, DEPTH / 2, light, false, color);
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
        out.addVertex(pose.last(), x0, y0, z0).setColor(color).setNormal(pose.last(), dx / length, dy / length, dz / length);
        out.addVertex(pose.last(), x1, y1, z1).setColor(color).setNormal(pose.last(), dx / length, dy / length, dz / length);
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
                            boolean texture, int color, int light) {
        float w = WIDTH / 2 - .0015f, h = HEIGHT / 2 - .0015f;
        for (int[] corners : CAP_QUADS) for (int i = 0; i < 4; i++) {
            int corner = corners[back ? 3 - i : i];
            float x = ring[2*corner], y = ring[2*corner+1];
            float u = texture ? (w - x) / (2*w) : SWATCH_U;
            float v = texture ? (h - y) / (2*h) : SWATCH_V;
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
        out.addVertex(pose.last(), x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(pose.last(), nx, ny, nz);
    }
}
