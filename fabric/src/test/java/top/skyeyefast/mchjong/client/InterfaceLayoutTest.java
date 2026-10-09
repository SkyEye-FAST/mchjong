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
}
