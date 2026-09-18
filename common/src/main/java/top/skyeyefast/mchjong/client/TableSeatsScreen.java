package top.skyeyefast.mchjong.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.BotDifficulty;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RoomView;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Per-seat ownership and bot controls. Every lobby choice is a server-issued action. */
public final class TableSeatsScreen extends Screen {
    private final TableScreen parent;
    private long revision = -1;

    public TableSeatsScreen(TableScreen parent) {
        super(Component.translatable("room.mchjong.participants"));
        this.parent = parent;
    }
    public TableScreen tableScreen() { return parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}

    @Override protected void init() {
        clearWidgets();
        var view = parent.view();
        var room = parent.room();
        if (view == null || room == null) return;
        revision = view.revision();
        int span = Math.min(440, width - 24), left = (width - span) / 2;
        boolean host = view.viewerSeat() >= 0 && view.viewerSeat() == room.host() && view.exitVote() == null;
        for (int seat = 0; seat < view.seats().size(); seat++) {
            var player = view.seats().get(seat);
            var state = room.seats().get(seat);
            Component label;
            Component hint;
            Runnable action;
            boolean enabled;
            if (seat == room.host() || player.occupied() && !player.bot() && state.present()) {
                label = Component.translatable(seat == room.host() ? "room.mchjong.host_short" : "room.mchjong.transfer");
                hint = Component.translatable("room.mchjong.transfer_host", player.name());
                int index = find(view, Action.Type.TRANSFER_HOST, List.of(seat));
                enabled = host && seat != room.host() && (view.phase() != Game.Phase.LOBBY || index >= 0);
                action = () -> {
                    if (view.phase() == Game.Phase.LOBBY) parent.send(view, index);
                    else if (minecraft.getConnection() != null) minecraft.getConnection().sendCommand("mchjong host " + player.name());
                };
            } else {
                BotDifficulty next = !player.bot() ? BotDifficulty.EASY : switch (state.difficulty()) {
                    case EASY -> BotDifficulty.NORMAL;
                    case NORMAL -> BotDifficulty.HARD;
                    case HARD -> null;
                };
                label = Component.translatable(player.bot() ? state.difficulty().translationKey()
                    : player.occupied() ? "room.mchjong.absent" : "room.mchjong.empty").append(" ›");
                hint = Component.translatable("room.mchjong.bot_next", next == null ? Component.translatable("room.mchjong.empty")
                    : Component.translatable(next.translationKey()));
                int index = next == null ? find(view, Action.Type.REMOVE_BOT, List.of(seat))
                    : find(view, Action.Type.SET_BOT, List.of(seat, next.ordinal()));
                enabled = host && view.phase() == Game.Phase.LOBBY && index >= 0;
                action = () -> parent.send(view, index);
            }
            var button = MahjongButton.create(label, ignored -> action.run())
                .bounds(left + span - 100, 42 + seat * 37, 100, 22).tooltip(Tooltip.create(hint)).build();
            button.active = enabled;
            addRenderableWidget(button);
        }
        int leave = find(view, Action.Type.LEAVE_ROOM, List.of());
        if (leave >= 0) addRenderableWidget(MahjongButton.create(Component.translatable("action.mchjong.leave_room"), ignored -> parent.send(view, leave))
            .bounds(left, height - 54, span, 20).build());
        addRenderableWidget(MahjongButton.create(Component.translatable("gui.done"), ignored -> onClose())
            .bounds(left, height - 30, span, 20).build().primary());
    }

    static int find(TableView view, Action.Type type, List<Integer> arguments) {
        for (int index = 0; index < view.actions().size(); index++) {
            var action = view.actions().get(index);
            if (action.type() == type && action.tiles().equals(arguments)) return index;
        }
        return -1;
    }

    static Component wind(int wind) {
        return Component.translatable(wind < 0 ? "room.mchjong.wind_unknown"
            : "wind.mchjong." + new String[]{"east", "south", "west", "north"}[wind]);
    }

    static Component position(TableScreen table, int seat) {
        var pos = TableGeometry.stool(table.tablePos(), seat);
        return Component.translatable("room.mchjong.position", wind(seat), pos.getX(), pos.getY(), pos.getZ());
    }

    @Override public void tick() {
        var view = parent.view();
        if (view == null || view.viewerSeat() < 0) { onClose(); return; }
        if (view.revision() != revision) init();
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, 464);
        MahjongUi.text(graphics, font, title, 12, 16, width - 24, MahjongUi.TEXT, true);
        var view = parent.view();
        RoomView room = parent.room();
        if (view != null && room != null) {
            int span = Math.min(440, width - 24), left = (width - span) / 2;
            for (int seat = 0; seat < view.seats().size(); seat++) {
                var state = room.seats().get(seat);
                var player = view.seats().get(seat);
                Component label = Component.translatable("room.mchjong.member", seat + 1, TableScreen.playerName(view, seat));
                Component status = wind(state.wind()).copy().append(" · ").append(Component.translatable(!player.occupied()
                    ? "room.mchjong.empty" : player.bot() ? "room.mchjong.bot"
                    : state.present() ? "room.mchjong.present" : "room.mchjong.absent"));
                int inset = PlayerPortrait.draw(graphics, player, left, 42 + seat * 37, 10);
                MahjongUi.text(graphics, font, label, left + inset, 43 + seat * 37, span - 108 - inset, MahjongUi.TEXT, false);
                MahjongUi.text(graphics, font, status, left, 56 + seat * 37, span - 108, MahjongUi.MUTED, false);
            }
        }
        super.render(graphics, x, y, partialTick);
    }
    @Override public void onClose() { minecraft.setScreen(minecraft.level == null ? null : parent); }
}
