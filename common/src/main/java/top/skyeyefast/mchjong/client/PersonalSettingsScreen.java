package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Client-local settings entry point for loader mod lists. */
public final class PersonalSettingsScreen extends Screen {
    private final Screen parent;

    public PersonalSettingsScreen(Screen parent) {
        super(Component.translatable("settings.mchjong.title"));
        this.parent = parent;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        int span = Math.min(440, width - 24), left = (width - span) / 2;
        int top = Math.max(65, (height - 76) / 2);
        addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.preferences"), ignored ->
            minecraft.setScreen(new TableSettingsScreen(this))).bounds(left, top, span, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("settings.mchjong.personal_presets"), ignored ->
            minecraft.setScreen(new PersonalPresetsScreen(this))).bounds(left, top + 26, span, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left, height - 30, span, 20).build().primary());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, 464);
        MahjongUi.text(graphics, font, title, 12, 16, width - 24, MahjongUi.ACCENT, true);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
