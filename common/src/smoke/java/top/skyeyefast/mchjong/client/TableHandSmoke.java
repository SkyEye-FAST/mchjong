package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec2;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.TaiwanView;

/** Smoke clicks use the same foreground clearance as the rendered hand. */
public final class TableHandSmoke {
    private TableHandSmoke() {}
    public static Vec2 point(TaiwanView view, int tile, int width, int height) {
        var player = view.seats().get(view.recipient());
        return point(player.concealed(), player.drawn(), player.melds(), player.flowers().size(),
            view.recipient(), MahjongVariant.TAIWAN, tile, width, height);
    }
    public static Vec2 point(top.skyeyefast.mchjong.engine.McrView view, int tile, int width, int height) {
        var player = view.seats().get(view.viewerSeat());
        return point(player.hand(), player.drawn(), player.melds(), player.flowers().size(),
            view.viewerSeat(), MahjongVariant.MCR, tile, width, height);
    }
    public static Vec2 point(top.skyeyefast.mchjong.engine.SichuanView view, int tile, int width, int height) {
        var player = view.seats().get(view.viewerSeat());
        return point(player.hand(), player.drawn(), player.melds(), 0,
            view.viewerSeat(), MahjongVariant.SICHUAN, tile, width, height);
    }
    private static Vec2 point(List<Integer> concealed, int drawn, List<top.skyeyefast.mchjong.engine.Meld> melds,
                             int flowers, int seat, MahjongVariant variant, int tile, int width, int height) {
        var tiles = new ArrayList<>(concealed);
        if (drawn >= 0 && tiles.remove(Integer.valueOf(drawn))) tiles.add(drawn);
        var hand = new TableHand(tiles, drawn, melds, flowers, seat,
            TableCanvas.WIDTH, TableCanvas.HEIGHT - 60, 58, true, variant);
        var point = hand.point(tile);
        float scale = Math.min(width / (float) TableCanvas.WIDTH, height / (float) TableCanvas.HEIGHT);
        return new Vec2((width - TableCanvas.WIDTH * scale) / 2 + point.x() * scale,
            (height - TableCanvas.HEIGHT * scale) / 2 + point.y() * scale);
    }
}
