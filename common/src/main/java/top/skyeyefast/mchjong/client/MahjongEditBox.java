package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Replace only the frame; preserve EditBox's caret, selection, clipboard, filter and hit area. */
public final class MahjongEditBox extends EditBox {
    public MahjongEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        setTextColor(MahjongUi.TEXT);
        setTextColorUneditable(MahjongUi.DISABLED);
    }

    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = width;
        g.fill(x, y, x + w, y + height, MahjongUi.INPUT);
        g.renderOutline(x, y, w, height, isFocused() ? MahjongUi.ACCENT : MahjongUi.EDGE);
        // Native bordered fields use this same four-pixel text inset. Temporarily suppress
        // only its stock frame while rendering, then restore the native interaction bounds.
        setBordered(false);
        setX(x + 4); setY(y + (height - 8) / 2); width = w - 8;
        try { super.renderWidget(g, mouseX, mouseY, partialTick); }
        finally { setX(x); setY(y); width = w; setBordered(true); }
    }
}
