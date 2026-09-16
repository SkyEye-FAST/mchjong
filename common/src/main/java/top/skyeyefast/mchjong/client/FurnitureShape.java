package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/** Small textured furniture primitives, independent of texture selection and item components. */
public final class FurnitureShape {
    private FurnitureShape() {}

    public static void box(PoseStack pose, VertexConsumer out, float x0, float y0, float z0,
            float x1, float y1, float z1, int color, int light) {
        tapered(pose, out, x0, y0, z0, x1, y1, z1, 0, color, light);
    }

    /** Inset the bottom footprint to make a solid tapered leg, not a stack of floating cubes. */
    public static void tapered(PoseStack pose, VertexConsumer out, float x0, float y0, float z0,
            float x1, float y1, float z1, float inset, int color, int light) {
        float a = x0 + inset, b = x1 - inset, c = z0 + inset, d = z1 - inset;
        quad(pose, out, color, light, a,y0,d, b,y0,d, x1,y1,z1, x0,y1,z1);
        quad(pose, out, color, light, b,y0,c, a,y0,c, x0,y1,z0, x1,y1,z0);
        quad(pose, out, color, light, x0,y1,z1, x1,y1,z1, x1,y1,z0, x0,y1,z0);
        quad(pose, out, color, light, a,y0,c, b,y0,c, b,y0,d, a,y0,d);
        quad(pose, out, color, light, b,y0,d, b,y0,c, x1,y1,z0, x1,y1,z1);
        quad(pose, out, color, light, a,y0,c, a,y0,d, x0,y1,z1, x0,y1,z0);
    }

    /** Clipped corners and a sloped top edge catch the light without rounded high-poly meshes. */
    public static void bevel(PoseStack pose, VertexConsumer out, float x0, float y0, float z0,
            float x1, float y1, float z1, float radius, int color, int light) {
        float r = Math.min(radius, Math.min((y1 - y0) / 2, Math.min(x1 - x0, z1 - z0) / 4));
        float[] base = ring(x0, z0, x1, z1, r);
        float[] top = ring(x0 + r, z0 + r, x1 - r, z1 - r, r / 2);
        for (int i = 0; i < 8; i++) {
            int j = (i + 1) % 8;
            quad(pose, out, color, light,
                base[2*i],y0,base[2*i+1], base[2*i],y1-r,base[2*i+1],
                base[2*j],y1-r,base[2*j+1], base[2*j],y0,base[2*j+1]);
            quad(pose, out, color, light,
                base[2*i],y1-r,base[2*i+1], top[2*i],y1,top[2*i+1],
                top[2*j],y1,top[2*j+1], base[2*j],y1-r,base[2*j+1]);
        }
        cap(pose, out, base, y0, false, color, light);
        cap(pose, out, top, y1, true, color, light);
    }

    private static float[] ring(float x0, float z0, float x1, float z1, float r) {
        return new float[]{x0+r,z0, x1-r,z0, x1,z0+r, x1,z1-r,
            x1-r,z1, x0+r,z1, x0,z1-r, x0,z0+r};
    }

    private static void cap(PoseStack pose, VertexConsumer out, float[] ring, float y, boolean up, int color, int light) {
        for (int[] corners : new int[][]{{0,1,4,5}, {1,2,3,4}, {5,6,7,0}}) {
            float[] points = new float[12];
            for (int i = 0; i < 4; i++) {
                int corner = corners[up ? 3 - i : i];
                points[3*i] = ring[2*corner];
                points[3*i+1] = y;
                points[3*i+2] = ring[2*corner+1];
            }
            quad(pose, out, color, light, points);
        }
    }

    private static void quad(PoseStack pose, VertexConsumer out, int color, int light, float... p) {
        var across = new Vector3f(p[3]-p[0], p[4]-p[1], p[5]-p[2]);
        var along = new Vector3f(p[9]-p[0], p[10]-p[1], p[11]-p[2]);
        var normal = new Vector3f(across).cross(along).normalize();
        float width = across.length(), height = along.length();
        // One original 64px repeat per block; grain follows the long axis of each face.
        for (int i = 0; i < 4; i++) {
            float u = i == 1 || i == 2 ? width : 0, v = i < 2 ? height : 0;
            out.addVertex(pose.last(), p[3*i], p[3*i+1], p[3*i+2]).setColor(color)
                .setUv(width > height ? v : u, width > height ? u : v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose.last(), normal.x, normal.y, normal.z);
        }
    }
}
