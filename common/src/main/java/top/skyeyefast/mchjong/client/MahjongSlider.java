package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public abstract class MahjongSlider extends AbstractSliderButton {
    protected MahjongSlider(int x, int y, int width, int height, Component message, double value) {
        super(x, y, width, height, message, value);
    }

    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        MahjongUi.control(g, getX(), getY(), width, height, active, isHovered(), isFocused(), false, false);
        int start = getX() + 5, end = getX() + width - 5;
        int handle = start + (int) Math.round(value * (end - start));
        g.fill(start, getY() + height - 5, end, getY() + height - 3, MahjongUi.INPUT);
        g.fill(start, getY() + height - 5, handle, getY() + height - 3, active ? MahjongUi.ACCENT : MahjongUi.DISABLED);
        g.fill(handle - 2, getY() + height - 7, handle + 2, getY() + height - 2, active ? MahjongUi.TEXT : MahjongUi.DISABLED);
        MahjongUi.text(g, Minecraft.getInstance().font, getMessage(), getX() + 6, getY() + 3, width - 12,
            active ? MahjongUi.TEXT : MahjongUi.DISABLED, true);
    }
}
