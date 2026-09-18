package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;

/** The recipient's own hand, using the same tile identities, artwork and decisions as the world. */
final class TableHand {
    private final List<Integer> tiles;
    private final int drawn, left, y, tileWidth, tileHeight, gap, span;

    TableHand(TableView.Seat player, int width, int height) {
        tiles = player.hand();
        drawn = player.drawn();
        tileWidth = Math.max(1, Math.min(26, (width - 36) / Math.max(14, tiles.size())));
        tileHeight = Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH);
        gap = drawn == Tile.ABSENT ? 0 : Math.max(4, tileWidth / 3);
        span = tiles.size() * tileWidth + gap;
        // Keep a stable fourteen-tile rail; a short hand must not cover the right-corner melds.
        left = (width - Math.max(14, tiles.size()) * tileWidth - Math.max(4, tileWidth / 3)) / 2;
        y = height - 20 - tileHeight;
    }

    int top() { return y - 7; }

    int centerX(int tile) {
        int index = tiles.indexOf(tile);
        return index < 0 ? -1 : x(index) + tileWidth / 2;
    }

    private int x(int index) { return left + index * tileWidth + (gap > 0 && index == tiles.size() - 1 ? gap : 0); }
    private int y(int tile, int selected) { return y - (tile == selected ? 3 : 0); }

    boolean contains(double px, double py) {
        return px >= left - 5 && px < left + span + 5 && py >= top() && py < y + tileHeight + 5;
    }

    int pick(double px, double py, int selected) {
        for (int i = 0; i < tiles.size(); i++) {
            int tile = tiles.get(i), x = x(i), top = y(tile, selected);
            if (tile >= 0 && px >= x && px < x + tileWidth && py >= top && py < top + tileHeight) return tile;
        }
        return Tile.ABSENT;
    }

    void render(GuiGraphics graphics, int selected, IntUnaryOperator highlight) {
        MahjongUi.panel(graphics, left - 5, top(), span + 10, tileHeight + 12);
        for (int i = 0; i < tiles.size(); i++) {
            int tile = tiles.get(i), top = y(tile, selected), color = highlight.applyAsInt(tile);
            TileGui.tile(graphics, tile, x(i), top, tileWidth, tile < 0, false, false);
            if (color != 0) graphics.renderOutline(x(i), top, tileWidth, tileHeight, color);
        }
    }
}
