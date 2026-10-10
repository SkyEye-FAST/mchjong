package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Scrolls whole rows while keeping each rule a native, independently focusable button. */
public final class RoomVariantList extends AbstractWidget {
    private static final int ROW = 24;
    private final List<MahjongButton> buttons;
    private final IntConsumer changed;
    private final int rows;
    private int first;

    RoomVariantList(int x, int y, int width, int height, List<MahjongButton> buttons, int first, IntConsumer changed) {
        super(x, y, width, Math.max(ROW, height / ROW * ROW), Component.empty());
        this.buttons = buttons;
        this.changed = changed;
        rows = this.height / ROW;
        scrollTo(first);
    }

    void reveal(int index) {
        if (index < first) scrollTo(index);
        else if (index >= first + rows) scrollTo(index - rows + 1);
    }

    public List<MahjongButton> children() { return buttons; }

    private int maximum() { return Math.max(0, buttons.size() - rows); }
    private int thumbHeight() { return Math.max(12, height * rows / buttons.size()); }

    private void scrollTo(int row) {
        first = net.minecraft.util.Mth.clamp(row, 0, maximum());
        for (int index = 0; index < buttons.size(); index++) {
            var button = buttons.get(index);
            button.visible = index >= first && index < first + rows;
            button.setX(getX());
            button.setY(getY() + (index - first) * ROW);
            button.setWidth(width - (maximum() > 0 ? 8 : 0));
            if (!button.visible) button.setFocused(false);
        }
        changed.accept(first);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (maximum() == 0) return;
        int thumb = thumbHeight(), top = getY() + first * (height - thumb) / maximum();
        graphics.fill(getX() + width - 4, getY(), getX() + width - 2, getY() + height, MahjongUi.EDGE);
        graphics.fill(getX() + width - 5, top, getX() + width - 1, top + thumb, MahjongUi.ACCENT);
    }

    @Override public boolean mouseScrolled(double x, double y, double vertical) {
        if (!isMouseOver(x, y) || maximum() == 0 || vertical == 0) return false;
        scrollTo(first - (int) Math.copySign(Math.max(1, Math.abs(Math.round(vertical))), vertical));
        return true;
    }

    private void scrollAt(double y) {
        scrollTo((int) Math.round((y - getY() - thumbHeight() / 2.0) * maximum() / (height - thumbHeight())));
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || maximum() == 0 || !isMouseOver(x, y) || x < getX() + width - 8) return false;
        scrollAt(y);
        return true;
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0 || maximum() == 0) return false;
        scrollAt(y);
        return true;
    }

    @Override public ComponentPath nextFocusPath(FocusNavigationEvent event) { return null; }
    @Override public NarrationPriority narrationPriority() { return NarrationPriority.NONE; }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {}
}
