package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TimeControl;

/** The host edits server-owned lobby settings, not a client-side timeout. */
public final class TableClockScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final TimeControl initial;
    private EditBox reserve;
    private EditBox move;
    private Button apply;
    private int top;

    public TableClockScreen(Screen parent, TimeControl initial) {
        super(Component.translatable("ui.mchjong.clock_settings"));
        this.parent = parent;
        this.initial = initial;
    }
    public RiichiTableScreen tableScreen() { return TableChildScreen.root(parent) instanceof RiichiTableScreen table ? table : null; }
    public McrLobbyScreen mcrLobby() { return TableChildScreen.root(parent) instanceof McrLobbyScreen lobby ? lobby : null; }
    public SichuanLobbyScreen sichuanScreen() { return TableChildScreen.root(parent) instanceof SichuanLobbyScreen screen ? screen : null; }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics) {}

    @Override protected void init() {
        String reserveValue = reserve == null ? Integer.toString(initial.reserveSeconds()) : reserve.getValue();
        String moveValue = move == null ? Integer.toString(initial.moveSeconds()) : move.getValue();
        int left = width / 2 - 100;
        top = (height - 174) / 2;
        reserve = field(left, top + 48, "ui.mchjong.reserve_time", reserveValue);
        move = field(left, top + 94, "ui.mchjong.move_time", moveValue);
        apply = addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> {
            TimeControl control = control();
            if (control != null && minecraft.getConnection() != null) {
                minecraft.getConnection().sendCommand("mchjong clock " + control.reserveSeconds() + " " + control.moveSeconds());
                onClose();
            }
        }).bounds(left, top + 148, 96, 20).build().primary());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(left + 104, top + 148, 96, 20).build());
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
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        MahjongUi.panel(graphics, width / 2 - 110, top, 220, 174);
        MahjongUi.text(graphics, font, title, width / 2 - 100, top + 12, 200, MahjongUi.TEXT, false);
        graphics.drawString(font, Component.translatable("ui.mchjong.reserve_time"), width / 2 - 100, top + 34, MahjongUi.MUTED);
        graphics.drawString(font, Component.translatable("ui.mchjong.move_time"), width / 2 - 100, top + 80, MahjongUi.MUTED);
        apply.active = control() != null;
        super.render(graphics, x, y, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
