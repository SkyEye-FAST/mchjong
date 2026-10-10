package top.skyeyefast.mchjong.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.IntStream;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class RoomVariantListTest {
    @Test void additionalRulesStayReachableWithoutShrinkingOrOverlappingTheBody() {
        var buttons = IntStream.range(0, 18).mapToObj(index ->
            new MahjongButton(0, 0, 84, 20, Component.literal("Rule " + index), ignored -> {})).toList();
        int[] saved = {0};
        var list = new RoomVariantList(16, 44, 84, 132, buttons, 0, value -> saved[0] = value);
        assertEquals(5, buttons.stream().filter(button -> button.visible).count());
        assertFalse(list.mouseScrolled(110, 50, -1));
        assertTrue(list.mouseScrolled(30, 50, -100));
        assertTrue(buttons.get(buttons.size() - 1).visible);
        assertFalse(buttons.get(0).visible);
        assertEquals(13, saved[0]);
        for (var button : buttons) {
            assertEquals(76, button.getWidth());
            if (button.visible) {
                assertTrue(button.getY() >= 44 && button.getY() + button.getHeight() <= 164);
                assertFalse(button.isMouseOver(110, button.getY() + 4));
            }
        }
        list.reveal(0);
        assertTrue(buttons.get(0).visible);
        assertEquals(0, saved[0]);
        assertFalse(list.mouseClicked(30, 50, 0));
        assertTrue(list.mouseClicked(97, 50, 0));
        assertTrue(list.mouseDragged(97, 200, 0, 0, 150));
        assertTrue(buttons.get(buttons.size() - 1).visible);
        var rebuilt = new RoomVariantList(16, 44, 84, 132, buttons, saved[0], value -> saved[0] = value);
        assertTrue(buttons.get(buttons.size() - 1).visible);
        rebuilt.reveal(6);
        assertTrue(buttons.get(6).visible);
    }
}
