package top.skyeyefast.mchjong.client;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Replace only the frame; preserve EditBox's caret, selection, clipboard, filter and hit area. */
public final class MahjongEditBox extends EditBox {
    private Predicate<String> filter = value -> true;

    public MahjongEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        setTextColor(MahjongUi.TEXT);
        setTextColorUneditable(MahjongUi.DISABLED);
    }

    public void setFilter(Predicate<String> filter) {
        this.filter = Objects.requireNonNull(filter);
        if (!filter.test(getValue())) super.setValue("");
    }

    @Override public void setValue(String value) {
        if (filter.test(value)) super.setValue(value);
    }

    @Override public void insertText(String text) {
        String before = getValue();
        int cursor = getCursorPosition();
        super.insertText(text);
        if (!filter.test(getValue())) {
            super.setValue(before);
            setCursorPosition(Math.min(cursor, before.length()));
        }
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = width;
        g.fill(x, y, x + w, y + height, MahjongUi.INPUT);
        g.outline(x, y, w, height, isFocused() ? MahjongUi.ACCENT : MahjongUi.EDGE);
        // Native bordered fields use this same four-pixel text inset. Temporarily suppress
        // only its stock frame while rendering, then restore the native interaction bounds.
        setBordered(false);
        setX(x + 4); setY(y + (height - 8) / 2); width = w - 8;
        try { super.extractWidgetRenderState(g, mouseX, mouseY, partialTick); }
        finally { setX(x); setY(y); width = w; setBordered(true); }
    }
}
