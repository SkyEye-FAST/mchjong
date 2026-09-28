package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Shows several sample tiles for each face preset before printing the box contents. */
public final class MahjongBoxFaceScreen extends MahjongBoxPresetScreen {
    private static final int[] SAMPLES = {
        Tile.id(0, 0, false), Tile.id(13, 0, false), Tile.id(26, 0, false), Tile.id(Tile.EAST, 0, false)
    };
    private int page;
    private PresetSource source = PresetSource.SERVER;

    public MahjongBoxFaceScreen(MahjongBoxScreen parent) {
        super(Component.translatable("box.mchjong.preset"), parent);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics) {}

    @Override protected void init() {
        clearWidgets();
        int span = Math.min(304, width - 24), left = (width - span) / 2;
        source.tabs(left, 34, span, value -> { source = value; page = 0; init(); }).forEach(this::addRenderableWidget);
        var choices = choices();
        int rows = Math.max(1, Math.min(5, (height - 128) / 25));
        page = net.minecraft.util.Mth.clamp(page, 0, Math.max(0, (choices.size() - 1) / rows));
        int top = 62;
        for (int i = 0; i < rows && page * rows + i < choices.size(); i++) {
            TileFacePreset preset = choices.get(page * rows + i);
            var button = MahjongButton.create(TileFacePresets.source(preset).caption(TileFacePresets.label(preset)), ignored -> {
                parent.selectFace(preset);
                onClose();
            }).bounds(left + 76, top + i * 25, span - 80, 20)
                .tooltip(PresetSource.tooltip(TileFacePresets.label(preset), preset.id())).build().selected(preset.equals(parent.facePreset()));
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
        int span = Math.min(304, width - 24), left = (width - span) / 2;
        MahjongUi.text(graphics, font, Component.translatable("item.mchjong.mahjong_box").append(" › ").append(title),
            left + 10, 16, span - 20, MahjongUi.ACCENT, false);
        var choices = choices();
        int rows = Math.max(1, Math.min(5, (height - 128) / 25));
        for (int i = 0; i < rows && page * rows + i < choices.size(); i++) {
            TileFacePreset preset = choices.get(page * rows + i);
            for (int sample = 0; sample < SAMPLES.length; sample++)
                TileGui.tile(graphics, SAMPLES[sample], left + 7 + sample * 17, 62 + i * 25, 13,
                    false, false, false, preset);
        }
        if (choices.size() > rows) graphics.drawCenteredString(font,
            (page + 1) + " / " + ((choices.size() - 1) / rows + 1), width / 2, height - 51, MahjongUi.MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private java.util.List<TileFacePreset> choices() {
        return TileFacePresets.choices().stream().filter(id -> source.includes(TileFacePresets.source(id))).toList();
    }

}
