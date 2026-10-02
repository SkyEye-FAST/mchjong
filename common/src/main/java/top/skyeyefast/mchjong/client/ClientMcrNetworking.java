package top.skyeyefast.mchjong.client;

import net.minecraft.client.Minecraft;
import top.skyeyefast.mchjong.engine.McrCodec;
import top.skyeyefast.mchjong.engine.McrGame;
import top.skyeyefast.mchjong.network.McrViewPayload;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

/** Decode the session's already-redacted projection before opening MCR screens. */
public final class ClientMcrNetworking {
    private ClientMcrNetworking() {}

    public static void receive(McrViewPayload payload) {
        var client = Minecraft.getInstance();
        if (client.level == null || !(client.level.getBlockEntity(payload.pos()) instanceof MahjongTableBlockEntity table)) return;
        var view = payload.view().isEmpty() ? null : McrCodec.decodeSessionView(payload.view());
        var previous = table.clientMcrView();
        var previousRoom = table.clientRoom();
        table.acceptMcrView(view, payload.room(), payload.deck(), payload.cloth(), payload.timeControl());
        if (table.clientTableRoom() != payload.room()) return;
        table.acceptWorldPolicy(payload.world());
        boolean opening = view != null && previousRoom != null && previousRoom.lobby()
            && previousRoom.tableId().equals(payload.room().tableId()) && previousRoom.incarnation().equals(payload.room().incarnation());
        TableAnimation.of(table).accept(view == null ? null : TableBoardState.live(view.game()), view == null ? java.util.List.of() : TableAnimation.world(view.game()), payload.room().tableId(), payload.room().incarnation(),
            view == null ? payload.room().revision() : view.revision(), view == null ? 0 : view.game().handNumber(), opening, net.minecraft.Util.getMillis());

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
            if (!(client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), payload.room().tableId())))
                client.setScreen(new TableLeaveScreen(payload.pos(), payload.room().tableId(), payload.room().decision()));
            return;
        }
        if (client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), payload.room().tableId()))
            client.setScreen(null);
        var rootScreen = TableChildScreen.root(client.screen);
        var active = McrTableScreen.active(client.screen);
        boolean showing = active != null && active.tablePos().equals(payload.pos())
            || rootScreen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
            || client.screen instanceof McrResultsScreen results && results.tablePos().equals(payload.pos());
        if (previousRoom != null && previousRoom.viewerSeat() >= 0 && payload.room().viewerSeat() < 0
            && payload.room().lobby() && showing) { client.setScreen(null); return; }
        if (view == null) {
            if (!payload.open() && client.screen instanceof TableClockScreen clock && clock.mcrLobby() != null
                && clock.mcrLobby().tablePos().equals(payload.pos())) return;
            if (payload.open() || showing
                || rootScreen instanceof SichuanLobbyScreen screen && screen.tablePos().equals(payload.pos())
                || RiichiTableScreen.active(client.screen) != null
                    && RiichiTableScreen.active(client.screen).tablePos().equals(payload.pos())) {
                if (rootScreen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())) lobby.receivedView();
                else client.setScreen(new McrLobbyScreen(payload.pos()));
            }
            return;
        }
        if (!payload.open() && !showing) return;
        boolean ended = view.game().phase() == McrGame.Phase.HAND_END || view.game().phase() == McrGame.Phase.MATCH_END;
        if (ended) {
            if (client.screen instanceof McrResultsScreen results && results.tablePos().equals(payload.pos())) results.receivedView();
            else client.setScreen(new McrResultsScreen(payload.pos(), active != null && active.immersive()));
        } else if (active != null && active.tablePos().equals(payload.pos())) {
            active.receivedView();
            if (payload.controlReply()) active.receivedControlReply();
        }
        else if (client.screen instanceof McrResultsScreen results && results.tablePos().equals(payload.pos())) {
            client.setScreen(new McrTableScreen(payload.pos(), results.immersive()));
        } else client.setScreen(new McrTableScreen(payload.pos()));
    }
}
