package top.skyeyefast.mchjong.client;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImmersiveDepthTest {
    @Test void tableVerticesStayInsideTheNativeGuiClipRange() {
        // GuiRenderer's native projection and model transform, without requiring a GPU device.
        var matrix = new Matrix4f().setOrtho(0, TableCanvas.WIDTH, TableCanvas.HEIGHT, 0, 1000, 11000, true)
            .translate(0, 0, -11000);
        for (double z : new double[]{-530, -445, 0, 445, 530}) {
            float previousDepth = Float.POSITIVE_INFINITY;
            for (double height : new double[]{-20, 0, 8, 80}) {
                var vertex = new ImmersiveTable.Vertex(0, z, height);
                var point = TableProjection.project(vertex.x(), vertex.z(), vertex.h());
                var clip = new Vector4f(point.x(), point.y(), ImmersiveTable.guiDepth(vertex), 1).mul(matrix);
                float depth = clip.z / clip.w;
                assertTrue(depth > 0 && depth < 1, "Table vertex clipped at z=" + z + ", height=" + height);
                assertTrue(depth < previousDepth, "Higher table faces must remain closer to the camera");
                previousDepth = depth;
            }
        }
    }
}
