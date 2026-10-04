package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TableRoomView;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TableSeatPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.SeatEntity;

/** Rule-specific snapshot adapter for the shared preparation interface. */
public final class TaiwanLobbyScreen extends Screen {
    private final BlockPos pos;
    private final RoomLobby lobby;
    public TaiwanLobbyScreen(BlockPos pos) {
        super(Component.translatable("variant.mchjong.taiwan"));
        this.pos = pos.immutable();
        lobby = new RoomLobby(this, this.pos, this::rebuild);
    }
    public BlockPos tablePos() { return pos; }
    public TableRoomView room() { return table() == null ? null : table().clientTableRoom(); }
    private MahjongTableBlockEntity table() {
        return minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof MahjongTableBlockEntity table ? table : null;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override protected void init() { rebuild(); }
    public void receivedView() { lobby.receivedView(); rebuild(); }
    private void rebuild() {
        clearWidgets();
        var room = room();
        if (room != null) lobby.build(room, width, height).forEach(this::addRenderableWidget);
        if (room != null && room.seating() == top.skyeyefast.mchjong.engine.RoomSeating.Stage.POSITIONING
            && TableSettings.get().autoSeat && room.viewerSeat() >= 0 && minecraft.player != null && minecraft.getConnection() != null
            && !(minecraft.player.getVehicle() instanceof SeatEntity seat && seat.tablePos().equals(pos) && seat.seat() == room.viewerSeat()))
            minecraft.getConnection().send(PayloadPackets.serverbound(new TableSeatPayload(pos, room.tableId())));
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var room = room();
        if (room != null) lobby.paint(graphics, room, width, height, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }
}
