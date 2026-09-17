package top.skyeyefast.mchjong.smoke;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import top.skyeyefast.mchjong.client.MahjongItemRenderer;
import top.skyeyefast.mchjong.client.TileRenderTypes;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Projects the real component-aware mesh through the loaded, handed item transform. */
final class HeldItemProjectionSmoke {
    private HeldItemProjectionSmoke() {}

    static void verify(Minecraft client, ItemStack stack, boolean left) {
        boolean tile = stack.is(MahjongContent.TILE_ITEM);
        if (!tile && !stack.is(MahjongContent.POINT_STICK)) return;
        var context = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        if (!tile) {
            var local = new Mesh();
            new MahjongItemRenderer().renderByItem(stack, context, new PoseStack(), ignored -> local, 0xf000f0, 0);
            check(local.span(0) > local.span(2) * 10, "Point stick should retain its slender physical proportions");
            check(local.span(1) > .02f && local.span(2) > .05f, "Point stick must retain solid thickness");
        }
        var pose = new PoseStack();
        // ItemInHandRenderer's fully equipped, idle arm pose. Attack rotations cancel at rest.
        int hand = left ? -1 : 1;
        pose.translate(hand * .56f, -.52f, -.72f);
        var meshes = new HashMap<RenderType, Mesh>();
        var renderer = client.getItemRenderer();
        renderer.render(stack, context, left, pose, type -> meshes.computeIfAbsent(type, ignored -> new Mesh()),
            0xf000f0, 0, renderer.getModel(stack, client.level, client.player, 0));
        var vertices = meshes.values().stream().flatMap(mesh -> mesh.vertices.stream()).toList();
        check(vertices.size() >= 24, "Held item must use a solid mesh");
        var center = new Vector3f();
        for (var vertex : vertices) center.add(vertex.position);
        center.div(vertices.size());
        var printNormal = new Vector3f(0, tile ? 0 : 1, tile ? 1 : 0);
        var facing = meshes.get(TileRenderTypes.FACES).vertices.stream()
            .filter(vertex -> vertex.sourceNormal.equals(printNormal)).findFirst().orElseThrow().normal;
        var eye = new Vector3f(center).negate().normalize();
        check(facing.dot(eye) > .45f, "Printed surface points away from the player: " + context);
        if (tile) {
            check(facing.x * hand < -.2f, "Tile face must turn inward: " + context);
            var side = vertices.stream().filter(vertex -> vertex.sourceNormal.x * hand > .999f)
                .findFirst().orElseThrow().normal;
            check(side.dot(eye) > .2f, "Tile thickness disappears behind the face: " + context);
        } else {
            var tip = new Vector3f(vertices.stream().filter(vertex -> vertex.sourceNormal.x * hand > .999f)
                .findFirst().orElseThrow().normal).negate();
            check(tip.x * hand < -.2f && tip.y > .2f, "Point stick's free end must rise inward: " + context);
        }
        check(center.x * hand < .45f, "Held object should extend inward from the grip: " + context);
        for (float aspect : new float[]{4f / 3, 16f / 10, 16f / 9}) {
            var projection = new Matrix4f().perspective((float) Math.toRadians(70), aspect, .05f, 100);
            for (var vertex : vertices) {
                var point = vertex.position;
                var screen = projection.transformProject(new Vector3f(point));
                check(point.z < -.05f && screen.isFinite(), "Held mesh crosses the near plane");
                check(Math.abs(screen.x) < .94f && Math.abs(screen.y) < .84f,
                    "Held mesh is clipped or overlaps the hotbar at " + aspect + ": " + screen + ", " + context);
            }
        }
    }

    private static final class Vertex {
        final Vector3f position;
        Vector3f normal, sourceNormal;
        Vertex(float x, float y, float z) { position = new Vector3f(x, y, z); }
    }

    private static final class Mesh implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        float span(int axis) {
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (var vertex : vertices) {
                min = Math.min(min, vertex.position.get(axis));
                max = Math.max(max, vertex.position.get(axis));
            }
            return max - min;
        }
        @Override public VertexConsumer addVertex(float x, float y, float z) { vertices.add(new Vertex(x, y, z)); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            vertices.getLast().normal = new Vector3f(x, y, z).normalize();
            return this;
        }
        @Override public VertexConsumer setNormal(PoseStack.Pose pose, float x, float y, float z) {
            vertices.getLast().sourceNormal = new Vector3f(x, y, z);
            return VertexConsumer.super.setNormal(pose, x, y, z);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
