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
            Component label;
            Component hint;
            Runnable action;
            boolean enabled;
            if (seat == room.host() || player.id() != null && !player.bot() && state.presence() == PlayerPresence.SEATED) {
                label = Component.translatable(seat == room.host() ? "room.mchjong.host.short" : "room.mchjong.transfer");
                hint = Component.translatable("room.mchjong.transfer_host", player.name());
                int index = RoomLobbyControls.find(room, RoomAction.Type.TRANSFER_HOST, List.of(seat));
                enabled = host && seat != room.host() && (!room.lobby() || index >= 0);
                action = () -> {
                    if (room.lobby()) RoomLobbyControls.send(pos, room, index);
                    else if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("mchjong host " + player.name());
                };
            } else {
                label = player.bot() ? botName(room, externalBots(), seat)
                    : Component.translatable(player.id() != null ? presenceKey(state.presence()) : "room.mchjong.empty");
                hint = label;
                enabled = false;
                action = () -> {};
            }
            var button = MahjongButton.create(label, ignored -> action.run())
                .bounds(left + span - 100, top + 38 + seat * pitch, 100, 22).tooltip(Tooltip.create(hint)).build();
            button.active = enabled;
            addRenderableWidget(button);
        }
        int leave = RoomLobbyControls.find(room, RoomAction.Type.LEAVE_ROOM, List.of());
        if (leave >= 0) addRenderableWidget(MahjongButton.create(Component.translatable("action.mchjong.leave_room"), ignored -> RoomLobbyControls.send(pos, room, leave))
            .bounds(left, top + panelHeight - 50, span, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left, top + panelHeight - 26, span, 20).build().primary());
    }

    static Component botName(TableRoomView room, List<top.skyeyefast.mchjong.engine.ExternalBot> externalBots, int seat) {
        var state = room.seats().get(seat);
        if (state.participant().externalBotId() != null) return externalBots.stream()
            .filter(bot -> bot.id().equals(state.participant().externalBotId()))
            .findFirst().<Component>map(bot -> Component.literal(bot.name()))
            .orElse(Component.literal(state.participant().externalBotId()));
        return Component.translatable(state.participant().difficulty().translationKey());
    }

    static Component wind(int wind) {
        return Component.translatable(wind < 0 ? "room.mchjong.wind_unknown"
            : "wind.mchjong." + new String[]{"east", "south", "west", "north"}[wind]);
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
                Component label = Component.translatable("room.mchjong.member", seat + 1, RiichiTableScreen.playerName(room, seat));
                Component status = wind(state.wind()).copy().append("  ").append(player.id() == null
                    ? Component.translatable("room.mchjong.left_room") : player.bot() ? Component.translatable("room.mchjong.bot")
                    : presence(state.presence()));
                int inset = PlayerPortrait.draw(graphics, player, left, top + 38 + seat * pitch, 10);
                int nameColor = !player.bot() && state.presence() == PlayerPresence.DISCONNECTED ? MahjongUi.NEGATIVE : MahjongUi.TEXT;
                MahjongUi.text(graphics, font, label, left + inset, top + 39 + seat * pitch, span - 108 - inset, nameColor, false);
                MahjongUi.text(graphics, font, status, left, top + 52 + seat * pitch, span - 108, MahjongUi.MUTED, false);
            }
        }
        super.render(graphics, x, y, partialTick);
    }

    static Component presence(PlayerPresence presence) {
        if (presence == null) return Component.translatable("room.mchjong.empty");
        return Component.translatable(presenceKey(presence));
    }

    private static String presenceKey(PlayerPresence presence) {
        return switch (presence) {
            case SEATED -> "room.mchjong.present";
            case AWAY -> "room.mchjong.away";
            case DISCONNECTED -> "room.mchjong.disconnected";
        };
    }

    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
