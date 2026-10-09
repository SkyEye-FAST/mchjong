package top.skyeyefast.mchjong.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InterfaceLayoutTest {
    @Test void panelsFitContentAndUnpopulatedNavigationDoesNotReserveAColumn() {
        for (int[] size : new int[][]{{320, 240}, {640, 400}, {1280, 800}}) {
            var compact = SettingsLayout.of(size[0], size[1], 72, 44);
            var dense = SettingsLayout.of(size[0], size[1], 88, 264);
            var presets = SettingsLayout.of(size[0], size[1], 0, 144);
            assertTrue(compact.height() < dense.height());
            assertEquals(presets.left(), presets.bodyLeft());
            assertEquals(presets.span(), presets.bodyWidth());
            for (var layout : new SettingsLayout[]{compact, dense, presets}) {
                assertTrue(layout.left() >= 6 && layout.top() >= 0);
                assertTrue(layout.left() + layout.span() + 6 <= size[0]);
                assertTrue(layout.footer() + 20 <= size[1]);
                assertTrue(layout.contentTop() + layout.rows() * 22 <= layout.paging());
            }
        }
    }

    @Test void windDisplayBaselinesFollowProjectedSeatEdgesAndTopsFaceTheCenter() {
        for (int side = 0; side < 4; side++) {
            var transform = TableProjection.surface(side, 0, 70, 8.5);
            var left = TableProjection.seat(side, -22, 70, 8.5);
            var right = TableProjection.seat(side, 22, 70, 8.5);
            var start = transform.transformPosition(new org.joml.Vector3f(-22, 0, 0));
            var end = transform.transformPosition(new org.joml.Vector3f(22, 0, 0));
            double cross = (end.x - start.x) * (right.y() - left.y()) - (end.y - start.y) * (right.x() - left.x());
            assertEquals(0, cross, .1, "Font baseline must follow the table plane at seat " + side);
            var center = TableProjection.project(0, 0, 8.5);
            var origin = transform.transformPosition(new org.joml.Vector3f());
            var top = transform.transformDirection(new org.joml.Vector3f(0, -1, 0));
            assertTrue(top.x * (center.x() - origin.x) + top.y * (center.y() - origin.y) > 0);
        }
    }
}
