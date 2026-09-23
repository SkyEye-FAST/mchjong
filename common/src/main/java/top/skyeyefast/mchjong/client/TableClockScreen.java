package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TimeControl;

/** The host edits server-owned lobby settings, not a client-side timeout. */
public final class TableClockScreen extends Screen {
    private final TableScreen parent;
    private final TimeControl initial;
    private EditBox reserve;
    private EditBox move;
    private Button apply;

    public TableClockScreen(TableScreen parent, TimeControl initial) {
        super(Component.translatable("ui.mchjong.clock_settings"));
        this.parent = parent;
        this.initial = initial;
    }
    public TableScreen tableScreen() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override protected void init() {
        String reserveValue = reserve == null ? Integer.toString(initial.reserveSeconds()) : reserve.getValue();
        String moveValue = move == null ? Integer.toString(initial.moveSeconds()) : move.getValue();
        int left = width / 2 - 100;
        reserve = field(left, 72, "ui.mchjong.reserve_time", reserveValue);
        move = field(left, 118, "ui.mchjong.move_time", moveValue);
        apply = addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> {
            TimeControl control = control();
            if (control != null && minecraft.getConnection() != null) {
                minecraft.getConnection().sendCommand("mchjong clock " + control.reserveSeconds() + " " + control.moveSeconds());
                onClose();
            }
        }).bounds(left, height - 30, 96, 20).build().primary());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(left + 104, height - 30, 96, 20).build());
    }
    private EditBox field(int x, int y, String key, String value) {
        var box = new MahjongEditBox(font, x, y, 200, 20, Component.translatable(key));
        box.setMaxLength(3);
        box.setFilter(text -> text.matches("[0-9]{0,3}"));
        box.setValue(value);
        return addRenderableWidget(box);
    }
    private TimeControl control() {
        try { return new TimeControl(Integer.parseInt(reserve.getValue()), Integer.parseInt(move.getValue())); }
        catch (IllegalArgumentException failure) { return null; }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, 280);
        MahjongUi.text(graphics, font, title, 12, 20, width - 24, MahjongUi.TEXT, true);
        graphics.text(font, Component.translatable("ui.mchjong.reserve_time"), width / 2 - 100, 58, MahjongUi.MUTED);
        graphics.text(font, Component.translatable("ui.mchjong.move_time"), width / 2 - 100, 104, MahjongUi.MUTED);
        apply.active = control() != null;
        super.extractRenderState(graphics, x, y, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
