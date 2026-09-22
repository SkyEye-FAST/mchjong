package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FurnitureShapeTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final class Vertex {
        final Vector3f position;
        Vector3f normal;
        float u, v;
        int color;
        Vertex(float x, float y, float z) { position = new Vector3f(x, y, z); }
    }

    private static final class Mesh implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        @Override public VertexConsumer vertex(double x, double y, double z) { vertices.add(new Vertex((float) x, (float) y, (float) z)); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { vertices.getLast().color = a << 24 | r << 16 | g << 8 | b; return this; }
        @Override public VertexConsumer uv(float u, float v) { vertices.getLast().u = u; vertices.getLast().v = v; return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { vertices.getLast().normal = new Vector3f(x, y, z); return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) { throw new UnsupportedOperationException(); }
        @Override public void unsetDefaultColor() {}
    }

    @Test void textureDensityAndComponentTintSurviveLongRails() {
        var mesh = new Mesh();
        FurnitureShape.box(new PoseStack(), mesh, 0, 0, 0, 3, .25f, 2, 0xffb8c4a2, 0);
        assertEquals(24, mesh.vertices.size());
        assertEquals(3, mesh.vertices.get(1).v);
        assertEquals(0, mesh.vertices.get(0).u);
        assertEquals(3, mesh.vertices.get(9).u);
        assertEquals(2, mesh.vertices.get(8).v);
        assertTrue(mesh.vertices.stream().allMatch(vertex -> vertex.color == 0xffb8c4a2));
    }

    @Test void bevelsAndTaperedLegsHaveFiniteOutwardNormalsInEveryOrientation() {
        for (int yaw : new int[]{0, 90, 180, 270}) for (boolean bevel : new boolean[]{false, true}) {
            var pose = new PoseStack();
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            var mesh = new Mesh();
            if (bevel) FurnitureShape.bevel(pose, mesh, -.4f, -.2f, -.3f, .4f, .2f, .3f, .04f, -1, 0);
            else FurnitureShape.tapered(pose, mesh, -.4f, -.2f, -.3f, .4f, .2f, .3f, .06f, -1, 0);
            assertTrue(mesh.vertices.size() <= 88, "Keep the furniture primitives small");
            for (int i = 0; i < mesh.vertices.size(); i += 4) {
                var a = mesh.vertices.get(i);
                var b = mesh.vertices.get(i + 1);
                var c = mesh.vertices.get(i + 2);
                var normal = new Vector3f(b.position).sub(a.position).cross(new Vector3f(c.position).sub(a.position)).normalize();
                assertTrue(normal.isFinite());
                assertTrue(normal.dot(a.normal) > .999f, "Quad winding differs from its lighting normal");
                assertTrue(normal.dot(a.position) > 0, "Face points into the solid");
                assertTrue(Float.isFinite(a.u) && Float.isFinite(a.v));
            }
        }
    }

    @Test void continuousRimIsClosedAndTopTextureCoordinatesAgreeAcrossEveryJoint() {
        var mesh = new Mesh();
        FurnitureShape.frame(new PoseStack(), mesh, 1.3125f, 1.4375f, .859375f, 1, .015625f, -1, 0);
        assertEquals(192, mesh.vertices.size());
        var edges = new java.util.HashMap<java.util.Set<Vector3f>, Integer>();
        for (int i = 0; i < mesh.vertices.size(); i += 4) {
            var a = mesh.vertices.get(i);
            var normal = new Vector3f(mesh.vertices.get(i+1).position).sub(a.position)
                .cross(new Vector3f(mesh.vertices.get(i+2).position).sub(a.position)).normalize();
            assertTrue(normal.isFinite() && normal.dot(a.normal) > .999);
            for (int corner = 0; corner < 4; corner++) {
                var vertex = mesh.vertices.get(i + corner);
                edges.merge(java.util.Set.of(vertex.position, mesh.vertices.get(i + (corner + 1) % 4).position), 1, Integer::sum);
                if (vertex.normal.y > .999) {
                    assertEquals(vertex.position.x, vertex.u);
                    assertEquals(vertex.position.z, vertex.v);
                }
            }
        }
        assertTrue(edges.values().stream().allMatch(count -> count == 2), "No open seams or overlapping corner caps");
    }

    @Test void furnitureMeshesStayFiniteAndInsideTheTableFootprint() {
        // Wood and dye select textures/tints, not geometry; art tests cover their assets.
        var wood = top.skyeyefast.mchjong.item.FurnitureWood.OAK;
        var dye = net.minecraft.world.item.DyeColor.GREEN;
        var mesh = new Mesh();
        net.minecraft.client.renderer.MultiBufferSource buffers = ignored -> mesh;
        FurnitureMesh.stool(new PoseStack(), buffers, 0, wood, dye);
        assertEquals(top.skyeyefast.mchjong.world.TableGeometry.STOOL_HEIGHT,
            mesh.vertices.stream().mapToDouble(vertex -> vertex.position.y).max().orElseThrow(), 1e-6);
        FurnitureMesh.table(new PoseStack(), buffers, 0, wood, dye, false);
        FurnitureMesh.table(new PoseStack(), buffers, 0, wood, null, true);
        FurnitureMesh.box(new PoseStack(), buffers, 0);
        FurnitureMesh.foldedCloth(new PoseStack(), buffers, 0, dye);
        assertTrue(mesh.vertices.size() < 16000, "Avoid high-poly furniture");
        for (var vertex : mesh.vertices) {
            assertTrue(vertex.position.isFinite() && vertex.normal.isFinite());
            double half = top.skyeyefast.mchjong.world.TableGeometry.OUTER_HALF_WIDTH + 1e-6;
            assertTrue(Math.abs(vertex.position.x) <= half && Math.abs(vertex.position.z) <= half);
            assertTrue(vertex.position.y >= 0 && vertex.position.y <= 1.003);
            assertEquals(255, vertex.color >>> 24);
        }
    }
}
