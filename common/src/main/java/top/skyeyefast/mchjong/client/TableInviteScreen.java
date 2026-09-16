package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.Comparator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** Uses the connection's online roster; server-side commands resolve and authorize the target. */
public final class TableInviteScreen extends Screen {
    private final TableScreen parent;
    private int page;
    private int pages = 1;

    public TableInviteScreen(TableScreen parent) {
        super(Component.translatable("ui.mchjong.invite"));
        this.parent = parent;
    }
    public TableScreen tableScreen() { return parent; }
    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        clearWidgets();
        if (minecraft.getConnection() == null || minecraft.player == null) return;
        List<PlayerInfo> players = minecraft.getConnection().getOnlinePlayers().stream()
            .filter(info -> !info.getProfile().getId().equals(minecraft.player.getUUID()))
            .sorted(Comparator.comparing(info -> info.getProfile().getName(), String.CASE_INSENSITIVE_ORDER)).toList();
        int rows = Math.max(1, (height - 116) / 24);
        pages = Math.max(1, (players.size() + rows - 1) / rows);
        page = Math.clamp(page, 0, pages - 1);
        int span = Math.min(320, width - 24), left = (width - span) / 2;
        for (int index = page * rows; index < Math.min(players.size(), (page + 1) * rows); index++) {
            var profile = players.get(index).getProfile();
            addRenderableWidget(Button.builder(Component.literal(profile.getName()), ignored -> {
                if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("mchjong invite " + profile.getId());
                onClose();
            }).bounds(left, 56 + (index % rows) * 24, span, 20).build());
        }
        var previous = addRenderableWidget(Button.builder(Component.literal("<"), ignored -> { page--; init(); })
            .bounds(left, height - 56, 32, 20).build());
        previous.active = page > 0;
        var next = addRenderableWidget(Button.builder(Component.literal(">"), ignored -> { page++; init(); })
            .bounds(left + span - 32, height - 56, 32, 20).build());
        next.active = page + 1 < pages;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left, height - 30, span, 20).build());
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xf017272c);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xfff0dec1);
        graphics.drawCenteredString(font, Component.translatable("ui.mchjong.invite_hint"), width / 2, 34, 0xffd1e4d9);
        graphics.drawCenteredString(font, (page + 1) + " / " + pages, width / 2, height - 51, 0xffd1e4d9);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
