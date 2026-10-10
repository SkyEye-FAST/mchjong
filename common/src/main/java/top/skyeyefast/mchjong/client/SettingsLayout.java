package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared floating panel, category rail, option rows and footer geometry. */
record SettingsLayout(int left, int top, int span, int rail, int height) {
    static SettingsLayout of(int width, int height, int navigationHeight, int contentHeight) {
        int span = Math.min(464, width - 32);
        int panelHeight = Math.min(Math.min(300, height - 24), 82 + Math.max(navigationHeight, contentHeight));
        return new SettingsLayout((width - span) / 2, (height - panelHeight) / 2, span,
            navigationHeight == 0 ? 0 : Math.min(104, Math.max(76, span / 4)), panelHeight);
    }
    int bodyLeft() { return left + (rail == 0 ? 0 : rail + 8); }
    int bodyWidth() { return span - (rail == 0 ? 0 : rail + 8); }
    int contentTop() { return top + 30; }
    int rows() { return Math.max(1, (paging() - contentTop()) / 22); }
    int footer() { return top + height - 26; }
    int paging() { return top + height - 52; }
    void paint(GuiGraphics graphics, Font font, Component title) {
        MahjongUi.panel(graphics, left - 6, top, span + 12, height);
        graphics.fill(left - 5, top + 1, left + span + 5, top + 27, MahjongUi.INPUT);
        MahjongUi.text(graphics, font, title, left + 7, top + 10, span - 14, MahjongUi.TEXT, false);
        if (rail > 0) graphics.fill(left + rail + 3, contentTop(), left + rail + 4, paging(), MahjongUi.EDGE);
        graphics.fill(left, footer() - 5, left + span, footer() - 4, MahjongUi.EDGE);
    }
}
