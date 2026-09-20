package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared visual vocabulary. Interaction remains in Minecraft's native widget/menu classes. */
public final class MahjongUi {
    public static final int BACKDROP = 0xd90b1418;
    public static final int PANEL = 0xf018292e;
    public static final int SURFACE = 0xff22383d;
    public static final int HOVER = 0xff304b50;
    public static final int INPUT = 0xff101e23;
    public static final int EDGE = 0xff4c686b;
    public static final int TEXT = 0xfff1eee3;
    public static final int MUTED = 0xffbdcfca;
    public static final int DISABLED = 0xff81938f;
    public static final int ACCENT = 0xffe3c082;
    public static final int SELECTED = 0xff365851;
    public static final int POSITIVE = 0xffa9d8b8;
    public static final int NEGATIVE = 0xfff0aaa4;
    public static final int DANGER = 0xffa33232;
    public static final int ON_DANGER = 0xffffffff;

    private MahjongUi() {}

    public static void panel(GuiGraphics g, int x, int y, int width, int height) {
        g.fill(x, y, x + width, y + height, PANEL);
        g.renderOutline(x, y, width, height, EDGE);
    }

    public static void backdrop(GuiGraphics g, int width, int height, int contentWidth) {
        g.fill(0, 0, width, height, BACKDROP);
        int span = Math.min(contentWidth, width - 24);
        panel(g, (width - span) / 2 - 8, 6, span + 16, height - 12);
        g.fill((width - span) / 2, 7, (width + span) / 2, 8, ACCENT);
    }

    public static void control(GuiGraphics g, int x, int y, int width, int height,
                               boolean active, boolean hover, boolean focused, boolean selected, boolean primary) {
        int fill = selected ? SELECTED : !active ? INPUT : hover ? HOVER : SURFACE;
        g.fill(x, y, x + width, y + height, fill);
        g.renderOutline(x, y, width, height, active && focused ? TEXT : selected || primary ? ACCENT : EDGE);
        if (selected || primary && active) g.fill(x + 1, y + height - 3, x + width - 1, y + height - 1, ACCENT);
        if (active && focused) g.renderOutline(x + 2, y + 2, width - 4, height - 4, ACCENT);
    }

    public static void text(GuiGraphics g, Font font, Component message, int x, int y, int width, int color, boolean centered) {
        text(g, font, message, x, y, width, color, centered, false);
    }

    public static void text(GuiGraphics g, Font font, Component message, int x, int y, int width, int color,
                            boolean centered, boolean shadow) {
        String text = message.getString();
        int available = Math.max(0, width);
        if (font.width(text) > available) {
            String ellipsis = "…";
            text = available < font.width(ellipsis) ? "" : font.plainSubstrByWidth(text, available - font.width(ellipsis)) + ellipsis;
        }
        g.drawString(font, text, centered ? x + (width - font.width(text)) / 2 : x, y, color, shadow);
    }

    public static void slot(GuiGraphics g, int x, int y, boolean locked) {
        g.fill(x - 1, y - 1, x + 17, y + 17, INPUT);
        g.renderOutline(x - 1, y - 1, 18, 18, locked ? ACCENT : EDGE);
        if (locked) g.fill(x + 5, y + 15, x + 11, y + 17, ACCENT);
    }
}
