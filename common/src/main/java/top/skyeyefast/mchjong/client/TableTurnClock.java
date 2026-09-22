package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TimeControl;

/** A quiet, right-aligned move allowance and reserve, with a localized accessible label. */
final class TableTurnClock extends AbstractWidget {
    private TimeControl.Clock clock = new TimeControl.Clock(0, 0, false);
    private int scale = 1;

    TableTurnClock() {
        super(0, 0, 96, 24, Component.empty());
        visible = false;
    }

    void update(TimeControl.Clock clock, int right, int bottom, int scale) {
        this.clock = clock;
        this.scale = scale;
        visible = clock.active();
        setWidth(96 * scale);
        height = 24 * scale;
        setX(right - getWidth());
        setY(bottom - getHeight());
        Component label = Component.translatable("ui.mchjong.clock", clock.moveSeconds(), clock.reserveSeconds());
        if (!label.equals(getMessage())) {
            setMessage(label);
            setTooltip(Tooltip.create(label));
        }
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        boolean usingReserve = clock.moveTicks() == 0;
        String move = Integer.toString(usingReserve ? clock.reserveSeconds() : clock.moveSeconds());
        String reserve = usingReserve || clock.reserveTicks() == 0 ? "" : "+" + clock.reserveSeconds();
        boolean urgent = clock.moveTicks() + clock.reserveTicks() <= 100;
        graphics.pose().pushPose();
        graphics.pose().translate(getX(), getY(), 0);
        graphics.pose().scale(scale, scale, 1);
        int reserveX = 96 - font.width(reserve);
        graphics.drawString(font, reserve, reserveX, 11,
            urgent ? MahjongUi.NEGATIVE : MahjongUi.ACCENT, true);
        graphics.pose().translate(reserveX - (reserve.isEmpty() ? 0 : 4) - font.width(move) * 2, 2, 0);
        graphics.pose().scale(2, 2, 1);
        graphics.drawString(font, move, 0, 0, urgent ? MahjongUi.NEGATIVE : usingReserve ? MahjongUi.ACCENT : MahjongUi.TEXT, true);
        graphics.pose().popPose();
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
