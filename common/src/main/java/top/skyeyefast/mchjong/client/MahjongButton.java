package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Custom paint, native focus, keyboard activation, sound, tooltip and narration. */
public class MahjongButton extends Button {
    private boolean selected;
    private boolean primary;

    public MahjongButton(int x, int y, int width, int height, Component message, OnPress action) {
        super(x, y, width, height, message, action, DEFAULT_NARRATION);
        setTooltip(Tooltip.create(message));
    }

    public MahjongButton selected(boolean value) { selected = value; return this; }
    public MahjongButton primary() { primary = true; return this; }

    @Override public void setMessage(Component message) {
        super.setMessage(message);
        setTooltip(Tooltip.create(message));
    }

    @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderSurface(g);
        MahjongUi.text(g, Minecraft.getInstance().font, getMessage(), getX() + 6, getY() + (height - 8) / 2,
            width - 12, active || selected ? MahjongUi.TEXT : MahjongUi.DISABLED, true);
    }

    protected final void renderSurface(GuiGraphics g) {
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
