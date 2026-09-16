package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TileMeshTest {
    private static final class Vertex {
        final Vector3f position;
        Vector3f normal;
        float u, v;
        int alpha, color;
        Vertex(float x, float y, float z) { position = new Vector3f(x, y, z); }
    }

    private static final class Mesh implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) { vertices.add(new Vertex(x, y, z)); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            vertices.getLast().alpha = a;
            vertices.getLast().color = a << 24 | r << 16 | g << 8 | b;
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v) { vertices.getLast().u = u; vertices.getLast().v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { vertices.getLast().normal = new Vector3f(x, y, z); return this; }
    }

    @Test void everyQuadWindsOutwardIncludingTheBackAfterRotation() {
        for (int pitch : new int[]{0, -90, 90}) for (boolean hidden : new boolean[]{false, true}) {
            var pose = new PoseStack();
            pose.mulPose(Axis.YP.rotationDegrees(90));
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.scale(TableScene.TILE_SCALE, TableScene.TILE_SCALE, TableScene.TILE_SCALE);
            var mesh = new Mesh();
            TileMesh.drawBody(pose, mesh, 0, top.skyeyefast.mchjong.item.TileMaterial.BONE);
            TileMesh.drawFace(pose, mesh, 0, hidden, 0);
            TileMesh.drawBack(pose, mesh, hidden, 0);
            assertEquals(0, mesh.vertices.size() % 4);
            for (int i = 0; i < mesh.vertices.size(); i += 4) {
                var a = mesh.vertices.get(i);
                var b = mesh.vertices.get(i + 1);
                var c = mesh.vertices.get(i + 2);
                var normal = new Vector3f(b.position).sub(a.position).cross(new Vector3f(c.position).sub(a.position)).normalize();
                assertTrue(normal.dot(a.normal) > 0.999, "Inside-out face at vertex " + i + " pitch " + pitch);
                for (int corner = 1; corner < 4; corner++) assertEquals(a.normal, mesh.vertices.get(i + corner).normal);
            }
        }
    }

    @Test void glassBodyIsTranslucentAndHiddenFacesEmitNoGlyphGeometry() {
        var hidden = new Mesh();
        var shown = new Mesh();
        TileMesh.drawFace(new PoseStack(), hidden, -1, false, 0);
        TileMesh.drawFace(new PoseStack(), shown, 0, false, 0);
        assertEquals(24, hidden.vertices.size(), "White inlay only, no printed face");
        assertEquals(hidden.vertices.size() + 4, shown.vertices.size());
        assertTrue(hidden.vertices.stream().allMatch(vertex -> vertex.color == 0xffffffff));
        var body = new Mesh();
        TileMesh.drawBody(new PoseStack(), body, 0, top.skyeyefast.mchjong.item.TileMaterial.GLASS);
        assertEquals(24, body.vertices.size());
        assertTrue(body.vertices.stream().allMatch(vertex -> vertex.alpha > 0 && vertex.alpha < 255));
        var backs = new Mesh();
        TileMesh.drawBack(new PoseStack(), backs, false, 0, net.minecraft.world.item.DyeColor.RED);
        assertTrue(backs.vertices.stream().allMatch(vertex -> vertex.alpha == 255), "Uniform opaque backs protect hand identity");
        var faceDown = new Mesh();
        TileMesh.drawFace(new PoseStack(), faceDown, 0, true, 0);
        assertEquals(hidden.vertices.size(), faceDown.vertices.size());
    }

    @Test void everyMaterialHasAnOpaqueWhiteFaceWithoutTintingItsGlyphsOrBody() {
        for (var material : top.skyeyefast.mchjong.item.TileMaterial.values()) {
            var mesh = new Mesh();
            TileMesh.drawBody(new PoseStack(), mesh, 0, material);
            TileMesh.drawFace(new PoseStack(), mesh, 0, false, 0);
            assertTrue(mesh.vertices.subList(0, 24).stream().allMatch(vertex -> vertex.color == material.color()), material.name());
            assertTrue(mesh.vertices.subList(24, 52).stream().allMatch(vertex -> vertex.color == 0xffffffff), material.name());
            assertEquals(TileMesh.DEPTH / 2, mesh.vertices.getLast().position.z);
        }
    }

    @Test void faceDownBackIsAboveTheBodyAndTextureKeepsItsOrientation() {
        var pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(90));
        var mesh = new Mesh();
        TileMesh.drawBack(pose, mesh, false, 0);
        var face = mesh.vertices.subList(mesh.vertices.size() - 4, mesh.vertices.size());
        assertTrue(face.stream().allMatch(vertex -> vertex.position.y > 0.036 && vertex.normal.y > 0.99));
        assertEquals(0, face.get(0).u);
        assertEquals(1, face.get(1).u);
        assertEquals(1, face.get(0).v);
        assertEquals(0, face.get(2).v);
    }
}
