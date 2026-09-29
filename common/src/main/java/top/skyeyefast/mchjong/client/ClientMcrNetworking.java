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
        table.acceptMcrView(view, payload.room(), payload.deck(), payload.cloth());
        if (table.clientTableRoom() != payload.room()) return;
        if (view == null) {
            if (payload.open() || client.screen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())
                || client.screen instanceof McrResultsScreen results && results.tablePos().equals(payload.pos())
                || TableScreen.active(client.screen) != null
                    && TableScreen.active(client.screen).tablePos().equals(payload.pos())) {
                if (client.screen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())) lobby.receivedView();
                else client.setScreen(new McrLobbyScreen(payload.pos()));
            }
            return;
        }
        boolean ended = view.game().phase() == McrGame.Phase.HAND_END || view.game().phase() == McrGame.Phase.MATCH_END;
        if (ended && (payload.open() || client.screen instanceof McrTableScreen || client.screen instanceof McrResultsScreen)) {
            if (client.screen instanceof McrResultsScreen results && results.tablePos().equals(payload.pos())) results.receivedView();
            else client.setScreen(new McrResultsScreen(payload.pos()));
        } else if (payload.open() || client.screen instanceof McrLobbyScreen lobby && lobby.tablePos().equals(payload.pos())) {
            if (client.screen instanceof McrTableScreen screen && screen.tablePos().equals(payload.pos())) screen.receivedView();
            else client.setScreen(new McrTableScreen(payload.pos()));
        } else if (client.screen instanceof McrTableScreen screen && screen.tablePos().equals(payload.pos())) screen.receivedView();
        else if (client.screen instanceof McrResultsScreen results && results.tablePos().equals(payload.pos())) {
            client.setScreen(new McrTableScreen(payload.pos()));
        }
    }
}
