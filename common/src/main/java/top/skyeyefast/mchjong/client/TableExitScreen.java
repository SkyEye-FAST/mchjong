package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;

/** Confirms a host's room closure or a sole human's match ending without a vote. */
final class TableExitScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final BlockPos pos;
    private final TableRoomView room;
    private boolean answered;

    TableExitScreen(Screen parent, BlockPos pos, TableRoomView room) {
        super(Component.translatable(room.lobby() ? "room.mchjong.dissolve" : "ui.mchjong.exit"));
        this.parent = parent;
        this.pos = pos.immutable();
        this.room = room;
    }

    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics) {}

    @Override protected void init() {
        int span = Math.min(320, width - 24), left = (width - span) / 2;
        int y = Math.min(height - 36, height / 2 + 26);
        var cancel = addRenderableWidget(MahjongButton.create(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(left, y, (span - 6) / 2, 20).build());
        addRenderableWidget(MahjongButton.create(title, ignored -> confirm())
            .bounds(left + (span + 6) / 2, y, (span - 6) / 2, 20).build());
        setInitialFocus(cancel);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int span = Math.min(320, width - 24), left = (width - span) / 2;
        MahjongUi.backdrop(graphics, width, height, 470);
        MahjongUi.text(graphics, font, title, left, height / 2 - 38, span, MahjongUi.TEXT, true);
        int y = height / 2 - 18;
        for (var line : font.split(Component.translatable(room.lobby() ? "ui.mchjong.exit_confirm_note" : "ui.mchjong.exit_confirm_match_note"), span)) {
            graphics.drawString(font, line, left, y, MahjongUi.MUTED, false);
            y += 11;
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void confirm() {
        if (answered) return;
        answered = true;
        TableExitControls.send(pos, room, TableSessionControlPayload.Operation.REQUEST_EXIT, room.decision(), false);
        minecraft.setScreen(parent);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
