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

    @Test void physicalFlowersHaveDistinctArtworkWithoutAliasingRedFives() {
        var designs = new java.util.HashSet<Integer>();
        var material = top.skyeyefast.mchjong.item.TileMaterial.BONE;
        for (int face = 0; face < 42; face++) {
            var data = new top.skyeyefast.mchjong.item.TileData(face, material, false);
            int artwork = TileMesh.artwork(data);
            assertTrue(designs.add(artwork));
            var mesh = new Mesh();
            TileMesh.drawArtwork(new PoseStack(), mesh, artwork, 0);
            var printed = mesh.vertices.subList(mesh.vertices.size() - 4, mesh.vertices.size());
            assertTrue(printed.stream().allMatch(vertex -> vertex.u >= 0 && vertex.u <= 1 && vertex.v >= 0 && vertex.v <= 1));
            if (data.flower()) assertEquals(face + 3, artwork);
        }
        for (int face : new int[]{4, 13, 22})
            assertTrue(designs.add(TileMesh.artwork(new top.skyeyefast.mchjong.item.TileData(face, material, true))));
        assertEquals(45, designs.size());
        assertEquals(-1, TileMesh.artwork(top.skyeyefast.mchjong.item.TileData.BLANK));
        assertThrows(IllegalArgumentException.class, () -> TileMesh.drawArtwork(new PoseStack(), new Mesh(), 45, 0));
    }

    @Test void highlightFollowsBeveledFrontBackAndSideEdgesUnderTheTilePose() {
        var local = new Mesh();
        TileMesh.drawOutline(new PoseStack(), local, MahjongUi.ACCENT);
        assertEquals(80, local.vertices.size(), "Forty edges include both beveled rims and the thickness");
        assertTrue(local.vertices.stream().allMatch(vertex -> vertex.color == MahjongUi.ACCENT));
        for (int i = 0; i < local.vertices.size(); i += 2) {
            var a = local.vertices.get(i);
            var b = local.vertices.get(i + 1);
            assertNotEquals(a.position, b.position);
            assertEquals(1, a.normal.length(), 1e-6);
            assertTrue(new Vector3f(b.position).sub(a.position).normalize().dot(a.normal) > .999);
        }
        for (int yaw : new int[]{0, 90, 180, 270}) for (int pitch : new int[]{0, -45, -90, 90}) {
            var pose = new PoseStack();
            pose.translate(.2, 1.1, -.4);
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.scale(TableScene.TILE_SCALE, TableScene.TILE_SCALE, TableScene.TILE_SCALE);
            var transformed = new Mesh();
            TileMesh.drawOutline(pose, transformed, MahjongUi.ACCENT);
            for (int i = 0; i < local.vertices.size(); i++)
                assertTrue(pose.last().pose().transformPosition(new Vector3f(local.vertices.get(i).position))
                    .distance(transformed.vertices.get(i).position) < 1e-6);
        }
    }

    @Test void everyQuadWindsOutwardIncludingTheBackAfterRotation() {
        for (int pitch : new int[]{0, -90, 90}) for (boolean hidden : new boolean[]{false, true}) {
            var pose = new PoseStack();
            pose.mulPose(Axis.YP.rotationDegrees(90));
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.scale(TableScene.TILE_SCALE, TableScene.TILE_SCALE, TableScene.TILE_SCALE);
            var mesh = new Mesh();
            var material = top.skyeyefast.mchjong.item.TileMaterial.BONE;
            TileMesh.drawBody(pose, mesh, 0, material, null);
            TileMesh.drawFace(pose, mesh, 0, hidden, 0);
            TileMesh.drawBack(pose, mesh, hidden, 0, material, null);
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

    @Test void concealedTileShellHasNoOpenEdges() {
        var mesh = new Mesh();
        var pose = new PoseStack();
        var material = top.skyeyefast.mchjong.item.TileMaterial.BONE;
        TileMesh.drawBody(pose, mesh, 0, material, null);
        TileMesh.drawBack(pose, mesh, true, 0, material, null);
        var edges = new java.util.HashMap<java.util.Set<Vector3f>, Integer>();
        for (int quad = 0; quad < mesh.vertices.size(); quad += 4) for (int corner = 0; corner < 4; corner++) {
            var a = mesh.vertices.get(quad + corner).position;
            var b = mesh.vertices.get(quad + (corner + 1) % 4).position;
            edges.merge(java.util.Set.of(a, b), 1, Integer::sum);
        }
        assertTrue(edges.values().stream().allMatch(count -> count == 2), "Every shell edge must meet exactly one neighboring face");
    }

    @Test void glassBodyIsTranslucentAndHiddenFacesEmitNoGlyphGeometry() {
        var hidden = new Mesh();
        var shown = new Mesh();
        TileMesh.drawFace(new PoseStack(), hidden, -1, false, 0);
        TileMesh.drawFace(new PoseStack(), shown, 0, false, 0);
        assertEquals(0, hidden.vertices.size(), "Hidden tiles leave the printed shell to the concealed back pass");
        assertEquals(80, shown.vertices.size());
        var body = new Mesh();
        var glass = top.skyeyefast.mchjong.item.TileMaterial.GLASS;
        TileMesh.drawBody(new PoseStack(), body, 0, glass, null);
        assertEquals(32, body.vertices.size(), "Eight material sides, no overlapping internal caps");
        assertTrue(body.vertices.stream().allMatch(vertex -> vertex.alpha > 0 && vertex.alpha < 255));
        var backs = new Mesh();
        TileMesh.drawBack(new PoseStack(), backs, false, 0, glass, net.minecraft.world.item.DyeColor.RED);
        assertTrue(backs.vertices.stream().allMatch(vertex -> vertex.alpha > 0 && vertex.alpha < 255),
            "Dyed glass stays translucent instead of gaining an opaque back");
        assertTrue(TileMesh.usesMaterialBack(glass, net.minecraft.world.item.DyeColor.RED));
        assertTrue(TileMesh.usesMaterialBack(top.skyeyefast.mchjong.item.TileMaterial.BONE, null));
        assertFalse(TileMesh.usesMaterialBack(top.skyeyefast.mchjong.item.TileMaterial.BONE, net.minecraft.world.item.DyeColor.RED));
        var faceDown = new Mesh();
        TileMesh.drawFace(new PoseStack(), faceDown, 0, true, 0);
        assertTrue(faceDown.vertices.isEmpty());
    }

    @Test void everyMaterialHasAnOpaqueWhiteFaceWithoutTintingItsGlyphsOrBody() {
        for (var material : top.skyeyefast.mchjong.item.TileMaterial.values()) {
            var mesh = new Mesh();
            TileMesh.drawBody(new PoseStack(), mesh, 0, material, null);
            int bodyVertices = mesh.vertices.size();
            TileMesh.drawFace(new PoseStack(), mesh, 0, false, 0);
            assertTrue(mesh.vertices.subList(0, bodyVertices).stream().allMatch(vertex -> vertex.color == material.color()), material.name());
            assertTrue(mesh.vertices.subList(bodyVertices, mesh.vertices.size()).stream().allMatch(vertex -> vertex.color == 0xffffffff), material.name());
            assertEquals(TileMesh.DEPTH / 2, mesh.vertices.getLast().position.z);
        }
    }

    @Test void faceDownBackIsAboveTheBodyAndTextureKeepsItsOrientation() {
        var pose = new PoseStack();
        pose.mulPose(Axis.XP.rotationDegrees(90));
        var mesh = new Mesh();
        TileMesh.drawBack(pose, mesh, false, 0, top.skyeyefast.mchjong.item.TileMaterial.BONE,
            net.minecraft.world.item.DyeColor.BLUE);
        var face = mesh.vertices.subList(mesh.vertices.size() - 12, mesh.vertices.size());
        assertTrue(face.stream().allMatch(vertex -> vertex.position.y > 0.036 && vertex.normal.y > 0.99));
        for (var vertex : face) {
            assertEquals(.5f - vertex.position.x / (TileMesh.WIDTH - .003f), vertex.u, 1e-6);
            assertEquals(.5f - vertex.position.z / (TileMesh.HEIGHT - .003f), vertex.v, 1e-6);
        }
    }

    @Test void backBodyAndFaceFormOneClosedShellWithoutDuplicateInternalSurfaces() {
        var mesh = new Mesh();
        var pose = new PoseStack();
        var material = top.skyeyefast.mchjong.item.TileMaterial.BONE;
        TileMesh.drawBody(pose, mesh, 0, material, null);
        TileMesh.drawArtwork(pose, mesh, -1, 0);
        TileMesh.drawBack(pose, mesh, false, 0, material, null);
        var edges = new java.util.HashMap<java.util.Set<Vector3f>, Integer>();
        for (int i = 0; i < mesh.vertices.size(); i += 4) for (int corner = 0; corner < 4; corner++) {
            var a = mesh.vertices.get(i + corner).position;
            var b = mesh.vertices.get(i + (corner + 1) % 4).position;
            assertNotEquals(a, b, "No degenerate edges");
            edges.merge(java.util.Set.of(a, b), 1, Integer::sum);
        }
        assertTrue(edges.values().stream().allMatch(count -> count == 2), "Every edge has exactly two neighbors");
    }
}
