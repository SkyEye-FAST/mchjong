package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Custom paint, native focus, keyboard activation, sound, tooltip and narration. */
public class MahjongButton extends Button {
    private boolean selected;
    private boolean primary;
    private boolean navigation;
    private Component shortCaption;
    private Component optionLabel;
    private Component optionValue;
    private Boolean checked;
    private float textScale = 1;

    public MahjongButton(int x, int y, int width, int height, Component message, OnPress action) {
        super(x, y, width, height, message, action, DEFAULT_NARRATION);
        setTooltip(Tooltip.create(message));
    }

    public MahjongButton selected(boolean value) { selected = value; return this; }
    public MahjongButton primary() { primary = true; return this; }
    public MahjongButton navigation() { navigation = true; return this; }
    public MahjongButton shortCaption(Component value) { shortCaption = value; return this; }
    public MahjongButton option(Component label, Component value) { optionLabel = label; optionValue = value; return this; }
    public MahjongButton checked(boolean value) { checked = value; return this; }
    public MahjongButton textScale(float value) { textScale = value; return this; }
    protected final int captionColor() { return active ? MahjongUi.TEXT : MahjongUi.DISABLED; }

    @Override public void setMessage(Component message) {
        super.setMessage(message);
        setTooltip(Tooltip.create(message));
    }

    @Override protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        renderSurface(g);
        if (optionLabel != null) {
            var font = Minecraft.getInstance().font;
            int color = active ? MahjongUi.TEXT : MahjongUi.DISABLED;
            int valueWidth = checked != null ? 12 : Math.min(width / 2, font.width(optionValue));
            MahjongUi.text(g, font, optionLabel, getX() + 6, getY() + (height - 8) / 2,
                width - valueWidth - 22, color, false);
            if (checked != null) {
                int cx = getX() + width - 16, cy = getY() + (height - 10) / 2;
                g.outline(cx, cy, 10, 10, active && checked ? MahjongUi.ACCENT : color);
                if (checked) g.fill(cx + 2, cy + 2, cx + 8, cy + 8, active ? MahjongUi.ACCENT : color);
            } else MahjongUi.text(g, font, optionValue, getX() + width - valueWidth - 6,
                getY() + (height - 8) / 2, valueWidth, color, false);
            return;
        }
        if (navigation) {
            MahjongUi.text(g, Minecraft.getInstance().font, caption(width - 14), getX() + 7, getY() + (height - 8) / 2,
                width - 14, active ? selected ? MahjongUi.ACCENT : MahjongUi.TEXT : MahjongUi.DISABLED, false);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(getX() + width / 2f, getY() + height / 2f);
        g.pose().scale(textScale, textScale);
        int captionWidth = (int) ((width - 8) / textScale);
        MahjongUi.text(g, Minecraft.getInstance().font, caption(captionWidth), -captionWidth / 2, -4,
            captionWidth, captionColor(), true);
        g.pose().popMatrix();
    }

    private Component caption(int available) {
        return shortCaption != null && Minecraft.getInstance().font.width(getMessage()) > available ? shortCaption : getMessage();
    }

    protected final void renderSurface(GuiGraphicsExtractor g) {
        if (optionLabel != null) {
            g.fill(getX(), getY(), getX() + width, getY() + height, active && isHovered() ? MahjongUi.HOVER : MahjongUi.INPUT);
            g.fill(getX() + 6, getY() + height - 1, getX() + width - 6, getY() + height, MahjongUi.SURFACE);
            if (active && isFocused()) g.outline(getX(), getY(), width, height, MahjongUi.ACCENT);
            return;
        }
        if (navigation) {
            g.fill(getX(), getY(), getX() + width, getY() + height,
                selected ? MahjongUi.SELECTED : isHovered() ? MahjongUi.HOVER : MahjongUi.PANEL);
            if (selected) g.fill(getX() + 7, getY() + height - 2, getX() + width - 7, getY() + height - 1, MahjongUi.ACCENT);
            if (isFocused()) g.outline(getX(), getY(), width, height, MahjongUi.ACCENT);
            return;
        }
        MahjongUi.control(g, getX(), getY(), width, height, active, isHovered(), isFocused(), selected, primary);
    }

    public static Factory create(Component message, OnPress action) { return new Factory(message, action); }

    public static final class Factory {
        private final Component message;
        private final OnPress action;
        private int x, y, width = 150, height = 20;
        private Tooltip tooltip;
        private Factory(Component message, OnPress action) { this.message = message; this.action = action; }
        public Factory bounds(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
            return this;
        }
        public Factory tooltip(Tooltip tooltip) { this.tooltip = tooltip; return this; }
        public MahjongButton build() {
            var button = new MahjongButton(x, y, width, height, message, action);
            if (tooltip != null) button.setTooltip(tooltip);
            return button;
        }
    }
}
