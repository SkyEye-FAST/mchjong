package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.SichuanAction;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.SichuanActionPayload;
import top.skyeyefast.mchjong.network.TableSeatPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** A native, recipient-safe control surface for Sichuan rooms and hands. */
public final class SichuanScreen extends Screen {
    private final BlockPos pos;
    private boolean pending;
    private int page;
    public SichuanScreen(BlockPos pos) {
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
        if (room.lobby()) {
            RoomLobbyControls.variantButtons(pos, room, left, 32, span, true).forEach(this::addRenderableWidget);
            var clock = RoomLobbyControls.button(Component.translatable("ui.mchjong.clock_settings"), left, 58, span,
                () -> minecraft.setScreen(new TableClockScreen(this, table.clientSichuanTimeControl())));
            clock.active = !pending && room.viewerSeat() >= 0 && room.viewerSeat() == room.host();
            addRenderableWidget(clock);
        }
        var labels = new ArrayList<Component>();
        var commands = new ArrayList<Runnable>();
        var session = table.clientSichuanView();
        if (session != null) for (int index = 0; index < session.game().actions().size(); index++) {
            int actionIndex = index;
            labels.add(label(session.game().actions().get(index)));
            commands.add(() -> {
                if (minecraft.getConnection() != null) minecraft.getConnection().send(PayloadPackets.serverbound(
                    new SichuanActionPayload(pos, session.tableId(), session.incarnation(), session.game().decision(), actionIndex)));
            });
        }
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
    private static Component label(SichuanAction action) {
        var text = Component.translatable("sichuan.mchjong.action." + action.type().name().toLowerCase(Locale.ROOT));
        if (action.type() == SichuanAction.Type.VOID_SUIT) return text.append(" ").append(suit(action.suit()));
        if (!action.tiles().isEmpty()) return text.append(" " + Tile.notation(Tile.kind(action.tiles().getFirst())));
        return text;
    }
    private static Component suit(int value) { return Component.translatable("sichuan.mchjong.suit." + value); }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MahjongUi.backdrop(graphics, width, height, Math.min(464, width));
        graphics.drawCenteredString(font, title, width / 2, 12, MahjongUi.TEXT);
        var table = table();
        if (table != null && table.clientTableRoom() != null) {
            TableRoomView room = table.clientTableRoom();
            var session = table.clientSichuanView();
            for (int seat = 0; seat < 4; seat++) {
                var participant = room.seats().get(seat).participant();
                Component text = Component.literal((seat + 1) + ". ").append(participant.id() == null
                    ? Component.translatable("room.mchjong.empty") : Component.literal(participant.name()));
                if (session != null) {
                    var player = session.game().seats().get(seat);
                    if (player.voidSuit() >= 0) text = text.copy().append(" · ").append(suit(player.voidSuit()));
                    if (player.won()) text = text.copy().append(" · ").append(Component.translatable("sichuan.mchjong.action.win"));
                    if (session.game().result() != null) text = text.copy().append(" · " + session.game().result().deltas().get(seat));
                } else if (participant.ready()) text = text.copy().append(" ✓");
                graphics.drawString(font, text, (width - Math.min(440, width - 20)) / 2, 84 + seat * 11, MahjongUi.TEXT);
            }
            if (session != null) {
                graphics.drawCenteredString(font, Component.translatable("sichuan.mchjong.phase." + session.game().phase().name().toLowerCase(Locale.ROOT))
                    .append(" · " + session.game().wall().remaining()), width / 2, 32, MahjongUi.MUTED);
                int viewer = session.game().viewerSeat();
                if (viewer >= 0) {
                    String hand = String.join(" ", Tile.notations(session.game().seats().get(viewer).hand()));
                    MahjongUi.text(graphics, font, Component.literal(hand), 10, 54, width - 20, MahjongUi.TEXT, true);
                }
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
