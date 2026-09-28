package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared category rail, option rows and footer geometry for settings screens. */
record SettingsLayout(int left, int span, int rail, int height) {
    static SettingsLayout of(int width, int height) {
        int span = Math.min(760, width - 24);
        return new SettingsLayout((width - span) / 2, span, Math.min(144, Math.max(88, span / 4)), height);
    }
    int bodyLeft() { return left + rail + 8; }
    int bodyWidth() { return span - rail - 8; }
    int rows() { return Math.max(1, (height - 124) / 22); }
    int footer() { return height - 30; }
    int paging() { return height - 56; }
    void paint(GuiGraphics graphics, Font font, int width, Component title, Component section) {
        graphics.fill(0, 0, width, height, MahjongUi.BACKDROP);
        graphics.fill(left, 10, left + span, 32, MahjongUi.INPUT);
        MahjongUi.text(graphics, font, title, left + 7, 17, span - 14, MahjongUi.TEXT, false);
        MahjongUi.text(graphics, font, section, bodyLeft() + 6, 43, bodyWidth() - 12, MahjongUi.TEXT, true);
    }
}
