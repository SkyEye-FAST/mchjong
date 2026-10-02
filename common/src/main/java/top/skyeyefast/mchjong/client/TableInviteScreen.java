package top.skyeyefast.mchjong.client;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** Uses the connection's online roster; server-side commands resolve and authorize the target. */
public final class TableInviteScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final net.minecraft.core.BlockPos pos;
    private final Map<String, MahjongButton> invitations = new HashMap<>();
    private int page;
    private int pages = 1;
    private int left, top, span, panelHeight;

    public TableInviteScreen(Screen parent, net.minecraft.core.BlockPos pos) {
        super(Component.translatable("ui.mchjong.invite"));
        this.parent = parent;
        this.pos = pos.immutable();
    }
    @Override public Screen parent() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        invitations.clear();
        if (minecraft.getConnection() == null || minecraft.player == null) return;
        List<PlayerInfo> players = minecraft.getConnection().getOnlinePlayers().stream()
            .filter(info -> !info.getProfile().id().equals(minecraft.player.getUUID()))
            .sorted(Comparator.comparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER)).toList();
        span = Math.min(320, width - 48);
        panelHeight = Math.min(238, height - 48);
        left = (width - span) / 2;
        top = (height - panelHeight) / 2;
        int rows = Math.max(1, (panelHeight - 96) / 24);
        pages = Math.max(1, (players.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        for (int index = page * rows; index < Math.min(players.size(), (page + 1) * rows); index++) {
            var profile = players.get(index).getProfile();
            var button = addRenderableWidget(MahjongButton.create(Component.literal(profile.name()), ignored -> {
                if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("mchjong invite " + profile.id());
                onClose();
            }).bounds(left, top + 36 + (index % rows) * 24, span, 20).build());
            invitations.put(profile.name(), button);
        }
        var previous = addRenderableWidget(MahjongButton.create(Component.literal("<"), ignored -> { page--; init(); })
            .bounds(left, top + panelHeight - 52, 32, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(MahjongButton.create(Component.literal(">"), ignored -> { page++; init(); })
            .bounds(left + span - 32, top + panelHeight - 52, 32, 20).build());
        next.active = page + 1 < pages;
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left, top + panelHeight - 26, span, 20).build());
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var room = minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof top.skyeyefast.mchjong.world.MahjongTableBlockEntity table ? table.clientTableRoom() : null;
        if (room != null) invitations.forEach((name, button) -> button.active = room.seats().stream()
            .noneMatch(seat -> seat.participant().id() != null && !seat.participant().bot()
                && seat.participant().name().equals(name)));
        MahjongUi.panel(graphics, left - 6, top, span + 12, panelHeight);
        MahjongUi.text(graphics, font, title, left + 7, top + 12, span - 14, MahjongUi.TEXT, false);
        if (pages > 1) graphics.centeredText(font, (page + 1) + " / " + pages, width / 2, top + panelHeight - 46, MahjongUi.MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
