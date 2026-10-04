package top.skyeyefast.mchjong.client;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TaiwanGame;
import top.skyeyefast.mchjong.engine.TaiwanSession;
import top.skyeyefast.mchjong.network.PayloadPackets;
import top.skyeyefast.mchjong.network.TaiwanNextHandPayload;

/** Rule-specific synchronization for the shared settlement interface. */
public final class TaiwanResultsScreen extends TableResultsScreen {
    public TaiwanResultsScreen(BlockPos pos) { this(pos, false); }
    public TaiwanResultsScreen(BlockPos pos, boolean immersive) {
        super(pos, immersive, Component.translatable("taiwan.mchjong.results"));
    }
    private TaiwanSession.View view() { return table() == null ? null : table().clientTaiwanView(); }
    @Override protected Presentation snapshot() {
        var t = table(); var v = view();
        if (t == null || v == null || t.clientTableRoom() == null || v.game().result() == null) return null;
        var deck = t.clientTaiwanDeck();
        return new Presentation(v.revision(), TableBoardState.live(v), TableResultState.taiwan(v), t.clientTableRoom(),
            deck.preset(), deck.material(), deck.back(), deck.backPreset(), t.clientTaiwanCloth(),
            tile -> TileMesh.artwork(deck.tile(tile)), v.lifecycle() == top.skyeyefast.mchjong.engine.TableSession.Lifecycle.FINISHED);
    }
    @Override protected void nextHandButton(int width, int height, int scale) {
        var v = view(); if (v == null || v.game().recipient() < 0) return;
        int span = Math.min(260 * scale, width - 24 * scale);
        var button = new MahjongButton((width - span) / 2, height - 26 * scale, span, 20 * scale,
            Component.translatable((v.confirmed() & (1 << v.game().recipient())) != 0 ? "ui.mchjong.confirmed" : "taiwan.mchjong.next_hand"), ignored -> {
                var current = view();
                if (pending || current == null || !canConfirm(current) || minecraft.getConnection() == null) return;
                pending = true;
                minecraft.getConnection().send(PayloadPackets.serverbound(new TaiwanNextHandPayload(tablePos(),
                    current.tableId(), current.incarnation(), current.game().decision())));
                rebuild();
            }).primary().textScale(scale);
        button.active = !pending && canConfirm(v); addRenderableWidget(button);
    }
    private boolean canConfirm(TaiwanSession.View v) {
        return v.lifecycle() == top.skyeyefast.mchjong.engine.TableSession.Lifecycle.PLAYING && !v.paused()
            && v.game().recipient() >= 0 && (v.confirmed() & 1 << v.game().recipient()) == 0;
    }
    @Override protected Component progressText() {
        var v = view(); if (v == null || v.lifecycle() != top.skyeyefast.mchjong.engine.TableSession.Lifecycle.PLAYING) return Component.empty();
        long elapsed = v.paused() ? 0 : table().clientViewAgeMillis() / 50;
        int seconds = (int) (Math.max(0, 200 - v.age() - elapsed) + 19) / 20;
        return Component.translatable("ui.mchjong.settlement_reading", Integer.bitCount(v.confirmed()), 4, seconds);
    }
}
