package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit choices with native focus, replacing hidden click-to-cycle values. */
final class TableChoiceScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final List<Component> choices;
    private final int selected;
    private final IntConsumer choose;
    private int left, top, span, panelHeight;

    TableChoiceScreen(Screen parent, Component title, List<Component> choices, int selected, IntConsumer choose) {
        super(title);
        this.parent = parent;
        this.choices = List.copyOf(choices);
        this.selected = selected;
        this.choose = choose;
    }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override protected void init() {
        span = Math.min(300, width - 48);
        panelHeight = choices.size() * 24 + 64;
        left = (width - span) / 2;
        top = (height - panelHeight) / 2;
        for (int index = 0; index < choices.size(); index++) {
            int value = index;
            addRenderableWidget(RoomLobbyControls.button(choices.get(index), left, top + 32 + index * 24, span, () -> {
                choose.accept(value); onClose();
            }).selected(index == selected));
        }
        addRenderableWidget(RoomLobbyControls.button(Component.translatable("gui.cancel"), left, top + panelHeight - 26, span, this::onClose));
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.panel(graphics, left - 6, top, span + 12, panelHeight);
        MahjongUi.text(graphics, font, title, left + 7, top + 12, span - 14, MahjongUi.TEXT, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
