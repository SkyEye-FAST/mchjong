package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import top.skyeyefast.mchjong.engine.SichuanCodec;
import top.skyeyefast.mchjong.engine.TableSession;
import top.skyeyefast.mchjong.network.SichuanViewPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

public final class ClientSichuanNetworking {
    private ClientSichuanNetworking() {}
    public static void receive(SichuanViewPayload payload) {
        var client = Minecraft.getInstance();
        if (client.level == null || !(client.level.getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)) return;
        var view = payload.view().isEmpty() ? null : SichuanCodec.decodeSessionView(payload.view());
        var room = payload.room();
        if ((room.lifecycle() == TableSession.Lifecycle.LOBBY) != (view == null)
            || view != null && (!view.tableId().equals(room.tableId()) || !view.incarnation().equals(room.incarnation())
                || view.game().viewerSeat() != room.viewerSeat())) return;
        table.acceptSichuanView(view, room, payload.timeControl());
        if (table.clientTableRoom() != room) return;
        if (payload.leaveDecision()) {
            if (!(client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), room.tableId())))
                client.setScreen(new TableLeaveScreen(payload.pos(), room.tableId(), room.decision()));
            return;
        }
        if (client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), room.tableId())) client.setScreen(null);
        if (!payload.open() && client.screen instanceof TableClockScreen clock && clock.sichuanScreen() != null
            && clock.sichuanScreen().tablePos().equals(payload.pos())) return;
        if (client.screen instanceof SichuanScreen screen && screen.tablePos().equals(payload.pos())) screen.receivedView();
        else if (payload.open() || client.screen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
            || RiichiTableScreen.active(client.screen) != null && RiichiTableScreen.active(client.screen).tablePos().equals(payload.pos()))
            client.setScreen(new SichuanScreen(payload.pos()));
    }
}
