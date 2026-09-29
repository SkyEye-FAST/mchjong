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
        var view = McrCodec.decodeSessionView(payload.view());
        table.acceptMcrView(view, payload.deck(), payload.cloth());
        if (table.clientMcrView() != view) return;
        boolean ended = view.game().phase() == McrGame.Phase.HAND_END || view.game().phase() == McrGame.Phase.MATCH_END;
        if (ended && (payload.open() || client.screen instanceof McrTableScreen || client.screen instanceof McrResultsScreen)) {
            if (client.screen instanceof McrResultsScreen results && results.tablePos().equals(payload.pos())) results.receivedView();
            else client.setScreen(new McrResultsScreen(payload.pos()));
        } else if (payload.open()) {
            if (client.screen instanceof McrTableScreen screen && screen.tablePos().equals(payload.pos())) screen.receivedView();
            else client.setScreen(new McrTableScreen(payload.pos()));
        } else if (client.screen instanceof McrTableScreen screen && screen.tablePos().equals(payload.pos())) screen.receivedView();
        else if (client.screen instanceof McrResultsScreen results && results.tablePos().equals(payload.pos())) {
            client.setScreen(new McrTableScreen(payload.pos()));
        }
    }
}
