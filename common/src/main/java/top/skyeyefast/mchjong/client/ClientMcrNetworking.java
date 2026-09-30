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
        table.acceptMcrView(view, payload.room(), payload.deck(), payload.cloth(), payload.timeControl());
        if (table.clientTableRoom() != payload.room()) return;
        if (payload.leaveDecision()) {
            if (!(client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), payload.room().tableId())))
                client.setScreen(new TableLeaveScreen(payload.pos(), payload.room().tableId(), payload.room().decision()));
            return;
        }
        if (client.screen instanceof TableLeaveScreen leave && leave.matches(payload.pos(), payload.room().tableId()))
            client.setScreen(null);
        var active = McrTableScreen.active(client.screen);
        boolean showing = active != null && active.tablePos().equals(payload.pos())
            || client.screen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
            || client.screen instanceof McrResultsScreen results && results.tablePos().equals(payload.pos());
        if (view == null) {
            if (!payload.open() && client.screen instanceof TableClockScreen clock && clock.mcrLobby() != null
                && clock.mcrLobby().tablePos().equals(payload.pos())) return;
            if (payload.open() || showing
                || client.screen instanceof SichuanLobbyScreen screen && screen.tablePos().equals(payload.pos())
                || RiichiTableScreen.active(client.screen) != null
                    && RiichiTableScreen.active(client.screen).tablePos().equals(payload.pos())) {
                if (client.screen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())) lobby.receivedView();
                else client.setScreen(new McrLobbyScreen(payload.pos()));
            }
            return;
        }
        if (!payload.open() && !showing) return;
        boolean ended = view.game().phase() == McrGame.Phase.HAND_END || view.game().phase() == McrGame.Phase.MATCH_END;
        if (ended) {
            if (client.screen instanceof McrResultsScreen results && results.tablePos().equals(payload.pos())) results.receivedView();
            else client.setScreen(new McrResultsScreen(payload.pos(), active != null && active.immersive()));
        } else if (active != null && active.tablePos().equals(payload.pos())) active.receivedView();
        else if (client.screen instanceof McrResultsScreen results && results.tablePos().equals(payload.pos())) {
            client.setScreen(new McrTableScreen(payload.pos(), results.immersive()));
        } else client.setScreen(new McrTableScreen(payload.pos()));
    }
}
