package top.skyeyefast.mchjong.client;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.McrGame;
import top.skyeyefast.mchjong.engine.McrSession;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.McrNextHandPayload;

/** Rule-specific synchronization for the shared settlement interface. */
public final class McrResultsScreen extends TableResultsScreen {
    public McrResultsScreen(BlockPos pos) { this(pos, false); }
    public McrResultsScreen(BlockPos pos, boolean immersive) {
        super(pos, immersive, Component.translatable("mcr.mchjong.results"));
    }
    private McrSession.View view() { return table() == null ? null : table().clientMcrView(); }
    @Override protected Presentation snapshot() {
        var t = table(); var v = view();
        if (t == null || v == null || t.clientTableRoom() == null || v.game().result() == null) return null;
        var deck = t.clientMcrDeck();
        return new Presentation(v.revision(), TableBoardState.live(v.game()), TableResultState.mcr(v.game(), v.participants()), t.clientTableRoom(),
            deck.preset(), deck.material(), deck.back(), deck.backPreset(), t.clientMcrCloth(),
            tile -> TileMesh.artwork(deck.tile(tile)), v.game().phase() == McrGame.Phase.MATCH_END);
    }
    @Override protected void nextHandButton(int width, int height, int scale) {
        var v = view(); if (v == null || v.game().viewerSeat() < 0) return;
        int span = Math.min(260 * scale, width - 24 * scale);
        var button = new MahjongButton((width - span) / 2, height - 26 * scale, span, 20 * scale,
            Component.translatable((v.confirmed() & (1 << v.game().viewerSeat())) != 0 ? "ui.mchjong.confirmed" : "mcr.mchjong.next_hand"), ignored -> {
                var current = view();
                if (pending || current == null || !current.canConfirmNextHand() || minecraft.getConnection() == null) return;
                pending = true;
                minecraft.getConnection().send(PayloadPackets.serverbound(new McrNextHandPayload(tablePos(),
                    current.tableId(), current.incarnation(), current.game().decision())));
                rebuild();
            }).primary().textScale(scale);
        button.active = !pending && v.canConfirmNextHand(); addRenderableWidget(button);
    }
    @Override protected Component progressText() {
        var v = view(); if (v == null || v.game().phase() != McrGame.Phase.HAND_END) return Component.empty();
        long elapsed = v.paused() ? 0 : table().clientViewAgeMillis() / 50;
        int seconds = (int) (Math.max(0, v.settlementTicks() - elapsed) + 19) / 20;
        return Component.translatable("ui.mchjong.settlement_reading", Integer.bitCount(v.confirmed()), 4, seconds);
    }
}
