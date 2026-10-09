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
        var previous = table.clientSichuanView();
        var previousRoom = table.clientRoom();
        table.acceptSichuanView(view, room, payload.deck(), payload.cloth(), payload.settings());
        if (table.clientTableRoom() != room) return;
        table.acceptWorldPolicy(payload.world());
        boolean opening = view != null && previousRoom != null && previousRoom.lobby()
            && previousRoom.tableId().equals(payload.room().tableId()) && previousRoom.incarnation().equals(payload.room().incarnation());
        TableAnimation.of(table).accept(view == null ? null : TableBoardState.live(view.game()), view == null ? java.util.List.of() : TableAnimation.world(view.game()), payload.room().tableId(), payload.room().incarnation(),
            view == null ? payload.room().revision() : view.revision(), view == null ? 0 : view.game().handNumber(), opening, net.minecraft.util.Util.getMillis());

        if (view != null && previousRoom != null && previousRoom.tableId().equals(payload.room().tableId())
            && previousRoom.incarnation().equals(payload.room().incarnation())) {
            if (previous == null && previousRoom.lobby()
                || previous != null && previous.game().handNumber() != view.game().handNumber())
                TableAudio.opening(payload.pos(), table.automatic());
            else if (previous != null)
                for (String effect : TableAudio.between(previous.game(), view.game()))
                    TableAudio.effect(effect, effect.equals("score_reveal") ? null : payload.pos(), 0);
        }

        if (payload.leaveDecision()) {
            if (!(client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), room.tableId())))
                client.setScreen(new TableLeaveScreen(payload.pos(), room.tableId(), room.decision()));
            return;
        }
        if (client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), room.tableId())) client.setScreen(null);
        var rootScreen = TableChildScreen.root(client.screen);
        var rulesScreen = TableChildScreen.find(client.screen, SichuanRulesScreen.class);
        var active = SichuanTableScreen.active(client.screen);
        boolean showing = active != null && active.tablePos().equals(payload.pos())
            || rootScreen instanceof SichuanLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
            || rulesScreen != null && rulesScreen.tablePos().equals(payload.pos())
            || rootScreen instanceof SichuanResultsScreen results && results.tablePos().equals(payload.pos());
        if (previousRoom != null && previousRoom.viewerSeat() >= 0 && payload.room().viewerSeat() < 0
            && payload.room().lobby() && showing) { client.setScreen(null); return; }
        if (view == null) {
            if (!payload.open() && rulesScreen != null && rulesScreen.tablePos().equals(payload.pos())) {
                rulesScreen.receivedView(payload.controlReply());
                return;
            }
            if (!payload.open() && client.screen instanceof TableClockScreen clock && clock.sichuanScreen() != null
                && clock.sichuanScreen().tablePos().equals(payload.pos())) return;
            if (payload.open() || showing || rootScreen instanceof TaiwanLobbyScreen taiwan && taiwan.tablePos().equals(payload.pos()) || rootScreen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
                || RiichiTableScreen.active(client.screen) != null && RiichiTableScreen.active(client.screen).tablePos().equals(payload.pos())) {
                if (rootScreen instanceof SichuanLobbyScreen lobby && lobby.tablePos().equals(payload.pos())) lobby.receivedView();
                else client.setScreen(new SichuanLobbyScreen(payload.pos()));
            }
            return;
        }
        if (!payload.open() && !showing) return;
        if (view.game().result() != null) {
            if (rootScreen instanceof SichuanResultsScreen results && results.tablePos().equals(payload.pos())) results.receivedView();
            else client.setScreen(new SichuanResultsScreen(payload.pos(), active != null && active.immersive()));
        } else if (active != null && active.tablePos().equals(payload.pos())) {
            active.receivedView();
            if (payload.controlReply()) active.receivedControlReply();
        }
        else if (rootScreen instanceof SichuanResultsScreen results && results.tablePos().equals(payload.pos()))
            client.setScreen(new SichuanTableScreen(payload.pos(), results.immersive()));
        else client.setScreen(new SichuanTableScreen(payload.pos()));
    }
}
