package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.network.BoxPrintPayload;
import top.skyeyefast.mchjong.network.PayloadPackets;

/** Shows several sample tiles for each face preset before printing the box contents. */
public final class MahjongBoxFaceScreen extends MahjongBoxPresetScreen {
    private static final int[] SAMPLES = {
        Tile.id(0, 0, false), Tile.id(13, 0, false), Tile.id(26, 0, false), Tile.id(Tile.EAST, 0, false)
    };
    private int page;

    public MahjongBoxFaceScreen(MahjongBoxScreen parent) {
        super(Component.translatable("box.mchjong.preset"), parent);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        int span = Math.min(304, width - 24), left = (width - span) / 2;
        var choices = TileFacePresets.choices();
        int rows = Math.max(1, Math.min(5, (height - 110) / 25));
        page = Math.clamp(page, 0, (choices.size() - 1) / rows);
        int top = 52;
        for (int i = 0; i < rows && page * rows + i < choices.size(); i++) {
            TileFacePreset preset = choices.get(page * rows + i);
            var button = MahjongButton.create(TileFacePresets.label(preset), ignored -> {
                minecraft.getConnection().send(PayloadPackets.serverbound(new BoxPrintPayload(parent.boxMenu().containerId, preset)));
                onClose();
            }).bounds(left + 76, top + i * 25, span - 80, 20).build().selected(preset.equals(parent.facePreset()));
            button.active = parent.boxMenu().canEngrave(preset);
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
        MahjongUi.text(graphics, font, title, left + 10, 16, span - 20, MahjongUi.TEXT, false);
        var choices = TileFacePresets.choices();
        int rows = Math.max(1, Math.min(5, (height - 110) / 25));
        for (int i = 0; i < rows && page * rows + i < choices.size(); i++) {
            TileFacePreset preset = choices.get(page * rows + i);
            for (int sample = 0; sample < SAMPLES.length; sample++)
                TileGui.tile(graphics, SAMPLES[sample], left + 7 + sample * 17, 52 + i * 25, 13,
                    false, false, false, preset);
        }
        if (choices.size() > rows) graphics.drawCenteredString(font,
            (page + 1) + " / " + ((choices.size() - 1) / rows + 1), width / 2, height - 51, MahjongUi.MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

}
