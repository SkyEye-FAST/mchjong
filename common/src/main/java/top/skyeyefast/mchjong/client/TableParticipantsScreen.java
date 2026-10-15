package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.PlayerPresence;
import top.skyeyefast.mchjong.engine.TableRoomView;

/** Shared participant presence and ownership while inspecting room settings. */
public final class TableParticipantsScreen extends Screen implements TableChildScreen {
    private final Screen parent;
    private final net.minecraft.core.BlockPos pos;
    private long revision = -1;
    private int left, top, span, panelHeight, pitch;

    public TableParticipantsScreen(Screen parent, net.minecraft.core.BlockPos pos) {
        super(Component.translatable("room.mchjong.participants"));
        this.parent = parent;
        this.pos = pos.immutable();
    }
    @Override public Screen parent() { return parent; }
    private top.skyeyefast.mchjong.world.MahjongTableBlockEntity table() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof top.skyeyefast.mchjong.world.MahjongTableBlockEntity table ? table : null;
    }
    private TableRoomView room() { return table() == null ? null : table().clientTableRoom(); }
    private List<top.skyeyefast.mchjong.engine.ExternalBot> externalBots() {
        return table().clientRiichiSettings() == null ? List.of() : table().clientRiichiSettings().externalBots();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        var room = room();
        if (room == null) return;
        revision = room.revision();
        span = Math.min(380, width - 48);
        panelHeight = Math.min(88 + room.seats().size() * 32, height - 32);
        left = (width - span) / 2;
        top = (height - panelHeight) / 2;
        pitch = (panelHeight - 88) / room.seats().size();
        boolean host = room.viewerSeat() >= 0 && room.viewerSeat() == room.host() && room.exitVote() == null;
        for (int seat = 0; seat < room.seats().size(); seat++) {
            var state = room.seats().get(seat);
            var player = state.participant();
            if (player.id() == null || player.bot() || seat == room.host() || !host) continue;
            int index = RoomLobbyControls.find(room, RoomAction.Type.TRANSFER_HOST, List.of(seat));
            var transferLabel = Component.translatable("room.mchjong.transfer");
            int transferWidth = RoomLobbyControls.buttonWidth(transferLabel, room.lobby() ? 78 : 100);
            var button = MahjongButton.create(transferLabel, ignored -> {
                if (room.lobby()) RoomLobbyControls.send(pos, room, index);
                else if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("mchjong host " + player.name());
            }).bounds(left + span - controlsWidth(room, seat), top + 38 + seat * pitch, transferWidth, 20)
                .tooltip(Tooltip.create(Component.translatable("room.mchjong.transfer_host", player.name()))).build();
            button.active = room.lobby() ? index >= 0 : state.presence() != PlayerPresence.DISCONNECTED
                && room.seats().get(room.viewerSeat()).presence() == PlayerPresence.SEATED;
            if (!room.lobby() && room.seats().get(room.viewerSeat()).presence() != PlayerPresence.SEATED)
                button.setTooltip(Tooltip.create(Component.translatable("message.mchjong.seat_required")));
            addRenderableWidget(button);
            int kick = RoomLobbyControls.find(room, RoomAction.Type.KICK, List.of(seat));
            if (kick >= 0) {
                var removeLabel = Component.translatable("room.mchjong.kick");
                int removeWidth = RoomLobbyControls.buttonWidth(removeLabel, 78);
                var remove = RoomLobbyControls.button(removeLabel, left + span - removeWidth,
                    top + 38 + seat * pitch, removeWidth, () -> RoomLobbyControls.send(pos, room, kick));
                remove.setTooltip(Tooltip.create(Component.translatable("room.mchjong.kick_player", player.name())));
                addRenderableWidget(remove);
            }
        }
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.back"), ignored -> onClose())
            .bounds(left, top + panelHeight - 26, span, 20).build().primary());
    }

    private int controlsWidth(TableRoomView room, int seat) {
        var player = room.seats().get(seat).participant();
        if (room.viewerSeat() < 0 || room.viewerSeat() != room.host() || room.exitVote() != null
            || player.id() == null || player.bot() || seat == room.host()) return 0;
        int width = RoomLobbyControls.buttonWidth(Component.translatable("room.mchjong.transfer"), room.lobby() ? 78 : 100);
        if (RoomLobbyControls.find(room, RoomAction.Type.KICK, List.of(seat)) >= 0)
            width += 4 + RoomLobbyControls.buttonWidth(Component.translatable("room.mchjong.kick"), 78);
        return width;
    }

    @Override public void tick() {
        var room = room();
        if (room == null || room.viewerSeat() < 0) { onClose(); return; }
        if (room.revision() != revision) init();
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        MahjongUi.panel(graphics, left - 6, top, span + 12, panelHeight);
        MahjongUi.text(graphics, font, title, left + 7, top + 12, span - 14, MahjongUi.TEXT, false);
        TableRoomView room = room();
        if (room != null) {
            for (int seat = 0; seat < room.seats().size(); seat++) {
                var state = room.seats().get(seat);
                var player = state.participant();
                Component label = RoomMembers.name(room, seat);
                Component status = RoomMembers.status(room, externalBots(), seat);
                int textWidth = span - controlsWidth(room, seat) - 8;
                int inset = PlayerPortrait.draw(graphics, player, left, top + 38 + seat * pitch, 10);
                int nameColor = !player.bot() && state.presence() == PlayerPresence.DISCONNECTED ? MahjongUi.NEGATIVE : MahjongUi.TEXT;
                MahjongUi.text(graphics, font, label, left + inset, top + 39 + seat * pitch, textWidth - inset, nameColor, false);
                MahjongUi.text(graphics, font, status, left, top + 52 + seat * pitch, textWidth, MahjongUi.MUTED, false);
                if (x >= left && x < left + textWidth && y >= top + 36 + seat * pitch && y < top + 36 + (seat + 1) * pitch)
                    setTooltipForNextRenderPass(status.getString().isEmpty() ? label : label.copy().append("\n").append(status));
            }
        }
        super.render(graphics, x, y, partialTick);
    }

    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
