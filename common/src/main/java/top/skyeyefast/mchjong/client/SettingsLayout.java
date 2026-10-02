package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Shared floating panel, category rail, option rows and footer geometry. */
record SettingsLayout(int left, int top, int span, int rail, int height) {
    static SettingsLayout of(int width, int height) {
        int span = Math.min(420, width - 48), panelHeight = Math.min(240, height - 32);
        return new SettingsLayout((width - span) / 2, (height - panelHeight) / 2, span,
            Math.min(112, Math.max(84, span / 4)), panelHeight);
    }
    int bodyLeft() { return left + rail + 8; }
    int bodyWidth() { return span - rail - 8; }
    int contentTop() { return top + 38; }
    int rows() { return Math.max(1, (height - 92) / 22); }
    int footer() { return top + height - 26; }
    int paging() { return top + height - 52; }
    void paint(GuiGraphicsExtractor graphics, Font font, Component title) {
        MahjongUi.panel(graphics, left - 6, top, span + 12, height);
        MahjongUi.text(graphics, font, title, left + 7, top + 12, span - 14, MahjongUi.TEXT, false);
    }
}
