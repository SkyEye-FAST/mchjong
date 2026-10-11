package top.skyeyefast.mchjong.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InterfaceLayoutTest {
    @Test void transientCaptionsStayCenteredAndClearOfInterfaceElements() {
        for (int[] size : new int[][]{{320, 240}, {640, 400}, {1280, 800}}) {
            int width = size[0], height = size[1], scale = width == 1280 ? 2 : 1;
            var occupied = java.util.List.of(
                new net.minecraft.client.gui.navigation.ScreenRectangle(8, 7, width - 16, 60 * scale),
                new net.minecraft.client.gui.navigation.ScreenRectangle(width - 140 * scale, height / 2, 132 * scale, 50 * scale),
                new net.minecraft.client.gui.navigation.ScreenRectangle(0, height - 60 * scale, width, 60 * scale),
                new net.minecraft.client.gui.navigation.ScreenRectangle(20 * scale, height - 100 * scale, 40 * scale, 40 * scale));
            for (int textWidth : new int[]{80 * scale, width * 2}) for (int lines : new int[]{1, 2, 3}) {
                var bounds = TableStatusLayout.of(width, height, textWidth, ((lines - 1) * 13 + 9) * scale, 4 * scale, occupied);
                assertNotNull(bounds);
                assertEquals(width / 2.0, bounds.left() + bounds.width() / 2.0, .5);
                assertTrue(bounds.top() >= 4 * scale && bounds.bottom() <= height - 4 * scale);
                for (var obstacle : occupied) assertFalse(bounds.overlaps(obstacle));
            }
        }
    }

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

    @Test void currentDiscardCardsStayOnTheRightAndClearOfInterfaceElements() {
        for (int[] size : new int[][]{{320, 240}, {640, 400}, {1280, 800}}) {
            int width = size[0], height = size[1], scale = width == 1280 ? 2 : 1;
            var occupied = java.util.List.of(
                new net.minecraft.client.gui.navigation.ScreenRectangle(8, 7, width - 16, 60 * scale),
                new net.minecraft.client.gui.navigation.ScreenRectangle(width - 140 * scale, height / 2, 132 * scale, 50 * scale),
                new net.minecraft.client.gui.navigation.ScreenRectangle(0, height - 60 * scale, width, 60 * scale));
            var bounds = TableStatusLayout.focus(width, height, 100 * scale, 17 * scale, 4 * scale, 84 * scale, occupied);
            assertNotNull(bounds);
            assertEquals(width - 4 * scale, bounds.right());
            assertTrue(bounds.left() >= width / 2 && bounds.top() >= 4 * scale && bounds.bottom() <= height - 4 * scale);
            for (var obstacle : occupied) assertFalse(bounds.overlaps(obstacle));
        }
    }
}
