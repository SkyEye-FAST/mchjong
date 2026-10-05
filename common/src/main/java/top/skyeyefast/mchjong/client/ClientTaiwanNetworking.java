package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import top.skyeyefast.mchjong.engine.TaiwanCodec;
import top.skyeyefast.mchjong.engine.TableSession;
import top.skyeyefast.mchjong.network.TaiwanViewPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

public final class ClientTaiwanNetworking {
    private ClientTaiwanNetworking() {}
    public static void receive(TaiwanViewPayload payload) {
        var client = Minecraft.getInstance();
        if (client.level == null || !(client.level.getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)) return;
        var view = payload.view().isEmpty() ? null : TaiwanCodec.decodeSessionView(payload.view());
        var room = payload.room();
        if ((room.lifecycle() == TableSession.Lifecycle.LOBBY) != (view == null)
            || view != null && (!view.tableId().equals(room.tableId()) || !view.incarnation().equals(room.incarnation())
                || view.game().recipient() != room.viewerSeat() || !view.game().rules().equals(payload.settings().rules()))) return;
        var previous = table.clientTaiwanView();
        var previousRoom = table.clientRoom();
        table.acceptTaiwanView(view, room, payload.deck(), payload.cloth(), payload.settings());
        if (table.clientTableRoom() != room) return;
        table.acceptWorldPolicy(payload.world());
        boolean opening = view != null && previousRoom != null && previousRoom.lobby()
            && previousRoom.tableId().equals(payload.room().tableId()) && previousRoom.incarnation().equals(payload.room().incarnation());
        TableAnimation.of(table).accept(view == null ? null : TableBoardState.live(view), view == null ? java.util.List.of() : TableAnimation.world(view.game()), payload.room().tableId(), payload.room().incarnation(),
            view == null ? payload.room().revision() : view.revision(), view == null ? 0 : view.handNumber(), opening, net.minecraft.util.Util.getMillis());

        if (payload.leaveDecision()) {
            if (!(client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), room.tableId())))
                client.setScreen(new TableLeaveScreen(payload.pos(), room.tableId(), room.decision()));
            return;
        }
        if (client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), room.tableId())) client.setScreen(null);
        var rootScreen = TableChildScreen.root(client.screen);
        var active = TaiwanTableScreen.active(client.screen);
        boolean showing = active != null && active.tablePos().equals(payload.pos())
            || rootScreen instanceof TaiwanLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
            || client.screen instanceof TaiwanRulesScreen rules && rules.tablePos().equals(payload.pos())
            || client.screen instanceof TaiwanResultsScreen results && results.tablePos().equals(payload.pos());
        if (previousRoom != null && previousRoom.viewerSeat() >= 0 && payload.room().viewerSeat() < 0
            && payload.room().lobby() && showing) { client.setScreen(null); return; }
        if (view == null) {
            if (!payload.open() && client.screen instanceof TaiwanRulesScreen rules && rules.tablePos().equals(payload.pos())) {
                rules.receivedView(payload.controlReply());
                return;
            }
            if (!payload.open() && client.screen instanceof TableClockScreen clock && clock.taiwanLobby() != null
                && clock.taiwanLobby().tablePos().equals(payload.pos())) return;
            if (payload.open() || showing || rootScreen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
                || rootScreen instanceof SichuanLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
                || RiichiTableScreen.active(client.screen) != null && RiichiTableScreen.active(client.screen).tablePos().equals(payload.pos())) {
                if (rootScreen instanceof TaiwanLobbyScreen lobby && lobby.tablePos().equals(payload.pos())) lobby.receivedView();
                else client.setScreen(new TaiwanLobbyScreen(payload.pos()));
            }
            return;
        }
        if (!payload.open() && !showing) return;
        if (view.game().result() != null) {
            if (client.screen instanceof TaiwanResultsScreen results && results.tablePos().equals(payload.pos())) results.receivedView();
            else client.setScreen(new TaiwanResultsScreen(payload.pos(), active != null && active.immersive()));
        } else if (active != null && active.tablePos().equals(payload.pos())) {
            active.receivedView();
        }
        else if (client.screen instanceof TaiwanResultsScreen results && results.tablePos().equals(payload.pos()))
            client.setScreen(new TaiwanTableScreen(payload.pos(), results.immersive()));
        else client.setScreen(new TaiwanTableScreen(payload.pos()));
    }
}
