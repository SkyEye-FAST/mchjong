package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TableSeatPayload;
import top.skyeyefast.mchjong.network.TableSessionControlPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** MCR preparation uses the shared room snapshot without a Riichi game projection. */
public final class McrLobbyScreen extends Screen {
    private final BlockPos pos;
    private boolean pending;

    public McrLobbyScreen(BlockPos pos) {
        super(Component.translatable("mcr.mchjong.title"));
        this.pos = pos.immutable();
    }

    public BlockPos tablePos() { return pos; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private TableRoomView room() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table
            ? table.clientTableRoom() : null;
    }

    public void receivedView() { pending = false; rebuild(); }
    @Override protected void init() { rebuild(); }

    private int panelTop() { return (height - Math.min(246, height - 12)) / 2; }

    private void rebuild() {
        clearWidgets();
        TableRoomView room = room();
        if (room == null) return;
        int x = (width - 230) / 2;
        int top = panelTop();
        if (room.viewerSeat() >= 0 && room.viewerSeat() == room.host()) {
            var dissolve = RoomLobbyControls.button(Component.translatable("room.mchjong.dissolve"),
                x + 152, top + 7, 78, () -> TableExitControls.send(pos, room,
                    TableSessionControlPayload.Operation.REQUEST_EXIT, room.decision(), false));
            dissolve.active = !pending;
            addRenderableWidget(dissolve);
        }
        var mcr = RoomLobbyControls.variantButton(pos, room, MahjongVariant.MCR, x, top + 32, 113, true);
        mcr.setTooltip(Tooltip.create(Component.translatable("mcr.mchjong.stock_hint")));
        addRenderableWidget(mcr);
        var riichi = RoomLobbyControls.variantButton(pos, room, MahjongVariant.RIICHI, x + 117, top + 32, 113, true);
        addRenderableWidget(riichi);
        if (minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table) {
            var clock = RoomLobbyControls.button(Component.translatable("ui.mchjong.clock_settings"), x, top + 78, 230,
                () -> minecraft.setScreen(new TableClockScreen(this, table.clientMcrTimeControl())));
            clock.active = !pending && room.viewerSeat() >= 0 && room.viewerSeat() == room.host();
            clock.setTooltip(Tooltip.create(Component.translatable("ui.mchjong.clock",
                table.clientMcrTimeControl().moveSeconds(), table.clientMcrTimeControl().reserveSeconds())));
            addRenderableWidget(clock);
        }
        int actionTop = top + 154;
        for (int index = 0; index < room.actions().size(); index++) {
            int actionIndex = index;
            RoomAction action = room.actions().get(index);
            Component label = Component.translatable("action.mchjong." + action.type().name().toLowerCase(java.util.Locale.ROOT));
            if (action.type() == RoomAction.Type.SET_BOT)
                label = Component.translatable("room.mchjong.add_bot").append(" " + (action.arguments().getFirst() + 1));
            if (action.type() == RoomAction.Type.REMOVE_BOT)
                label = label.copy().append(" " + (action.arguments().getFirst() + 1));
            if (action.type() == RoomAction.Type.DRAW_WIND)
                label = label.copy().append(" " + (action.arguments().getFirst() + 1));
            if (action.type() == RoomAction.Type.TRANSFER_HOST)
                label = Component.translatable("room.mchjong.transfer_host",
                    room.seats().get(action.arguments().getFirst()).participant().name());
            var button = RoomLobbyControls.button(label, x + index % 2 * 117,
                actionTop + index / 2 * 23, 113, () -> send(room, actionIndex));
            button.active = !pending;
            addRenderableWidget(button);
        }
        if (TableSettings.get().autoSeat && room.viewerSeat() >= 0 && minecraft.player != null
            && !(minecraft.player.getVehicle() instanceof SeatEntity seat
            && seat.tablePos().equals(pos) && seat.seat() == room.viewerSeat()) && minecraft.getConnection() != null)
            minecraft.getConnection().send(PayloadPackets.serverbound(new TableSeatPayload(pos, room.tableId())));
    }

    private void send(TableRoomView room, int index) {
        if (pending || minecraft.getConnection() == null) return;
        pending = true;
        RoomLobbyControls.send(pos, room, index);
        rebuild();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int top = panelTop();
        graphics.fill(0, 0, width, height, MahjongUi.BACKDROP);
        MahjongUi.panel(graphics, (width - 246) / 2, top, 246, Math.min(246, height - 12));
        graphics.fill((width - 230) / 2, top + 1, (width + 230) / 2, top + 2, MahjongUi.ACCENT);
        TableRoomView room = room();
        graphics.drawCenteredString(font, title, width / 2, top + 12, MahjongUi.TEXT);
        if (room != null) {
            var stock = font.split(Component.translatable("mcr.mchjong.stock_hint"), 226);
            for (int line = 0; line < Math.min(2, stock.size()); line++)
                graphics.drawCenteredString(font, stock.get(line), width / 2, top + 59 + line * 10, MahjongUi.MUTED);
            for (int seat = 0; seat < room.seats().size(); seat++) {
                var entry = room.seats().get(seat);
                var member = entry.participant();
                Component name = member.id() == null ? Component.translatable("room.mchjong.empty") : Component.literal(member.name());
                graphics.drawCenteredString(font, Component.literal((seat + 1) + ". ").append(name)
                    .append(member.ready() ? " ✓" : ""), width / 2, top + 103 + seat * 12, MahjongUi.TEXT);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
