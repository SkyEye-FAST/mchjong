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
    private final TileDiagram example;
    private final Runnable manual;
    private List<Component> shown = List.of();
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private int page;
    private int rows;
    private int left;
    private int top;
    private int span;
    private int panelHeight;
    private int exampleHeight;

    public TableHelpScreen(Screen parent, Component title, Supplier<List<Component>> contents) {
        this(parent, title, contents, null, null);
    }
    public TableHelpScreen(Screen parent, Component title, Supplier<List<Component>> contents, TileDiagram example, Runnable manual) {
        super(title); this.parent = parent; this.contents = contents; this.example = example; this.manual = manual;
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
        for (var text : shown) {
            var paragraph = font.split(text, span - 24);
            if (paragraph.size() > 1 && text.getString().endsWith("。") && font.width(paragraph.getLast()) <= font.width("。"))
                paragraph = font.split(text, span - 48);
            lines.addAll(paragraph); lines.add(FormattedCharSequence.EMPTY);
        }
        exampleHeight = example == null ? 0 : 58;
        panelHeight = Math.min(panelHeight, Math.max(100, lines.size() * 12 + 64 + exampleHeight));
        top = (height - panelHeight) / 2;
        rows = Math.max(1, (panelHeight - 64 - exampleHeight) / 12);
        int pages = Math.max(1, (lines.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        if (pages > 1) {
            var previous = addRenderableWidget(MahjongButton.create(Component.literal("‹"), ignored -> { page--; init(); }).bounds(left + 12, top + panelHeight - 28, 24, 20).build());
            previous.active = page > 0;
            var next = addRenderableWidget(MahjongButton.create(Component.literal("›"), ignored -> { page++; init(); }).bounds(left + span - 36, top + panelHeight - 28, 24, 20).build());
            next.active = page + 1 < pages;
        }
        int buttonWidth = manual == null ? 100 : Math.min(100, (span - 80) / 2);
        int buttonLeft = left + (span - buttonWidth * (manual == null ? 1 : 2) - (manual == null ? 0 : 4)) / 2;
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.back"), ignored -> onClose()).bounds(buttonLeft, top + panelHeight - 28, buttonWidth, 20).build());
        if (manual != null) addRenderableWidget(MahjongButton.create(Component.translatable("rules.mchjong.manual"), ignored -> manual.run())
            .bounds(buttonLeft + buttonWidth + 4, top + panelHeight - 28, buttonWidth, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.panel(graphics, left, top, span, panelHeight);
        MahjongUi.text(graphics, font, title, left + 12, top + 10, span - 24, MahjongUi.ACCENT, false);
        for (int row = 0; row < rows && page * rows + row < lines.size(); row++)
            graphics.drawString(font, lines.get(page * rows + row), left + 12, top + 30 + row * 12, MahjongUi.TEXT, false);
        if (example != null) {
            int y = top + panelHeight - 32 - exampleHeight;
            MahjongUi.text(graphics, font, Component.translatable("rules.mchjong.example"), left + 12, y, span - 24, MahjongUi.MUTED, false);
            int tileWidth = Math.min(22, (span - 24 - example.parts().size()) / example.parts().size());
            int drawnWidth = example.width(0, example.parts().size(), tileWidth);
            example.render(graphics, left + (span - drawnWidth) / 2, y + 22, tileWidth, 0, example.parts().size());
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void updateNarrationState(net.minecraft.client.gui.narration.NarrationElementOutput output) {
        super.updateNarrationState(output);
        for (var text : shown) output.add(net.minecraft.client.gui.narration.NarratedElementType.HINT, text);
    }
}
