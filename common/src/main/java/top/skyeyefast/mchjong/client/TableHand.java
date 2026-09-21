package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** The recipient's own hand, using the same tile identities, artwork and decisions as the world. */
final class TableHand {
    record Point(int x, int y) {}
    private final List<Integer> tiles;
    private final int drawn, left, y, tileWidth, tileHeight, gap, span;
    private final List<top.skyeyefast.mchjong.engine.Meld> melds;
    private final int right, owner;
    private final boolean perspective;

    TableHand(TableView.Seat player, int owner, int width, int height, int maxTileWidth) {
        this(player, owner, width, height, maxTileWidth, false);
    }

    TableHand(TableView.Seat player, int owner, int width, int height, int maxTileWidth, boolean perspective) {
        this.owner = owner;
        this.melds = player.melds();
        this.perspective = perspective;
        right = width - 8;
        tiles = player.hand();
        drawn = player.drawn();
        tileWidth = Math.max(1, Math.min(maxTileWidth, (width - 36) / 14));
        tileHeight = Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH);
        gap = drawn == Tile.ABSENT ? 0 : Math.max(4, tileWidth / 3);
        span = tiles.size() * tileWidth + gap;
        // Keep a stable fourteen-tile rail; a short hand must not cover the right-corner melds.
        left = (width - Math.max(14, tiles.size()) * tileWidth - Math.max(4, tileWidth / 3)) / 2;
        y = height - (perspective ? 15 : 20) - tileHeight;
    }

    int top() { return y - 7; }
    int centerX() { return left + span / 2; }
    int tileWidth() { return tileWidth; }

    int centerX(int tile) {
        int index = tiles.indexOf(tile);
        return index < 0 ? -1 : x(index) + tileWidth / 2;
    }

    Point point(int tile) {
        int index = tiles.indexOf(tile);
        if (index < 0) return null;
        return new Point(x(index) + tileWidth / 2, y(index, tile, Tile.ABSENT, Tile.ABSENT) + tileHeight / 2);
    }

    private int x(int index) { return left + index * tileWidth + (gap > 0 && index == tiles.size() - 1 ? gap : 0); }
    private int arc(int index) {
        if (!perspective || tiles.size() <= 1) return 0;
        double center = (tiles.size() - 1) / 2.0;
        return (int) Math.round(Math.abs(index - center) * .18);
    }

    private int y(int index, int tile, int selected, int hovered) {
        int lift = tile == selected ? 7 : tile == hovered ? 3 : 0;
        return y + arc(index) - lift;
    }

    boolean contains(double px, double py) {
        return px >= left - 5 && px < left + span + 5 && py >= top() && py < y + tileHeight + 5;
    }

    int pick(double px, double py, int selected) {
        for (int i = 0; i < tiles.size(); i++) {
            int tile = tiles.get(i), x = x(i), top = y(i, tile, selected, Tile.ABSENT);
            if (tile >= 0 && px >= x && px < x + tileWidth && py >= top && py < top + tileHeight) return tile;
        }
        return Tile.ABSENT;
    }

    void render(GuiGraphics graphics, int selected, int hovered, IntUnaryOperator highlight, TileFacePreset preset) {
        render(graphics, selected, hovered, highlight, Tile.ABSENT, preset);
    }

    void render(GuiGraphics graphics, int selected, int hovered, IntUnaryOperator highlight, int suppressedTile, TileFacePreset preset) {
        if (perspective) {
            int railLeft = Math.max(4, left - 12), railRight = Math.min(right + 4, left + span + 14);
            graphics.fill(railLeft + 3, y + tileHeight + 1, railRight + 3, y + tileHeight + 8, 0x55000000);
            graphics.fill(railLeft, y + tileHeight, railRight, y + tileHeight + 5, 0xff0b2325);
            graphics.hLine(railLeft + 1, railRight - 2, y + tileHeight, 0xff456264);
        }
        for (int i = 0; i < tiles.size(); i++) {
            int tile = tiles.get(i), top = y(i, tile, selected, hovered), color = highlight.applyAsInt(tile);
            if (tile != suppressedTile) {
                if (perspective) TileGui.tile3d(graphics, tile, x(i), top, tileWidth, tile < 0, false, false, false,
                    Math.max(2, tileWidth / 8), preset);
                else TileGui.tile(graphics, tile, x(i), top, tileWidth, tile < 0, false, false, preset);
                if (color != 0) graphics.renderOutline(x(i), top, tileWidth, tileHeight, color);
            }
        }
        int meldTileWidth = tileWidth;
        while (meldTileWidth > 2 && meldWidth(meldTileWidth) > right - left - span - 8) meldTileWidth--;
        int meldX = right - meldWidth(meldTileWidth);
        int meldY = y + tileHeight - Math.round(meldTileWidth * TileMesh.HEIGHT / TileMesh.WIDTH);
        for (var meld : melds) {
            if (perspective) TileGui.meld3d(graphics, meld, owner, meldX, meldY, meldTileWidth,
                Math.max(1, meldTileWidth / 8), preset);
            else TileGui.meld(graphics, meld, owner, meldX, meldY, meldTileWidth, preset);
            meldX += TileGui.meldWidth(meld, owner, meldTileWidth);
        }
    }

    private int meldWidth(int width) { return melds.stream().mapToInt(meld -> TileGui.meldWidth(meld, owner, width)).sum(); }
}
