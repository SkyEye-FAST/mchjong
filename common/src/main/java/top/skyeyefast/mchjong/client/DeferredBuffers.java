package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

/** Records legacy mesh writers and submits them through the 26.x render-state pipeline. */
final class DeferredBuffers implements MultiBufferSource {
    private final SubmitNodeCollector collector;
    private final List<Batch> batches = new ArrayList<>();

    DeferredBuffers(SubmitNodeCollector collector) { this.collector = collector; }

    @Override public VertexConsumer getBuffer(RenderType type) {
        var batch = new Batch(type);
        batches.add(batch);
        return batch.consumer;
    }

    void submit(PoseStack sortPose) {
        for (var batch : batches) if (!batch.vertices.isEmpty())
            collector.submitCustomGeometry(sortPose, batch.type, (ignored, output) -> batch.replay(output));
        batches.clear();
    }

    private static final class Batch {
        final RenderType type;
        final List<Vertex> vertices = new ArrayList<>();
        final Recorder consumer = new Recorder(vertices);

        Batch(RenderType type) { this.type = type; }

        void replay(VertexConsumer output) {
            for (var vertex : vertices) {
                if (vertex.pose == null) output.addVertex(vertex.x, vertex.y, vertex.z);
                else output.addVertex(vertex.pose, vertex.x, vertex.y, vertex.z);
                if (vertex.hasColor) output.setColor(vertex.color);
                if (vertex.hasUv) output.setUv(vertex.u, vertex.v);
                if (vertex.hasUv1) output.setUv1(vertex.u1, vertex.v1);
                if (vertex.hasUv2) output.setUv2(vertex.u2, vertex.v2);
                if (vertex.hasNormal) {
                    if (vertex.normalPose == null) output.setNormal(vertex.nx, vertex.ny, vertex.nz);
                    else output.setNormal(vertex.normalPose, vertex.nx, vertex.ny, vertex.nz);
                }
                if (vertex.hasLineWidth) output.setLineWidth(vertex.lineWidth);
            }
        }
    }

    private static final class Vertex {
        PoseStack.Pose pose;
        PoseStack.Pose normalPose;
        float x, y, z, u, v, nx, ny, nz, lineWidth;
        int color, u1, v1, u2, v2;
        boolean hasColor, hasUv, hasUv1, hasUv2, hasNormal, hasLineWidth;
    }

    private static final class Recorder implements VertexConsumer {
        private final List<Vertex> vertices;
        private Vertex current;

        Recorder(List<Vertex> vertices) { this.vertices = vertices; }

        private Vertex add(float x, float y, float z, PoseStack.Pose pose) {
            current = new Vertex();
            current.x = x; current.y = y; current.z = z;
            current.pose = pose == null ? null : pose.copy();
            vertices.add(current);
            return current;
        }

        private Vertex current() {
            if (current == null) throw new IllegalStateException("Vertex attributes require a vertex");
            return current;
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { add(x, y, z, null); return this; }
        @Override public VertexConsumer addVertex(PoseStack.Pose pose, float x, float y, float z) { add(x, y, z, pose); return this; }
        @Override public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            var v = current(); v.color = (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255; v.hasColor = true; return this;
        }
        @Override public VertexConsumer setColor(int color) { var v = current(); v.color = color; v.hasColor = true; return this; }
        @Override public VertexConsumer setUv(float u, float v) { var vertex = current(); vertex.u = u; vertex.v = v; vertex.hasUv = true; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { var vertex = current(); vertex.u1 = u; vertex.v1 = v; vertex.hasUv1 = true; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { var vertex = current(); vertex.u2 = u; vertex.v2 = v; vertex.hasUv2 = true; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) {
            var v = current(); v.nx = x; v.ny = y; v.nz = z; v.normalPose = null; v.hasNormal = true; return this;
        }
        @Override public VertexConsumer setNormal(PoseStack.Pose pose, float x, float y, float z) {
            var v = current(); v.nx = x; v.ny = y; v.nz = z; v.normalPose = pose.copy(); v.hasNormal = true; return this;
        }
        @Override public VertexConsumer setLineWidth(float width) { var v = current(); v.lineWidth = width; v.hasLineWidth = true; return this; }
    }
}
