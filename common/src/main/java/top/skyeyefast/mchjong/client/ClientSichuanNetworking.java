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
                || view.game().viewerSeat() != room.viewerSeat() || !view.game().rules().equals(payload.settings().rules()))) return;
        table.acceptSichuanView(view, room, payload.deck(), payload.cloth(), payload.settings());
        if (table.clientTableRoom() != room) return;
        if (payload.leaveDecision()) {
            if (!(client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), room.tableId())))
                client.setScreen(new TableLeaveScreen(payload.pos(), room.tableId(), room.decision()));
            return;
        }
        if (client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), room.tableId())) client.setScreen(null);
        var active = SichuanTableScreen.active(client.screen);
        boolean showing = active != null && active.tablePos().equals(payload.pos())
            || client.screen instanceof SichuanLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
            || client.screen instanceof SichuanRulesScreen rules && rules.tablePos().equals(payload.pos())
            || client.screen instanceof SichuanResultsScreen results && results.tablePos().equals(payload.pos());
        if (view == null) {
            if (!payload.open() && client.screen instanceof SichuanRulesScreen rules && rules.tablePos().equals(payload.pos())) {
                rules.receivedView(payload.controlReply());
                return;
            }
            if (!payload.open() && client.screen instanceof TableClockScreen clock && clock.sichuanScreen() != null
                && clock.sichuanScreen().tablePos().equals(payload.pos())) return;
            if (payload.open() || showing || client.screen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
                || RiichiTableScreen.active(client.screen) != null && RiichiTableScreen.active(client.screen).tablePos().equals(payload.pos())) {
                if (client.screen instanceof SichuanLobbyScreen lobby && lobby.tablePos().equals(payload.pos())) lobby.receivedView();
                else client.setScreen(new SichuanLobbyScreen(payload.pos()));
            }
            return;
        }
        if (!payload.open() && !showing) return;
        if (view.game().result() != null) {
            if (client.screen instanceof SichuanResultsScreen results && results.tablePos().equals(payload.pos())) results.receivedView();
            else client.setScreen(new SichuanResultsScreen(payload.pos(), active != null && active.immersive()));
        } else if (active != null && active.tablePos().equals(payload.pos())) active.receivedView();
        else if (client.screen instanceof SichuanResultsScreen results && results.tablePos().equals(payload.pos()))
            client.setScreen(new SichuanTableScreen(payload.pos(), results.immersive()));
        else client.setScreen(new SichuanTableScreen(payload.pos()));
    }
}
