package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import net.minecraft.world.phys.Vec2;
import top.skyeyefast.mchjong.engine.MahjongVariant;
import top.skyeyefast.mchjong.engine.TaiwanView;

/** Smoke clicks use the same foreground clearance as the rendered hand. */
public final class TaiwanHandSmoke {
    private TaiwanHandSmoke() {}
    public static Vec2 point(TaiwanView view, int tile, int width, int height) {
        var player = view.seats().get(view.recipient());
        var tiles = new ArrayList<>(player.concealed());
        if (player.drawn() >= 0 && tiles.remove(Integer.valueOf(player.drawn()))) tiles.add(player.drawn());
        var hand = new TableHand(tiles, player.drawn(), player.melds(), player.flowers().size(), view.recipient(),
            TableCanvas.WIDTH, TableCanvas.HEIGHT - 60, 58, true, MahjongVariant.TAIWAN);
        var point = hand.point(tile);
        float scale = Math.min(width / (float) TableCanvas.WIDTH, height / (float) TableCanvas.HEIGHT);
        return new Vec2((width - TableCanvas.WIDTH * scale) / 2 + point.x() * scale,
            (height - TableCanvas.HEIGHT * scale) / 2 + point.y() * scale);
    }
}
