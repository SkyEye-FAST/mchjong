package top.skyeyefast.mchjong.client;

import java.io.IOException;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** A player's local cosmetic choices, opened from the room settings' Personal scope. */
public final class PersonalPresetsScreen extends Screen {
    private final TableOptionsScreen parent;
    private int page;
    private boolean saveFailed;

    public PersonalPresetsScreen(TableOptionsScreen parent) {
        super(Component.translatable("settings.mchjong.personal_presets"));
        this.parent = parent;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        int span = Math.min(440, width - 24), left = (width - span) / 2;
        var choices = RiichiStickPresets.choices();
        int rows = Math.max(1, (height - 132) / 25);
        page = Math.clamp(page, 0, (choices.size() - 1) / rows);
        for (int i = 0; i < rows && page * rows + i < choices.size(); i++) {
            ResourceLocation id = choices.get(page * rows + i);
            addRenderableWidget(MahjongButton.create(RiichiStickPresets.label(id), ignored -> choose(id))
                .bounds(left + 102, 78 + i * 25, span - 102, 20).build()
                .selected(id.equals(TableSettings.get().riichiStickPreset)));
        }
        int navY = height - 56;
        var previous = MahjongButton.create(Component.literal("<"), ignored -> { page--; init(); })
            .bounds(left, navY, 30, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        var next = MahjongButton.create(Component.literal(">"), ignored -> { page++; init(); })
            .bounds(left + span - 30, navY, 30, 20).build();
        next.active = (page + 1) * rows < choices.size();
        addRenderableWidget(next);
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left, height - 30, span, 20).build().primary());
    }

    private void choose(ResourceLocation id) {
        var settings = TableSettings.get();
        settings.riichiStickPreset = id;
        RiichiStickPresets.sendChoice();
        try { settings.save(TableSettings.configPath()); saveFailed = false; }
        catch (IOException failure) { saveFailed = true; }
        init();
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, 464);
        int span = Math.min(440, width - 24), left = (width - span) / 2;
        MahjongUi.text(graphics, font, title, left + 10, 16, span - 20, MahjongUi.TEXT, false);
        MahjongUi.text(graphics, font, Component.translatable("settings.mchjong.stick_preset"),
            left + 10, 53, span - 20, MahjongUi.MUTED, false);
        var choices = RiichiStickPresets.choices();
        int rows = Math.max(1, (height - 132) / 25);
        for (int i = 0; i < rows && page * rows + i < choices.size(); i++)
            graphics.blit(RiichiStickPresets.texture(choices.get(page * rows + i)),
                left + 10, 85 + i * 25, 80, 7, 0, 0, 384, 32, 384, 32);
        if (choices.size() > rows) graphics.drawCenteredString(font,
            (page + 1) + " / " + ((choices.size() - 1) / rows + 1), width / 2, height - 51, MahjongUi.MUTED);
        if (saveFailed) graphics.drawCenteredString(font, Component.translatable("settings.mchjong.save_failed"),
            width / 2, height - 76, MahjongUi.NEGATIVE);
        super.render(graphics, x, y, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
