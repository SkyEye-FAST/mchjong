package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Shared native focus target and hand-hint mark. */
abstract class TableHintsButton extends MahjongButton {
    protected int scale = 1;

    TableHintsButton() {
        super(0, 0, 20, 16, Component.translatable("hints.mchjong.button"), ignored -> {});
        visible = active = false;
        setTooltip(null);
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(getX(), getY());
        graphics.pose().scale(scale, scale);
        int centerX = 10, centerY = 8;
        int edge = isHoveredOrFocused() ? MahjongUi.ACCENT : MahjongUi.EDGE;
        for (int dy = -7; dy <= 7; dy++) {
            int half = 7 - Math.abs(dy);
            graphics.fill(centerX - half, centerY + dy, centerX + half + 1, centerY + dy + 1, edge);
            if (half > 1) graphics.fill(centerX - half + 1, centerY + dy, centerX + half, centerY + dy + 1, MahjongUi.PANEL);
        }
        graphics.fill(centerX, centerY - 3, centerX + 1, centerY + 1, MahjongUi.ACCENT);
        graphics.fill(centerX, centerY + 3, centerX + 1, centerY + 4, MahjongUi.ACCENT);
        graphics.pose().popMatrix();
    }
}
