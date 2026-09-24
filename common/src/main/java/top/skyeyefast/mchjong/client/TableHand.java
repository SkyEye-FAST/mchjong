package top.skyeyefast.mchjong.client;

import java.util.List;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.gui.GuiGraphics;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;

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
        right = width - (perspective ? 24 : 8);
        tiles = player.hand();
        drawn = player.drawn();
        tileWidth = Math.max(1, Math.min(maxTileWidth, (width - 36) / 14));
        tileHeight = Math.round(tileWidth * TileMesh.HEIGHT / TileMesh.WIDTH);
        gap = drawn == Tile.ABSENT ? 0 : Math.max(18, tileWidth / 2);
        span = tiles.size() * tileWidth + gap;
        // Keep a stable fourteen-tile rail; a short hand must not cover the right-corner melds.
        left = (width - Math.max(14, tiles.size()) * tileWidth - Math.max(18, tileWidth / 2)) / 2;
        y = height - (perspective ? 15 : 20) - tileHeight;
    }

    int top() { return y - (perspective ? Math.max(12, tileWidth / 5) + 2 : 7); }
    int centerX() { return left + span / 2; }
    int tileWidth() { return tileWidth; }

    int centerX(int tile) {
        int index = tiles.indexOf(tile);
        return index < 0 ? -1 : x(index) + tileWidth / 2;
    }

    Point point(int tile) {
        return point(tile, Tile.ABSENT, Tile.ABSENT);
    }

    Point point(int tile, int selected, int hovered) {
        int index = tiles.indexOf(tile);
        if (index < 0) return null;
        return new Point(x(index) + tileWidth / 2, y(index, tile, selected, hovered) + tileHeight / 2);
    }

    private int x(int index) { return left + index * tileWidth + (gap > 0 && index == tiles.size() - 1 ? gap : 0); }
    private int y(int index, int tile, int selected, int hovered) {
        int lift = tile == selected ? Math.max(10, tileWidth / 5)
            : tile == hovered ? Math.max(5, tileWidth / 10) : 0;
        return y - lift;
    }

    boolean contains(double px, double py) {
        int depth = perspective ? Math.max(2, tileWidth / 8) : 0;
        return px >= left - 5 && px < left + span + 5 && py >= top()
            && py < y + tileHeight + depth + 5;
    }

    int pick(double px, double py, int selected) {
        int depth = perspective ? Math.max(2, tileWidth / 8) : 0;
        for (int i = tiles.size() - 1; i >= 0; i--) {
            int tile = tiles.get(i), x = x(i), top = y(i, tile, selected, Tile.ABSENT);
            if (tile >= 0 && px >= x && px < x + tileWidth && py >= top && py < top + tileHeight + depth) return tile;
        }
        return Tile.ABSENT;
    }

    void render(GuiGraphics graphics, int selected, int hovered, IntUnaryOperator highlight, TileFacePreset preset) {
        render(graphics, selected, hovered, highlight, Tile.ABSENT, preset, TileMaterial.BONE, null);
    }

    void render(GuiGraphics graphics, int selected, int hovered, IntUnaryOperator highlight, int suppressedTile,
                TileFacePreset preset, TileMaterial material, net.minecraft.world.item.DyeColor dye) {
        if (perspective) {
            int railLeft = Math.max(8, left - 24), railRight = Math.min(right + 8, left + span + 26);
            graphics.fill(railLeft + 8, y + tileHeight + 5, railRight + 10, y + tileHeight + 20, 0x66000000);
            graphics.fill(railLeft, y + tileHeight - 1, railRight, y + tileHeight + 12, 0xff081d20);
            graphics.fill(railLeft + 3, y + tileHeight - 1, railRight - 3, y + tileHeight + 3, 0xff31575a);
            graphics.hLine(railLeft + 4, railRight - 5, y + tileHeight - 2, 0xff638083);
        }
        for (int i = 0; i < tiles.size(); i++) {
            int tile = tiles.get(i), top = y(i, tile, selected, hovered), color = highlight.applyAsInt(tile);
            if (tile != suppressedTile) {
                if (perspective) TileGui.tile3d(graphics, tile, x(i), top, tileWidth, tile < 0, false, false, false,
                    Math.max(2, tileWidth / 8), preset, material, dye);
                else TileGui.tile(graphics, tile, x(i), top, tileWidth, tile < 0, false, false, false, preset, material, dye);
                if (color != 0) graphics.renderOutline(x(i), top, tileWidth, tileHeight, color);
            }
        }
        if (perspective) return;
        int meldTileWidth = tileWidth;
        int meldAvailable = right - left - span - 8;
        while (meldTileWidth > 2 && meldWidth(meldTileWidth) > meldAvailable) meldTileWidth--;
        int meldX = right;
        int meldHeight = Math.round(meldTileWidth * TileMesh.HEIGHT / TileMesh.WIDTH);
        int meldY = y + tileHeight - meldHeight;
        for (var meld : melds) {
            meldX -= TileGui.meldWidth(meld, owner, meldTileWidth);
            TileGui.meld(graphics, meld, owner, meldX, meldY, meldTileWidth, preset, material, dye);
        }
    }

    private int meldWidth(int width) { return melds.stream().mapToInt(meld -> TileGui.meldWidth(meld, owner, width)).sum(); }
}
