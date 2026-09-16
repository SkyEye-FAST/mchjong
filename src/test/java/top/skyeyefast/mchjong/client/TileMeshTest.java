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
        Vertex(float x, float y, float z) { position = new Vector3f(x, y, z); }
    }

    private static final class Mesh implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x, float y, float z) { vertices.add(new Vertex(x, y, z)); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
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

    @Test void faceDownBackIsAboveTheIvoryBodyAndTextureKeepsItsOrientation() {
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
