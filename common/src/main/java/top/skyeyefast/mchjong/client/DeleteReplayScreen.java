package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.ReplayMatch;

/** An explicit, reversible-at-the-dialog confirmation; no record changes before the delete button. */
public final class DeleteReplayScreen extends Screen {
    private final ReplayBrowserScreen parent;
    private final ReplayMatch.Header match;

    public DeleteReplayScreen(ReplayBrowserScreen parent, ReplayMatch.Header match) {
        super(Component.translatable("replay.mchjong.delete_title"));
        this.parent = parent;
        this.match = match;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}

    @Override protected void init() {
        int span = Math.min(440, width - 32), left = (width - span) / 2;
        var cancel = addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(left, height - 38, (span - 8) / 2, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("replay.mchjong.delete"), ignored -> {
            minecraft.setScreen(parent);
            parent.delete(match.id());
        }).bounds(left + (span + 8) / 2, height - 38, (span - 8) / 2, 20).build());
        setInitialFocus(cancel);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int span = Math.min(440, width - 32), left = (width - span) / 2;
        MahjongUi.backdrop(graphics, width, height, 470);
        MahjongUi.text(graphics, font, title, left, 18, span, MahjongUi.TEXT, true);
        MahjongUi.text(graphics, font, Component.literal(String.join(" · ", match.names())), left, 44, span, MahjongUi.ACCENT, true);
        MahjongUi.text(graphics, font, Component.literal(match.id().toString()), left, 60, span, MahjongUi.MUTED, true);
        int y = 86;
        for (var line : font.split(Component.translatable("replay.mchjong.delete_note"), span)) {
            graphics.drawString(font, line, left, y, MahjongUi.TEXT, false);
            y += 11;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
