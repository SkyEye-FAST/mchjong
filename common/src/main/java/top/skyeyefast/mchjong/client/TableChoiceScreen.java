package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.ArrayList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicit choices with native focus, replacing hidden click-to-cycle values. */
final class TableChoiceScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final List<Component> choices;
    private final int selected;
    private final IntConsumer choose;
    private final IntFunction<Component> unavailable;
    private final List<MahjongButton> buttons = new ArrayList<>();
    private SettingsLayout layout;
    private int page, pages, rows;

    TableChoiceScreen(Screen parent, Component title, List<Component> choices, int selected, IntConsumer choose) {
        this(parent, title, choices, selected, choose, index -> null);
    }
    TableChoiceScreen(Screen parent, Component title, List<Component> choices, int selected, IntConsumer choose,
                      IntFunction<Component> unavailable) {
        super(title);
        this.parent = parent;
        this.choices = List.copyOf(choices);
        this.selected = selected;
        this.choose = choose;
        this.unavailable = unavailable;
    }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override protected void init() {
        clearWidgets(); buttons.clear();
        layout = SettingsLayout.of(width, height, 0, choices.size() * 24);
        rows = Math.max(1, (layout.paging() - layout.contentTop()) / 24);
        pages = Math.max(1, (choices.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        for (int index = page * rows; index < Math.min(choices.size(), (page + 1) * rows); index++) {
            int value = index;
            buttons.add(addRenderableWidget(RoomLobbyControls.button(choices.get(index), layout.left(),
                layout.contentTop() + (index % rows) * 24, layout.span(), () -> {
                    if (unavailable.apply(value) == null) { choose.accept(value); onClose(); }
                }).selected(index == selected)));
        }
        if (pages > 1) {
            var previous = addRenderableWidget(RoomLobbyControls.button(Component.literal("‹"), layout.left(), layout.paging(), 28, () -> { page--; init(); }));
            previous.active = page > 0;
            var next = addRenderableWidget(RoomLobbyControls.button(Component.literal("›"), layout.left() + layout.span() - 28, layout.paging(), 28, () -> { page++; init(); }));
            next.active = page + 1 < pages;
        }
        addRenderableWidget(RoomLobbyControls.button(Component.translatable("gui.cancel"), layout.left(), layout.footer(), layout.span(), this::onClose));
        tick();
    }
    @Override public void tick() {
        for (int i = 0; i < buttons.size(); i++) {
            var reason = unavailable.apply(page * rows + i);
            buttons.get(i).active = reason == null;
            buttons.get(i).setTooltip(Tooltip.create(reason == null ? choices.get(page * rows + i) : reason));
        }
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout.paint(graphics, font, title);
        if (pages > 1) graphics.drawCenteredString(font, (page + 1) + " / " + pages, width / 2, layout.paging() + 6, MahjongUi.MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
