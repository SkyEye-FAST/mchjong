package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.PlayerPresence;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Participant presence and ownership. Bot controls live on the room's seat cards. */
public final class RiichiSeatsScreen extends Screen {
    private final RiichiTableScreen parent;
    private long revision = -1;

    public RiichiSeatsScreen(RiichiTableScreen parent) {
        super(Component.translatable("room.mchjong.participants"));
        this.parent = parent;
    }
    public RiichiTableScreen tableScreen() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        var room = parent.room();
        if (room == null) return;
        revision = room.revision();
        int span = Math.min(440, width - 24), left = (width - span) / 2;
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
                    if (room.lobby()) parent.sendRoom(room, index);
                    else if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("mchjong host " + player.name());
                };
            } else {
                label = player.bot() ? botName(room, parent.roomSettings().externalBots(), seat)
                    : Component.translatable(player.id() != null ? presenceKey(state.presence()) : "room.mchjong.empty");
                hint = label;
                enabled = false;
                action = () -> {};
            }
            var button = MahjongButton.create(label, ignored -> action.run())
                .bounds(left + span - 100, 42 + seat * 37, 100, 22).tooltip(Tooltip.create(hint)).build();
            button.active = enabled;
            addRenderableWidget(button);
        }
        int leave = RoomLobbyControls.find(room, RoomAction.Type.LEAVE_ROOM, List.of());
        if (leave >= 0) addRenderableWidget(MahjongButton.create(Component.translatable("action.mchjong.leave_room"), ignored -> parent.sendRoom(room, leave))
            .bounds(left, height - 54, span, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left, height - 30, span, 20).build().primary());
    }

    static int find(RiichiView view, RiichiAction.Type type, List<Integer> arguments) {
        for (int index = 0; index < view.actions().size(); index++) {
            var action = view.actions().get(index);
            if (action.type() == type && action.tiles().equals(arguments)) return index;
        }
        return -1;
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

    static Component position(RiichiTableScreen table, int seat) {
        var pos = TableGeometry.stool(table.tablePos(), seat);
        return Component.translatable("room.mchjong.position", wind(seat), pos.getX(), pos.getY(), pos.getZ());
    }

    @Override public void tick() {
        var room = parent.room();
        if (room == null || room.viewerSeat() < 0) { onClose(); return; }
        if (room.revision() != revision) init();
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, 464);
        MahjongUi.text(graphics, font, title, 12, 16, width - 24, MahjongUi.TEXT, true);
        TableRoomView room = parent.room();
        if (room != null) {
            int span = Math.min(440, width - 24), left = (width - span) / 2;
            for (int seat = 0; seat < room.seats().size(); seat++) {
                var state = room.seats().get(seat);
                var player = state.participant();
                Component label = Component.translatable("room.mchjong.member", seat + 1, RiichiTableScreen.playerName(room, seat));
                Component status = wind(state.wind()).copy().append("  ").append(player.id() == null
                    ? Component.translatable("room.mchjong.left_room") : player.bot() ? Component.translatable("room.mchjong.bot")
                    : presence(state.presence()));
                int inset = PlayerPortrait.draw(graphics, player, left, 42 + seat * 37, 10);
                int nameColor = !player.bot() && state.presence() == PlayerPresence.DISCONNECTED ? MahjongUi.NEGATIVE : MahjongUi.TEXT;
                MahjongUi.text(graphics, font, label, left + inset, 43 + seat * 37, span - 108 - inset, nameColor, false);
                MahjongUi.text(graphics, font, status, left, 56 + seat * 37, span - 108, MahjongUi.MUTED, false);
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
