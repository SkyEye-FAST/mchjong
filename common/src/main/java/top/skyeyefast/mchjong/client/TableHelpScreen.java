package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Compact, paginated text help using ordinary focus and return navigation. */
public class TableHelpScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final Supplier<List<Component>> contents;
    private List<Component> shown = List.of();
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private int page;
    private int rows;
    private int left;
    private int top;
    private int span;
    private int panelHeight;

    public TableHelpScreen(Screen parent, Component title, Supplier<List<Component>> contents) {
        super(title); this.parent = parent; this.contents = contents;
    }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public void tick() { if (!contents.get().equals(shown)) init(); }

    @Override protected void init() {
        clearWidgets();
        span = Math.min(360, width - 32); panelHeight = Math.min(220, height - 32);
        left = (width - span) / 2; top = (height - panelHeight) / 2;
        shown = List.copyOf(contents.get()); lines.clear();
        for (var text : shown) { lines.addAll(font.split(text, span - 24)); lines.add(FormattedCharSequence.EMPTY); }
        panelHeight = Math.min(panelHeight, Math.max(100, lines.size() * 12 + 64));
        top = (height - panelHeight) / 2;
        rows = Math.max(1, (panelHeight - 64) / 12);
        int pages = Math.max(1, (lines.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        if (pages > 1) {
            var previous = addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> { page--; init(); }).bounds(left + 12, top + panelHeight - 28, 24, 20).build());
            previous.active = page > 0;
            var next = addRenderableWidget(MahjongButton.create(Component.literal("›"), ignored -> { page++; init(); }).bounds(left + span - 36, top + panelHeight - 28, 24, 20).build());
            next.active = page + 1 < pages;
        }
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.back"), ignored -> onClose()).bounds(left + (span - 100) / 2, top + panelHeight - 28, 100, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.panel(graphics, left, top, span, panelHeight);
        MahjongUi.text(graphics, font, title, left + 12, top + 10, span - 24, MahjongUi.ACCENT, false);
        for (int row = 0; row < rows && page * rows + row < lines.size(); row++)
            graphics.drawString(font, lines.get(page * rows + row), left + 12, top + 30 + row * 12, MahjongUi.TEXT, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void updateNarrationState(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        super.updateNarrationState(output);
        for (var text : shown) output.add(net.minecraft.client.gui.narration.NarratedElementType.HINT, text);
    }
}
