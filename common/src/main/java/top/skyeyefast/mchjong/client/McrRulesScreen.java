package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Fixed MCR match contract with the existing rule explanation. */
final class McrRulesScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private SettingsLayout layout;
    private List<FormattedCharSequence> lines = List.of();
    McrRulesScreen(Screen parent) { super(Component.translatable("rules.mchjong.title")); this.parent = parent; }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float partial) {}
    @Override protected void init() {
        clearWidgets();
        layout = SettingsLayout.of(width, height, 0, 110);
        lines = font.split(Component.translatable("rules.mchjong.mcr_summary"), layout.span());
        addRenderableWidget(RuleHelp.setting(this, Component.translatable("variant.mchjong.mcr"),
            RuleHelp.pageDescription("manual.mchjong.entry.mcr_tiles.tiles"), "manual.mchjong.entry.mcr_tiles.tiles",
            layout.left() + layout.span() - 20, layout.paging()));
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.back"), ignored -> onClose())
            .bounds(layout.left(), layout.footer(), layout.span(), 20).build());
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float partial) {
        layout.paint(g, font, title);
        for (int i = 0; i < lines.size(); i++) g.text(font, lines.get(i), layout.left(), layout.contentTop() + i * 12, MahjongUi.TEXT, false);
        super.extractRenderState(g, x, y, partial);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
