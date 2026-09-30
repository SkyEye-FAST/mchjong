package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TableSeatPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

public final class SichuanLobbyScreen extends Screen {
    private final BlockPos pos;
    private boolean pending;
    private int page;
    public SichuanLobbyScreen(BlockPos pos) {
        super(Component.translatable("variant.mchjong.sichuan"));
        this.pos = pos.immutable();
    }
    public BlockPos tablePos() { return pos; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    private MahjongTableBlockEntity table() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table ? table : null;
    }
    @Override protected void init() { rebuild(); }
    public void receivedView() { pending = false; rebuild(); }
    private void rebuild() {
        clearWidgets();
        var table = table();
        if (table == null || table.clientTableRoom() == null) return;
        var room = table.clientTableRoom();
        int span = Math.min(440, width - 20), left = (width - span) / 2;
        int actionTop = 132;
        if (room.exitVote() != null) {
            TableExitControls.voteButtons(pos, room, width, height).forEach(this::addRenderableWidget);
            return;
        }
        if (room.lobby()) {
            RoomLobbyControls.variantButtons(pos, room, left, 32, span, true).forEach(this::addRenderableWidget);
            var clock = RoomLobbyControls.button(Component.translatable("ui.mchjong.clock_settings"), left, 58, span,
                () -> minecraft.setScreen(new TableClockScreen(this, table.clientSichuanTimeControl())));
            clock.active = !pending && room.viewerSeat() >= 0 && room.viewerSeat() == room.host();
            addRenderableWidget(clock);
        }
        var labels = new ArrayList<Component>();
        var commands = new ArrayList<Runnable>();
        for (int index = 0; index < room.actions().size(); index++) {
            int actionIndex = index;
            var action = room.actions().get(index);
            Component label = Component.translatable("action.mchjong." + action.type().name().toLowerCase(Locale.ROOT));
            if (action.type() == RoomAction.Type.TRANSFER_HOST)
                label = Component.translatable("room.mchjong.transfer_host", room.seats().get(action.arguments().getFirst()).participant().name());
            else if (!action.arguments().isEmpty()) label = label.copy().append(" " + (action.arguments().getFirst() + 1));
            labels.add(label); commands.add(() -> RoomLobbyControls.send(pos, room, actionIndex));
        }
        int rows = Math.max(1, (height - actionTop - 34) / 23), capacity = rows * 3;
        int pages = Math.max(1, (labels.size() + capacity - 1) / capacity);
        page = Math.min(page, pages - 1);
        int cell = (span - 8) / 3;
        for (int offset = 0; offset < capacity && page * capacity + offset < labels.size(); offset++) {
            int index = page * capacity + offset;
            var button = RoomLobbyControls.button(labels.get(index), left + offset % 3 * (cell + 4),
                actionTop + offset / 3 * 23, cell, () -> { if (!pending) { pending = true; commands.get(index).run(); rebuild(); } });
            button.active = !pending; addRenderableWidget(button);
        }
        if (pages > 1) {
            addRenderableWidget(RoomLobbyControls.button(Component.literal("◀"), left, height - 27, 40,
                () -> { page = Math.floorMod(page - 1, pages); rebuild(); }));
            addRenderableWidget(RoomLobbyControls.button(Component.literal("▶"), left + span - 40, height - 27, 40,
                () -> { page = (page + 1) % pages; rebuild(); }));
        }
        if (TableSettings.get().autoSeat && room.viewerSeat() >= 0 && minecraft.player != null && minecraft.getConnection() != null
            && !(minecraft.player.getVehicle() instanceof SeatEntity seat && seat.tablePos().equals(pos) && seat.seat() == room.viewerSeat()))
            minecraft.getConnection().send(PayloadPackets.serverbound(new TableSeatPayload(pos, room.tableId())));
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, Math.min(464, width));
        graphics.drawCenteredString(font, title, width / 2, 12, MahjongUi.TEXT);
        var table = table();
        if (table != null && table.clientTableRoom() != null) {
            TableRoomView room = table.clientTableRoom();
            for (int seat = 0; seat < 4; seat++) {
                var participant = room.seats().get(seat).participant();
                Component text = Component.literal((seat + 1) + ". ").append(participant.id() == null
                    ? Component.translatable("room.mchjong.empty") : Component.literal(participant.name()));
                if (participant.ready()) text = text.copy().append(" ✓");
                graphics.drawString(font, text, (width - Math.min(440, width - 20)) / 2, 84 + seat * 11, MahjongUi.TEXT);
            }
        }
        if (table != null && table.clientTableRoom() != null) TableExitControls.renderVote(graphics, font, table.clientTableRoom(), width, height);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
