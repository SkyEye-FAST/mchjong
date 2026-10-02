package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Client-local settings entry point for loader mod lists. */
public final class PersonalSettingsScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private int left, top, span;

    public PersonalSettingsScreen(Screen parent) {
        super(Component.translatable("settings.mchjong.title"));
        this.parent = parent;
    }

    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        span = Math.min(300, width - 48);
        left = (width - span) / 2;
        top = (height - 130) / 2;
        addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.preferences"), ignored ->
            minecraft.setScreen(new TableSettingsScreen(this))).bounds(left, top + 38, span, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.personal_presets"), ignored ->
            minecraft.setScreen(new PersonalPresetsScreen(this))).bounds(left, top + 64, span, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left, top + 104, span, 20).build().primary());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.panel(graphics, left - 6, top, span + 12, 130);
        MahjongUi.text(graphics, font, title, left + 7, top + 12, span - 14, MahjongUi.TEXT, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
