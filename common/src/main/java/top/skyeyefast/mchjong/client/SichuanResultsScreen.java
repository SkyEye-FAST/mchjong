package top.skyeyefast.mchjong.client;

import top.skyeyefast.mchjong.text.CountedText;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.SichuanGame;
import top.skyeyefast.mchjong.engine.SichuanSession;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.SichuanNextHandPayload;

/** Rule-specific synchronization for the shared settlement interface. */
public final class SichuanResultsScreen extends TableResultsScreen {
    public SichuanResultsScreen(BlockPos pos) { this(pos, false); }
    public SichuanResultsScreen(BlockPos pos, boolean immersive) {
        super(pos, immersive, Component.translatable("sichuan.mchjong.results"));
    }
    private SichuanSession.View view() { return table() == null ? null : table().clientSichuanView(); }
    @Override protected Presentation snapshot() {
        var t = table(); var v = view();
        if (t == null || v == null || t.clientTableRoom() == null || v.game().result() == null) return null;
        var deck = t.clientSichuanDeck();
        return new Presentation(v.revision(), TableBoardState.live(v.game()), TableResultState.sichuan(v.game().rules(), v.game().result(), v.game().seats(), v.game().scores(), v.game().viewerSeat(), t.clientTableRoom().seats().stream().map(seat -> seat.participant()).toList()), t.clientTableRoom(),
            deck.preset(), deck.material(), deck.back(), deck.backPreset(), t.clientSichuanCloth(),
            tile -> TileMesh.artwork(deck.tile(tile)), v.game().phase() == SichuanGame.Phase.MATCH_END);
    }
    @Override protected void nextHandButton(int width, int height, int scale) {
        var v = view(); if (v == null || v.game().viewerSeat() < 0) return;
        int span = Math.min(260 * scale, width - 24 * scale);
        var button = new MahjongButton((width - span) / 2, height - 26 * scale, span, 20 * scale,
            Component.translatable((v.confirmed() & (1 << v.game().viewerSeat())) != 0 ? "sichuan.mchjong.confirmed" : "sichuan.mchjong.next_hand"), ignored -> {
                var current = view();
                if (pending || current == null || !current.canConfirmNextHand() || minecraft.getConnection() == null) return;
                pending = true;
                minecraft.getConnection().send(PayloadPackets.serverbound(new SichuanNextHandPayload(tablePos(),
                    current.tableId(), current.incarnation(), current.game().decision())));
                rebuild();
            }).primary().textScale(scale);
        button.active = !pending && v.canConfirmNextHand(); addRenderableWidget(button);
    }
    @Override protected Component progressText() {
        var v = view(); if (v == null || v.game().phase() != SichuanGame.Phase.HAND_END) return Component.empty();
        long elapsed = v.paused() ? 0 : table().clientViewAgeMillis() / 50;
        int seconds = (int) (Math.max(0, v.settlementTicks() - elapsed) + 19) / 20;
        return CountedText.of("sichuan.mchjong.reading", 2, v.confirmedCount(), 4, seconds);
    }

}
