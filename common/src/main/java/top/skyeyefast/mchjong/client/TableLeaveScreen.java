package top.skyeyefast.mchjong.client;

import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TableControlPayload;

/** The last player to dismount decides whether the paused match remains at the table. */
public final class TableLeaveScreen extends Screen {
    private final BlockPos pos;
    private final UUID tableId;
    private final long decision;
    private boolean answered;

    public TableLeaveScreen(BlockPos pos, UUID tableId, long decision) {
        super(Component.translatable("ui.mchjong.leave_match_title"));
        this.pos = pos.immutable();
        this.tableId = tableId;
        this.decision = decision;
    }

    public boolean matches(BlockPos pos, UUID tableId) { return this.pos.equals(pos) && this.tableId.equals(tableId); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override protected void init() {
        int span = Math.min(320, width - 24), left = (width - span) / 2;
        int y = Math.min(height - 36, height / 2 + 26);
        var retain = addRenderableWidget(MahjongButton.create(Component.translatable("ui.mchjong.leave_match_keep"), ignored -> answer(true))
            .bounds(left, y, (span - 6) / 2, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("ui.mchjong.leave_match_end"), ignored -> answer(false))
            .bounds(left + (span + 6) / 2, y, (span - 6) / 2, 20).build());
        setInitialFocus(retain);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int span = Math.min(320, width - 24), left = (width - span) / 2;
        MahjongUi.backdrop(graphics, width, height, 470);
        MahjongUi.text(graphics, font, title, left, height / 2 - 38, span, MahjongUi.TEXT, true);
        int y = height / 2 - 18;
        for (var line : font.split(Component.translatable("ui.mchjong.leave_match_note"), span)) {
            graphics.text(font, line, left, y, MahjongUi.MUTED, false);
            y += 11;
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void answer(boolean retain) {
        if (answered) return;
        answered = true;
        if (minecraft.getConnection() != null) minecraft.getConnection().send(PayloadPackets.serverbound(
            new TableControlPayload(pos, tableId, TableControlPayload.Operation.RESOLVE_LEAVE, decision, retain)));
        minecraft.setScreen(null);
    }

    @Override public void onClose() { answer(true); }
}
