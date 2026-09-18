package top.skyeyefast.mchjong.client;

import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Screen-space play surface. It consumes only the recipient's view, never world geometry or camera rays. */
final class TableBoard {
    record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
    }
    record Point(int x, int y) {}

    private final Rect bounds;
    private final int viewer, players, actionsTop;
    private final Map<Integer, Point> tiles = new HashMap<>();

    TableBoard(TableView view, int left, int right, int top, int bottom, int actionsTop) {
        bounds = new Rect(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
        viewer = view.viewerSeat();
        players = view.rules().players();
        this.actionsTop = actionsTop;
    }

    static int side(int seat, int viewer, int players) {
        int relative = Math.floorMod(seat - viewer, players);
        return players == 3 && relative == 2 ? 3 : relative;
    }

    Rect area(int seat) {
        int side = side(seat, viewer, players);
        int flank = bounds.width() * 28 / 100;
        int center = bounds.width() - 2 * flank - 12;
        int middle = centerY();
        int gap = bounds.height() >= 180 ? 18 : 3;
        return switch (side) {
            case 1 -> new Rect(bounds.right() - flank, bounds.y(), flank,
                Math.max(1, Math.min(bounds.bottom(), actionsTop) - bounds.y()));
            case 3 -> new Rect(bounds.x(), bounds.y(), flank, Math.max(1, bounds.height() - cardHeight() - 4));
            case 2 -> new Rect(bounds.x() + flank + 6, bounds.y(), center, Math.max(1, middle - bounds.y() - gap));
            default -> new Rect(bounds.x() + flank + 6, players == 3 ? bounds.y() + cardHeight() : middle + gap,
                center, Math.max(1, bounds.bottom() - (players == 3 ? bounds.y() + cardHeight() : middle + gap)));
        };
    }

    Rect card(int seat) {
        Rect area = area(seat);
        return seat == viewer ? new Rect(bounds.x(), bounds.bottom() - cardHeight(), bounds.width() * 28 / 100, cardHeight())
            : new Rect(area.x(), area.y(), area.width(), cardHeight());
    }

    private int cardHeight() { return 24; }
    private int centerY() { return bounds.y() + (bounds.height() + cardHeight() + 16) / 2; }

    Point point(int tile) { return tiles.get(tile); }

    void render(GuiGraphics graphics, TableView view, TileFacePreset preset) {
        tiles.clear();
        for (int seat = 0; seat < players; seat++) {
            var player = view.seats().get(seat);
            Rect area = area(seat);
            int side = side(seat, viewer, players);
            int top = seat == viewer ? area.y() : area.y() + cardHeight() + 3;
            int bottom = area.bottom();
            int riverLeft = area.x(), riverWidth = area.width();
            if (seat != viewer || !player.norths().isEmpty()) {
                int tileWidth = 14;
                boolean vertical = side % 2 == 1;
                int stripSpace = vertical ? bottom - top : area.width();
                boolean addedKan = seat != viewer && player.melds().stream()
                    .anyMatch(meld -> meld.type() == Meld.Type.ADDED_KAN);
                while (tileWidth > 2 && (stripWidth(player, seat, tileWidth) > stripSpace
                    || tileHeight(tileWidth) + (addedKan ? tileWidth : 0) > (vertical ? area.width() : bottom - top) / 3)) tileWidth--;
                int strip = stripWidth(player, seat, tileWidth);
                int thickness = tileHeight(tileWidth) + (addedKan ? tileWidth : 0);
                int cx = vertical ? side == 3 ? area.x() + thickness / 2 : area.right() - thickness / 2
                    : area.x() + area.width() / 2;
                int cy = vertical ? top + (bottom - top) / 2 : top + thickness / 2;
                graphics.pose().pushPose();
                graphics.pose().translate(cx, cy, 0);
                graphics.pose().mulPose(Axis.ZP.rotationDegrees(-90 * side));
                int x = -strip / 2;
                int tileY = -tileHeight(tileWidth) / 2 + (addedKan ? tileWidth / 2 : 0);
                if (seat != viewer) for (int tile : player.hand()) {
                    TileGui.tile(graphics, tile, x, tileY, tileWidth, tile < 0, false, false, preset);
                    rememberRotated(tile, cx, cy, x + tileWidth / 2, tileY + tileHeight(tileWidth) / 2, side);
                    x += tileWidth;
                }
                if (seat != viewer && !player.hand().isEmpty() && !player.melds().isEmpty()) x += 3;
                if (seat != viewer) for (var meld : player.melds()) {
                    TileGui.meld(graphics, meld, seat, x, tileY, tileWidth, preset);
                    int span = TileGui.meldWidth(meld, seat, tileWidth);
                    for (int tile : meld.tiles()) rememberRotated(tile, cx, cy, x + span / 2, tileY + tileHeight(tileWidth) / 2, side);
                    x += span;
                }
                for (int tile : player.norths()) {
                    TileGui.tile(graphics, tile, x, tileY, tileWidth, false, false, false, preset);
                    rememberRotated(tile, cx, cy, x + tileWidth / 2, tileY + tileHeight(tileWidth) / 2, side);
                    x += tileWidth;
                }
                graphics.pose().popPose();
                if (vertical) {
                    riverWidth -= thickness + 4;
                    if (side == 3) riverLeft += thickness + 4;
                } else top += thickness + 3;
            }
            if (TableSettings.get().showRiver) river(graphics, view, seat,
                new Rect(riverLeft, top, Math.max(1, riverWidth), Math.max(1, bottom - top)), preset);
        }
        if (players == 4 && bounds.height() >= 180) {
            int cx = bounds.x() + bounds.width() / 2, cy = centerY();
            var font = net.minecraft.client.Minecraft.getInstance().font;
            MahjongUi.panel(graphics, cx - 46, cy - 14, 92, 28);
            MahjongUi.text(graphics, font, TableScreen.roundName(view), cx - 42, cy - 10, 84, MahjongUi.ACCENT, true);
            MahjongUi.text(graphics, font, net.minecraft.network.chat.Component.translatable("ui.mchjong.remaining", view.remaining()),
                cx - 42, cy + 2, 84, MahjongUi.TEXT, true);
        }
    }

    private int stripWidth(TableView.Seat player, int seat, int width) {
        return (seat == viewer ? 0 : player.hand().size() * width
            + (!player.hand().isEmpty() && !player.melds().isEmpty() ? 3 : 0)
            + player.melds().stream().mapToInt(meld -> TileGui.meldWidth(meld, seat, width)).sum())
            + player.norths().size() * width;
    }

    private void river(GuiGraphics graphics, TableView view, int seat, Rect area, TileFacePreset preset) {
        var river = view.seats().get(seat).river().stream().filter(discard -> !discard.called()).toList();
        if (river.isEmpty()) return;
        int side = side(seat, viewer, players);
        int rows = (river.size() + 5) / 6;
        int tileWidth = 20;
        int span, depth;
        while (true) {
            span = 6 * tileWidth + tileHeight(tileWidth) - tileWidth;
            depth = rows * tileHeight(tileWidth);
            boolean fits = side % 2 == 0 ? span <= area.width() && depth <= area.height()
                : depth <= area.width() && span <= area.height();
            if (fits || tileWidth <= 2) break;
            tileWidth--;
        }
        int rotation = side * -90;
        int cx = area.x() + area.width() / 2, cy = area.y() + area.height() / 2;
        // Rivers grow outward from the center-facing edge, keeping early discards together.
        if (side == 0) cy = area.y() + depth / 2;
        if (side == 2) cy = area.bottom() - depth / 2;
        if (side == 1) cx = area.x() + depth / 2;
        if (side == 3) cx = area.right() - depth / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        int x = -span / 2;
        for (int index = 0; index < river.size(); index++) {
            var discard = river.get(index);
            if (index % 6 == 0) x = -span / 2;
            int height = tileHeight(tileWidth);
            int y = -depth / 2 + index / 6 * height;
            int occupiedWidth = discard.riichi() ? height : tileWidth;
            int occupiedHeight = discard.riichi() ? tileWidth : height;
            boolean focused = view.focus() != null && view.focus().tile() == discard.tile();
            TileGui.tile(graphics, discard.tile(), x, y, tileWidth, false, discard.riichi(), focused, preset);
            if (focused) graphics.renderOutline(x, y, occupiedWidth, occupiedHeight, MahjongUi.ACCENT);
            int px = x + occupiedWidth / 2, localY = y + occupiedHeight / 2;
            Point point = switch (side) {
                case 1 -> new Point(cx + localY, cy - px);
                case 2 -> new Point(cx - px, cy - localY);
                case 3 -> new Point(cx - localY, cy + px);
                default -> new Point(cx + px, cy + localY);
            };
            tiles.put(discard.tile(), point);
            x += occupiedWidth;
        }
        graphics.pose().popPose();
    }

    private static int tileHeight(int width) { return Math.round(width * TileMesh.HEIGHT / TileMesh.WIDTH); }
    private void rememberRotated(int tile, int cx, int cy, int x, int y, int side) {
        switch (side) {
            case 1 -> remember(tile, cx + y, cy - x);
            case 2 -> remember(tile, cx - x, cy - y);
            case 3 -> remember(tile, cx - y, cy + x);
            default -> remember(tile, cx + x, cy + y);
        }
    }
    private void remember(int tile, int x, int y) { if (tile >= 0) tiles.put(tile, new Point(x, y)); }
}
