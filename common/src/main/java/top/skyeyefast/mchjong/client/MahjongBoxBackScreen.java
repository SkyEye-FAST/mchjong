package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.skyeyefast.mchjong.network.BoxBackPayload;
import top.skyeyefast.mchjong.network.PayloadPackets;

/** Selects the decorative back for the physical tiles stored in the open box. */
public final class MahjongBoxBackScreen extends MahjongBoxPresetScreen {
    private int page;

    public MahjongBoxBackScreen(MahjongBoxScreen parent) {
        super(Component.translatable("box.mchjong.back_title"), parent);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        int span = Math.min(304, width - 24), left = (width - span) / 2;
        var choices = TileBackPresets.choices();
        int rows = Math.max(1, Math.min(5, (height - 110) / 25));
        page = Math.clamp(page, 0, (choices.size() - 1) / rows);
        int top = 52;
        for (int i = 0; i < rows && page * rows + i < choices.size(); i++) {
            Identifier id = choices.get(page * rows + i);
            var button = MahjongButton.create(TileBackPresets.label(id), ignored -> {
                minecraft.getConnection().send(PayloadPackets.serverbound(new BoxBackPayload(parent.boxMenu().containerId, id)));
                onClose();
            }).bounds(left + 38, top + i * 25, span - 42, 20).build().selected(id.equals(parent.backPreset()));
            button.active = parent.boxMenu().canChooseBack(id);
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

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, 304);
        MahjongUi.text(graphics, font, title, (width - Math.min(304, width - 24)) / 2 + 10, 16,
            Math.min(304, width - 24) - 20, MahjongUi.TEXT, false);
        var choices = TileBackPresets.choices();
        int rows = Math.max(1, Math.min(5, (height - 110) / 25));
        int left = (width - Math.min(304, width - 24)) / 2;
        for (int i = 0; i < rows && page * rows + i < choices.size(); i++) {
            Identifier id = choices.get(page * rows + i);
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TileBackPresets.texture(id), left + 7, 52 + i * 25, 0, 0,
                14, 20, 256, 384, 256, 384);
        }
        if (choices.size() > rows) graphics.centeredText(font,
            (page + 1) + " / " + ((choices.size() - 1) / rows + 1), width / 2, height - 51, MahjongUi.MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

}
