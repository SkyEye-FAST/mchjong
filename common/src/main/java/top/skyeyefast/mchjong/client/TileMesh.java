package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Three shallow cuboids give a tile a back, ivory bevel and recessed printed face. */
public final class TileMesh {
    public static final ResourceLocation ATLAS = MahjongContent.id("textures/tiles.png");
    public static final ResourceLocation BACK = MahjongContent.id("textures/tile/back.png");
    public static final int TILE_WIDTH = 256;
    public static final int TILE_HEIGHT = 384;
    public static final int ATLAS_SIZE = 2048;
    private TileMesh() {}

    public static int face(int tile) {
        if (tile < 0) throw new IllegalArgumentException("A concealed tile has no printed face");
        int kind = Tile.kind(tile);
        return Tile.red(tile) ? 34 + kind / 9 : kind;
    }

    public static void drawFace(PoseStack pose, VertexConsumer vertices, int tile, boolean concealed, int light) {
        box(pose, vertices, -0.052f, -0.080f, -0.012f, 0.052f, 0.080f, 0.030f, 0xffd6cdb7, light);
        box(pose, vertices, -0.049f, -0.077f, 0.027f, 0.049f, 0.077f, 0.035f, 0xfff4eedb, light);
        if (!concealed && tile >= 0) {
            int face = face(tile);
            float u0 = (face % 8 * TILE_WIDTH + 0.5f) / ATLAS_SIZE;
            float v0 = (face / 8 * TILE_HEIGHT + 0.5f) / ATLAS_SIZE;
            float u1 = (face % 8 * TILE_WIDTH + TILE_WIDTH - 0.5f) / ATLAS_SIZE;
            float v1 = (face / 8 * TILE_HEIGHT + TILE_HEIGHT - 0.5f) / ATLAS_SIZE;
            texturedFace(pose, vertices, u0, v0, u1, v1, 0.048f, 0.073f, 0.0353f, light, false);
        }
    }

    public static void drawBack(PoseStack pose, VertexConsumer vertices, boolean concealed, int light) {
        // The shell samples the back's corner, so custom back colors also color its edges.
        box(pose, vertices, -0.052f, -0.080f, -0.036f, 0.052f, 0.080f, -0.011f,
            0xffffffff, light, 0.5f / TILE_WIDTH, 0.5f / TILE_HEIGHT);
        texturedFace(pose, vertices, 0, 0, 1, 1, 0.050f, 0.078f, -0.0363f, light, true);
        if (concealed) texturedFace(pose, vertices, 0, 0, 1, 1, 0.048f, 0.073f, 0.0353f, light, false);
    }

    private static void texturedFace(PoseStack pose, VertexConsumer out, float u0, float v0, float u1, float v1,
            float w, float h, float z, int light, boolean reverse) {
        float normal = reverse ? -1 : 1;
        vertex(pose, out, -w, -h, z, reverse ? u1 : u0, v1, 0xffffffff, 0, 0, normal, light);
        vertex(pose, out, w, -h, z, reverse ? u0 : u1, v1, 0xffffffff, 0, 0, normal, light);
        vertex(pose, out, w, h, z, reverse ? u0 : u1, v0, 0xffffffff, 0, 0, normal, light);
        vertex(pose, out, -w, h, z, reverse ? u1 : u0, v0, 0xffffffff, 0, 0, normal, light);
    }

    public static void box(PoseStack pose, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1, int color, int light) {
        float swatch = (ATLAS_SIZE - 16f) / ATLAS_SIZE;
        box(pose, out, x0, y0, z0, x1, y1, z1, color, light, swatch, swatch);
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
