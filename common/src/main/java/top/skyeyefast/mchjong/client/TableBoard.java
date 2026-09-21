package top.skyeyefast.mchjong.client;

import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
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

    private static final String[] WINDS = {"east", "south", "west", "north"};
    private static final int RIVER_GAP = 4;
    static final int MIN_RIVER_WIDTH = 8;
    static final int MIN_MELD_WIDTH = 10;
    private final Rect bounds, center;
    private final int viewer, players, actionsTop, riverWidth;
    private final int[] riverRows = new int[4];
    private final Map<Integer, Point> tiles = new HashMap<>();

    TableBoard(TableBoardState view, int left, int right, int top, int bottom, int actionsTop) {
        bounds = new Rect(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
        viewer = view.viewerSeat();
        players = view.players();
        this.actionsTop = actionsTop;
        for (int seat = 0; seat < players; seat++) riverRows[side(seat, viewer, players)] = Math.max(2,
            ((int) view.seats().get(seat).river().stream().filter(discard -> !discard.called()).count() + 5) / 6);
        int centerHeight = bounds.height() >= 200 ? 64 : 54;
        int verticalRows = riverRows[0] + riverRows[2];
        // Compress the score panel before sacrificing the face's logical pixels.
        boolean compact = centerHeight + 2 * RIVER_GAP + verticalRows * tileHeight(12) > bottom - top - 22;
        if (compact) centerHeight = 40;
        int usableTop = top + (compact ? 20 : 22);
        int usableHeight = Math.max(1, bottom - usableTop);
        if (centerHeight + verticalSpace(MIN_RIVER_WIDTH, centerHeight) > usableHeight) centerHeight = 16;
        int width = 20;
        while (width > MIN_RIVER_WIDTH && (centerHeight + verticalSpace(width, centerHeight) > usableHeight
            || riverSpan(width) + (riverRows[1] + riverRows[3]) * tileHeight(width) + 160 > bounds.width())) width--;
        riverWidth = width;
        int centerWidth = Math.max(84, riverSpan(width));
        center = new Rect((left + right - centerWidth) / 2,
            usableTop + Math.max(riverRows[2] * tileHeight(width) + RIVER_GAP, (riverSpan(width) - centerHeight + 1) / 2)
                + Math.max(0, usableHeight - centerHeight - verticalSpace(width, centerHeight)) / 2,
            centerWidth, centerHeight);
    }

    int riverTileWidth() { return riverWidth; }
    boolean scoresOnCards() { return center.height() == 16; }

    private int verticalSpace(int width, int height) {
        int side = (riverSpan(width) - height + 1) / 2;
        return Math.max(riverRows[0] * tileHeight(width) + RIVER_GAP, side)
            + Math.max(riverRows[2] * tileHeight(width) + RIVER_GAP, side);
    }

    static int side(int seat, int viewer, int players) {
        int relative = Math.floorMod(seat - viewer, players);
        return players == 3 && relative == 2 ? 3 : relative;
    }

    Rect card(int seat) {
        int width = Math.min(108, Math.max(48, (bounds.width() - center.width()) / 2
            - Math.max(riverRows[1], riverRows[3]) * tileHeight(riverWidth) - 66));
        int height = scoresOnCards() ? 32 : 20;
        return switch (side(seat, viewer, players)) {
            case 1 -> new Rect(bounds.right() - 58 - width, center.y() + center.height() / 2 - height / 2, width, height);
            case 3 -> new Rect(bounds.x() + 58, center.y() + center.height() / 2 - height / 2, width, height);
            case 2 -> new Rect(bounds.right() - 42 - width, bounds.y() + 28, width, height);
            default -> new Rect(bounds.x() + 42, bounds.bottom() - height - 2, width, height);
        };
    }

    Rect riverArea(int seat) {
        int span = riverSpan(riverWidth), depth = riverRows[side(seat, viewer, players)] * tileHeight(riverWidth);
        int cx = center.x() + center.width() / 2, cy = center.y() + center.height() / 2;
        return switch (side(seat, viewer, players)) {
            case 1 -> new Rect(center.right() + RIVER_GAP, cy - span / 2, depth, span);
            case 3 -> new Rect(center.x() - RIVER_GAP - depth, cy - span / 2, depth, span);
            case 2 -> new Rect(cx - span / 2, center.y() - RIVER_GAP - depth, span, depth);
            default -> new Rect(cx - span / 2, center.bottom() + RIVER_GAP, span, depth);
        };
    }

    Rect focus() {
        Rect card = card(viewer);
        return new Rect(card.x(), card.y() - 21, card.width(), 17);
    }

    Point point(int tile) { return tiles.get(tile); }

    void render(GuiGraphics graphics, TableBoardState view, TileFacePreset preset) {
        tiles.clear();
        for (int seat = 0; seat < players; seat++) {
            outerTiles(graphics, view, seat, preset);
            if (TableSettings.get().showRiver) river(graphics, view, seat, preset);
        }
        center(graphics, view);
    }

    private void outerTiles(GuiGraphics graphics, TableBoardState view, int seat, TileFacePreset preset) {
        var player = view.seats().get(seat);
        int side = side(seat, viewer, players);
        if (seat == viewer) {
            int x = card(seat).right() + 4;
            int width = Math.min(10, Math.max(2, (riverArea(seat).x() - x - 4) / 4));
            int y = bounds.bottom() - tileHeight(width);
            for (int tile : player.norths()) {
                TileGui.tile(graphics, tile, x, y, width, false, false, false, preset);
                remember(tile, x + width / 2, y + tileHeight(width) / 2);
                x += width;
            }
            return;
        }
        boolean vertical = side % 2 == 1;
        int bottom = side == 1 ? Math.min(bounds.bottom(), actionsTop) : bounds.bottom();
        int length = vertical ? bottom - bounds.y() - 8 : bounds.width() - 84;
        int width = Math.min(center.height() <= 40 && !vertical ? 12 : 14, outerTileWidth(player, seat, length));
        var rails = meldRails(player, seat, width, length);
        boolean addedKan = player.melds().stream().anyMatch(meld -> meld.type() == Meld.Type.ADDED_KAN);
        int railDepth = tileHeight(width) + (addedKan ? width : 0);
        int thickness = railDepth * rails.size();
        int cx = side == 3 ? bounds.x() + thickness / 2 : side == 1 ? bounds.right() - thickness / 2
            : bounds.x() + bounds.width() / 2;
        int cy = vertical ? (bounds.y() + bottom) / 2 : bounds.y() + thickness / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(-90 * side));
        int y = thickness / 2 - tileHeight(width);
        int meldSpan = 0;
        for (var meld : rails.getFirst()) meldSpan += TileGui.meldWidth(meld, seat, width);
        // The meld corner belongs to the owner's right, independently of the concealed hand.
        int corner = length / 2 - meldSpan;
        int handWidth = Math.min(width, Math.max(6, (length - meldSpan - player.norths().size() * width - 8)
            / Math.max(1, player.hand().size())));
        int handSpan = player.hand().size() * handWidth;
        int x = Math.max(-length / 2 + player.norths().size() * width + 4,
            Math.min(-handSpan / 2, corner - 4 - handSpan));
        for (int tile : player.hand()) {
            TileGui.tile(graphics, tile, x, y, handWidth, tile < 0, false, false, preset);
            rememberRotated(tile, cx, cy, x + handWidth / 2, y + tileHeight(handWidth) / 2, side);
            x += handWidth;
        }
        for (int row = 0; row < rails.size(); row++) {
            int span = 0;
            for (var meld : rails.get(row)) span += TileGui.meldWidth(meld, seat, width);
            x = length / 2 - span;
            int railY = y - row * railDepth;
            for (var meld : rails.get(row)) {
                TileGui.meld(graphics, meld, seat, x, railY, width, preset);
                int occupied = TileGui.meldWidth(meld, seat, width);
                for (int tile : meld.tiles()) rememberRotated(tile, cx, cy, x + occupied / 2, railY + tileHeight(width) / 2, side);
                x += occupied;
            }
        }
        x = -length / 2;
        for (int tile : player.norths()) {
            TileGui.tile(graphics, tile, x, y, width, false, false, false, preset);
            rememberRotated(tile, cx, cy, x + width / 2, y + tileHeight(width) / 2, side);
            x += width;
        }
        graphics.pose().popPose();
    }

    private static int stripWidth(TableView.Seat player, int seat, int width) {
        return player.hand().size() * width + 8
            + player.melds().stream().mapToInt(meld -> TileGui.meldWidth(meld, seat, width)).sum()
            + player.norths().size() * width;
    }

    static int outerTileWidth(TableView.Seat player, int seat, int length) {
        int width = 14;
        while (width > MIN_MELD_WIDTH && stripWidth(player, seat, width) > length) width--;
        return width;
    }

    static java.util.List<java.util.List<Meld>> meldRails(TableView.Seat player, int seat, int width, int length) {
        var rails = new java.util.ArrayList<java.util.List<Meld>>();
        java.util.List<Meld> row = new java.util.ArrayList<>();
        rails.add(row);
        int occupied = player.hand().size() * 6 + player.norths().size() * width + 8;
        for (var meld : player.melds()) {
            int span = TileGui.meldWidth(meld, seat, width);
            if (!row.isEmpty() && occupied + span > length) {
                row = new java.util.ArrayList<>();
                rails.add(row);
                occupied = 0;
            }
            row.add(meld);
            occupied += span;
        }
        return rails;
    }

    private void river(GuiGraphics graphics, TableBoardState view, int seat, TileFacePreset preset) {
        var river = view.seats().get(seat).river().stream().filter(discard -> !discard.called()).toList();
        if (river.isEmpty()) return;
        int side = side(seat, viewer, players);
        int tileWidth = riverWidth, span = riverSpan(tileWidth);
        int rotation = side * -90;
        Rect area = riverArea(seat);
        int cx = area.x() + area.width() / 2, cy = area.y() + area.height() / 2;
        // Rivers grow outward from the center-facing edge, keeping early discards together.
        if (side == 0) cy = area.y();
        if (side == 2) cy = area.bottom();
        if (side == 1) cx = area.x();
        if (side == 3) cx = area.right();
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(rotation));
        int x = -span / 2;
        for (int index = 0; index < river.size(); index++) {
            var discard = river.get(index);
            if (index % 6 == 0) x = -span / 2;
            int height = tileHeight(tileWidth);
            int y = index / 6 * height;
            int occupiedWidth = discard.riichi() ? height : tileWidth;
            int occupiedHeight = discard.riichi() ? tileWidth : height;
            boolean focused = view.focus() != null && view.focus().tile() == discard.tile();
            TileGui.tile(graphics, discard.tile(), x, y, tileWidth, false, discard.riichi(),
                focused || view.markTedashi() && !discard.tsumogiri(), preset);
            if (focused) graphics.renderOutline(x, y, occupiedWidth, occupiedHeight, MahjongUi.ACCENT);
            rememberRotated(discard.tile(), cx, cy, x + occupiedWidth / 2, y + occupiedHeight / 2, side);
            x += occupiedWidth;
        }
        graphics.pose().popPose();
    }

    private void center(GuiGraphics graphics, TableBoardState view) {
        var settings = TableSettings.get();
        var font = Minecraft.getInstance().font;
        int cx = center.x() + center.width() / 2, cy = center.y() + center.height() / 2;
        MahjongUi.panel(graphics, center.x(), center.y(), center.width(), center.height());
        for (int seat = 0; !scoresOnCards() && seat < players; seat++) {
            int side = side(seat, viewer, players);
            int length = side % 2 == 0 ? center.width() : center.height();
            int depth = side % 2 == 0 ? center.height() : center.width();
            Component label = Component.empty();
            if (settings.show(TableSettings.Information.WINDS)) label = Component.translatable("wind.mchjong."
                + WINDS[Math.floorMod(seat - view.dealer(), players)] + ".short");
            if (settings.show(TableSettings.Information.POINTS)) label = label.copy().append(" " + view.seats().get(seat).points());
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(-90 * side));
            boolean turn = seat == view.turn() && settings.show(TableSettings.Information.TURN);
            if (turn) graphics.fill(-length / 2 + 3, depth / 2 - 2, length / 2 - 3, depth / 2, MahjongUi.ACCENT);
            MahjongUi.text(graphics, font, label, -length / 2 + 3, depth / 2 - 12, length - 6,
                turn ? MahjongUi.ACCENT : MahjongUi.MUTED, true);
            graphics.pose().popPose();
        }
        if (center.height() <= 40) {
            var summary = Component.empty();
            if (settings.show(TableSettings.Information.ROUND)) summary.append(Component.translatable("ui.mchjong.round_short",
                Component.translatable("wind.mchjong." + WINDS[Math.min(3, view.round() / players)] + ".short"),
                view.round() % players + 1));
            if (settings.show(TableSettings.Information.REMAINING) && view.remaining() >= 0) {
                if (!summary.getString().isEmpty()) summary.append(" · ");
                summary.append(Integer.toString(view.remaining()));
            }
            MahjongUi.text(graphics, font, summary, center.x() + 12, cy - 4, center.width() - 24, MahjongUi.TEXT, true);
            return;
        }
        if (settings.show(TableSettings.Information.ROUND)) MahjongUi.text(graphics, font,
            Component.translatable("ui.mchjong.round_short", Component.translatable("wind.mchjong."
                + WINDS[Math.min(3, view.round() / players)]), view.round() % players + 1),
            center.x() + 14, cy - 10, center.width() - 28, MahjongUi.ACCENT, true);
        if (settings.show(TableSettings.Information.REMAINING) && view.remaining() >= 0) MahjongUi.text(graphics, font,
            Component.translatable("ui.mchjong.remaining_short", view.remaining()), center.x() + 14, cy + 2,
            center.width() - 28, MahjongUi.TEXT, true);
    }

    private static int riverSpan(int width) { return 5 * width + tileHeight(width); }
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
