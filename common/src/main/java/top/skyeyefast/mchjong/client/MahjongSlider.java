package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public abstract class MahjongSlider extends AbstractSliderButton {
    private Component optionLabel;
    private Component optionValue;
    protected void option(Component label, Component value) { optionLabel = label; optionValue = value; }
    protected MahjongSlider(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message, value);
    }

    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (optionLabel == null) MahjongUi.control(g, getX(), getY(), width, height, active, isHovered(), isFocused(), false, false);
        else {
            g.fill(getX(), getY(), getX() + width, getY() + height, active && isHovered() ? MahjongUi.HOVER : MahjongUi.INPUT);
            if (active && isFocused()) g.outline(getX(), getY(), width, height, MahjongUi.ACCENT);
        }
        int start = getX() + 5, end = getX() + width - 5;
        int handle = start + (int) Math.round(value * (end - start));
        g.fill(start, getY() + height - 5, end, getY() + height - 3, MahjongUi.INPUT);
        g.fill(start, getY() + height - 5, handle, getY() + height - 3, active ? MahjongUi.ACCENT : MahjongUi.DISABLED);
        g.fill(handle - 2, getY() + height - 7, handle + 2, getY() + height - 2, active ? MahjongUi.TEXT : MahjongUi.DISABLED);
        var font = Minecraft.getInstance().font;
        int color = active ? MahjongUi.TEXT : MahjongUi.DISABLED;
        if (optionLabel == null) MahjongUi.text(g, font, getMessage(), getX() + 6, getY() + 3, width - 12, color, true);
        else {
            int valueWidth = font.width(optionValue);
            MahjongUi.text(g, font, optionLabel, getX() + 6, getY() + 2, width - valueWidth - 20, color, false);
            MahjongUi.text(g, font, optionValue, getX() + width - valueWidth - 6, getY() + 2, valueWidth, color, false);
        }
    }
}
