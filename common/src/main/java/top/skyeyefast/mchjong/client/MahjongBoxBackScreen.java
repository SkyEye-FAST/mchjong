package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Selects the decorative back for the physical tiles stored in the open box. */
public final class MahjongBoxBackScreen extends MahjongBoxPresetScreen {
    private int page;
    private PresetSource source = PresetSource.SERVER;

    public MahjongBoxBackScreen(MahjongBoxScreen parent) {
        super(Component.translatable("box.mchjong.back_title"), parent);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        int span = Math.min(304, width - 24), left = (width - span) / 2;
        source.tabs(left, 34, span, value -> { source = value; page = 0; init(); }).forEach(this::addRenderableWidget);
        var choices = choices();
        int rows = Math.max(1, Math.min(5, (height - 128) / 25));
        page = Math.clamp(page, 0, Math.max(0, (choices.size() - 1) / rows));
        int top = 62;
        for (int i = 0; i < rows && page * rows + i < choices.size(); i++) {
            ResourceLocation id = choices.get(page * rows + i);
            var button = MahjongButton.create(TileBackPresets.source(id).caption(TileBackPresets.label(id)), ignored -> {
                parent.selectBack(id);
                onClose();
            }).bounds(left + 38, top + i * 25, span - 42, 20)
                .tooltip(PresetSource.tooltip(TileBackPresets.label(id), id)).build().selected(id.equals(parent.backPreset()));
            addRenderableWidget(button);
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

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, 304);
        MahjongUi.text(graphics, font, title, (width - Math.min(304, width - 24)) / 2 + 10, 16,
            Math.min(304, width - 24) - 20, MahjongUi.TEXT, false);
        var choices = choices();
        int rows = Math.max(1, Math.min(5, (height - 128) / 25));
        int left = (width - Math.min(304, width - 24)) / 2;
        for (int i = 0; i < rows && page * rows + i < choices.size(); i++) {
            ResourceLocation id = choices.get(page * rows + i);
            graphics.blit(TileBackPresets.texture(id), left + 7, 62 + i * 25, 14, 20,
                0, 0, 256, 384, 256, 384);
        }
        if (choices.size() > rows) graphics.drawCenteredString(font,
            (page + 1) + " / " + ((choices.size() - 1) / rows + 1), width / 2, height - 51, MahjongUi.MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private java.util.List<ResourceLocation> choices() {
        return TileBackPresets.choices().stream().filter(id -> source.includes(TileBackPresets.source(id))).toList();
    }

}
