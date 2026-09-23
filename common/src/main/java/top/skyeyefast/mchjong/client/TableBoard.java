package top.skyeyefast.mchjong.client;

import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;

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
    private final boolean perspective;
    private final ImmersiveTable immersive;
    private final int[] riverRows = new int[4];
    private final Map<Integer, Point> tiles = new HashMap<>();
    private final Map<Integer, Integer> tileWidths = new HashMap<>();
    private TileMaterial material = TileMaterial.BONE;
    private net.minecraft.world.item.DyeColor back;

    TableBoard(TableBoardState view, int left, int right, int top, int bottom, int actionsTop) {
        this(view, left, right, top, bottom, actionsTop, false);
    }

    TableBoard(TableBoardState view, int left, int right, int top, int bottom, int actionsTop, boolean perspective) {
        bounds = new Rect(left, top, Math.max(1, right - left), Math.max(1, bottom - top));
        viewer = view.viewerSeat();
        players = view.players();
        this.actionsTop = actionsTop;
        this.perspective = perspective;
        immersive = perspective ? new ImmersiveTable(view) : null;
        for (int seat = 0; seat < players; seat++) riverRows[side(seat, viewer, players)] = Math.max(2,
            ((int) view.seats().get(seat).river().stream().filter(discard -> !discard.called()).count() + 5) / 6);
        if (perspective) {
            riverWidth = ImmersiveTable.RIVER_WIDTH;
            center = new Rect(568, 299, 144, 85);
            return;
        }
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
    int riverTileWidth(int seat) {
        return immersive == null ? riverWidth : immersive.riverWidth(seat, 0);
    }
    boolean scoresOnCards() { return center.height() == 16; }
    boolean perspective() { return perspective; }

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
        if (perspective) {
            return ImmersiveTable.card(side(seat, viewer, players));
        }
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
        if (immersive != null) return immersive.riverArea(seat);
        int side = side(seat, viewer, players);
        int span = riverSpan(riverWidth);
        int depth = riverRows[side] * tileHeight(riverWidth);
        int gap = RIVER_GAP;
        int cx = center.x() + center.width() / 2, cy = center.y() + center.height() / 2;
        return switch (side) {
            case 1 -> new Rect(center.right() + gap, cy - span / 2, depth, span);
            case 3 -> new Rect(center.x() - gap - depth, cy - span / 2, depth, span);
            case 2 -> new Rect(cx - span / 2, center.y() - gap - depth, span, depth);
            default -> new Rect(cx - span / 2, center.bottom() + gap, span, depth);
        };
    }

    Rect focus() {
        Rect card = card(viewer);
        return new Rect(card.x(), card.y() - 21, card.width(), 17);
    }

    Point point(int tile) { return immersive == null ? tiles.get(tile) : immersive.point(tile); }
    void discard(GuiGraphics graphics, int tile, TableHand.Point source, int sourceWidth, double opponentX,
                 boolean tsumogiri, boolean riichi, double fraction) {
        if (immersive != null) immersive.discard(graphics, tile, source, sourceWidth, opponentX, tsumogiri, riichi, fraction);
    }
    int tileWidth(int tile, int fallback) { return immersive == null ? tileWidths.getOrDefault(tile, fallback) : immersive.width(tile, fallback); }
    Point drawSource() {
        if (immersive != null) {
            var p = TableProjection.seat(0, 170, 350, 20);
            return new Point(Math.round(p.x()), Math.round(p.y()));
        }
        int offset = Math.min(240, Math.max(108, bounds.width() / 5));
        return new Point(bounds.x() + bounds.width() / 2 + offset, bounds.bottom() - Math.max(84, bounds.height() / 6));
    }

    void render(GuiGraphics graphics, TableBoardState view, TileFacePreset preset) {
        render(graphics, view, preset, Tile.ABSENT, TileMaterial.BONE, null);
    }

    void render(GuiGraphics graphics, TableBoardState view, TileFacePreset preset, int suppressedTile,
                TileMaterial material, net.minecraft.world.item.DyeColor back) {
        this.material = material;
        this.back = back;
        tiles.clear();
        tileWidths.clear();
        if (immersive != null) {
            immersive.render(graphics, view, preset, suppressedTile, material, back);
            immersiveCenter(graphics, view);
            return;
        }
        for (int seat = 0; seat < players; seat++) {
            outerTiles(graphics, view, seat, preset);
            if (TableSettings.get().showRiver) river(graphics, view, seat, preset, suppressedTile);
        }
        center(graphics, view);
    }

    private void outerTiles(GuiGraphics graphics, TableBoardState view, int seat, TileFacePreset preset) {
        var player = view.seats().get(seat);
        int side = side(seat, viewer, players);
        if (seat == viewer) {
            int x = card(seat).right() + 12;
            int width = Math.min(10, Math.max(2, (riverArea(seat).x() - x - 4) / 4));
            int y = bounds.bottom() - tileHeight(width) - 8;
            for (int tile : player.norths()) {
                TileGui.tile(graphics, tile, x, y, width, false, false, false, false, preset, material, back);
                remember(tile, x + width / 2, y + tileHeight(width) / 2);
                x += width;
            }
            return;
        }
        boolean vertical = side % 2 == 1;
        double scale = 1;
        int bottom = side == 1 ? Math.min(bounds.bottom(), actionsTop) : bounds.bottom();
        int length = (int) Math.floor((vertical ? bottom - bounds.y() - 8 : bounds.width() - 84) / scale);
        int maximum = center.height() <= 40 && !vertical ? 12 : 14;
        int width = Math.min(maximum, outerTileWidth(player, seat, length, maximum));
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
        graphics.pose().scale((float) scale, (float) scale, 1);
        int y = thickness / 2 - tileHeight(width);
        int meldSpan = 0;
        for (var meld : rails.get(0)) meldSpan += TileGui.meldWidth(meld, seat, width);
        // The meld corner belongs to the owner's right, independently of the concealed hand.
        int corner = length / 2 - meldSpan;
        int handWidth = Math.min(width, Math.max(6, (length - meldSpan - player.norths().size() * width - 16)
            / Math.max(1, player.hand().size())));
        int handSpan = player.hand().size() * handWidth;
        int x = Math.max(-length / 2 + player.norths().size() * width + 4,
            Math.min(-handSpan / 2, corner - 4 - handSpan));
        for (int tile : player.hand()) {
            tile(graphics, tile, x, y, handWidth, tile < 0, false, false, false, preset);
            rememberRotated(tile, cx, cy, x + handWidth / 2, y + tileHeight(handWidth) / 2, side, scale);
            x += handWidth;
        }
        for (int row = 0; row < rails.size(); row++) {
            int span = 0;
            for (var meld : rails.get(row)) span += TileGui.meldWidth(meld, seat, width);
            x = length / 2 - span;
            int railY = y - row * railDepth;
            for (var meld : rails.get(row)) {
                meld(graphics, meld, seat, x, railY, width, preset);
                int occupied = TileGui.meldWidth(meld, seat, width);
                for (int tile : meld.tiles()) rememberRotated(tile, cx, cy, x + occupied / 2, railY + tileHeight(width) / 2, side, scale);
                x += occupied;
            }
        }
        x = -length / 2;
        for (int tile : player.norths()) {
            tile(graphics, tile, x, y, width, false, false, false, false, preset);
            rememberRotated(tile, cx, cy, x + width / 2, y + tileHeight(width) / 2, side, scale);
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
        return outerTileWidth(player, seat, length, 14);
    }

    private static int outerTileWidth(TableView.Seat player, int seat, int length, int maximum) {
        int width = maximum;
        while (width > MIN_MELD_WIDTH && stripWidth(player, seat, width) > length) width--;
        return width;
    }

    static java.util.List<java.util.List<Meld>> meldRails(TableView.Seat player, int seat, int width, int length) {
        var rails = new java.util.ArrayList<java.util.List<Meld>>();
        java.util.List<Meld> row = new java.util.ArrayList<>();
        rails.add(row);
        int occupied = player.hand().size() * Math.max(6, Math.min(width, 12)) + player.norths().size() * width + 8;
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

    private void river(GuiGraphics graphics, TableBoardState view, int seat, TileFacePreset preset, int suppressedTile) {
        var river = view.seats().get(seat).river().stream().filter(discard -> !discard.called()).toList();
        if (river.isEmpty()) return;
        int side = side(seat, viewer, players);
        Rect area = riverArea(seat);
        int depth = 0;
        for (int row = 0, start = 0; start < river.size(); row++, start += 6) {
            int end = Math.min(start + 6, river.size());
            int rowWidth = riverWidth;
            int rowHeight = tileHeight(rowWidth);
            int rowSpan = 0;
            for (int index = start; index < end; index++) rowSpan += river.get(index).riichi() ? rowHeight : rowWidth;
            int cx = area.x() + area.width() / 2;
            int cy = area.y() + area.height() / 2;
            switch (side) {
                case 0 -> cy = area.y() + depth;
                case 1 -> cx = area.x() + depth;
                case 2 -> cy = area.bottom() - depth;
                case 3 -> cx = area.right() - depth;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(-90 * side));
            int x = -rowSpan / 2;
            int shadow = Math.max(3, rowWidth / 8) + row;
            graphics.fill(x - 5, 3, x + rowSpan + shadow + 5, rowHeight + shadow + 4,
                0x22000000 + Math.min(0x22000000, row * 0x05000000));
            graphics.hLine(x - 3, x + rowSpan + 3, -2, row == 0 ? 0x553f7168 : 0x332e5a53);
            for (int index = start; index < end; index++) {
                var discard = river.get(index);
                int occupiedWidth = discard.riichi() ? rowHeight : rowWidth;
                int occupiedHeight = discard.riichi() ? rowWidth : rowHeight;
                boolean focused = view.focus() != null && view.focus().tile() == discard.tile();
                int drawY = focused ? -Math.max(4, rowWidth / 8) : 0;
                if (discard.tile() != suppressedTile) {
                    tile(graphics, discard.tile(), x, drawY, rowWidth, false, discard.riichi(),
                        focused || view.markTedashi() && !discard.tsumogiri(), view.dimTsumogiri() && discard.tsumogiri(), preset);
                    if (focused) graphics.renderOutline(x, drawY, occupiedWidth, occupiedHeight, MahjongUi.ACCENT);
                }
                rememberRotated(discard.tile(), cx, cy, x + occupiedWidth / 2, drawY + occupiedHeight / 2, side);
                if (discard.tile() >= 0) tileWidths.put(discard.tile(), rowWidth);
                x += occupiedWidth;
            }
            graphics.pose().popPose();
            depth += rowHeight;
        }
    }

    int riverRowWidth(int seat, int row) {
        return immersive == null ? riverWidth : immersive.riverWidth(seat, row);
    }

    private void immersiveCenter(GuiGraphics graphics, TableBoardState view) {
        var settings = TableSettings.get();
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(640, 299, 0);
        graphics.pose().scale(2, 2, 1);
        if (settings.show(TableSettings.Information.ROUND)) MahjongUi.text(graphics, font,
            Component.translatable("ui.mchjong.round.short", Component.translatable("wind.mchjong."
                + WINDS[Math.min(3, view.round() / players)]), view.round() % players + 1), -42, 1, 84, MahjongUi.ACCENT, true);
        if (settings.show(TableSettings.Information.REMAINING) && view.remaining() >= 0) MahjongUi.text(graphics, font,
            Component.translatable("ui.mchjong.remaining.short", view.remaining()), -42, 17, 84, MahjongUi.TEXT, true);
        if (settings.show(TableSettings.Information.DEPOSITS)) {
            TableHud.stick(graphics, -30, 39, false);
            TableHud.stick(graphics, 5, 39, true);
            graphics.drawString(font, Integer.toString(view.honba()), -12, 36, MahjongUi.MUTED, false);
            graphics.drawString(font, Integer.toString(view.riichiSticks()), 23, 36, MahjongUi.MUTED, false);
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
            if (settings.show(TableSettings.Information.ROUND)) summary.append(Component.translatable("ui.mchjong.round.short",
                Component.translatable("wind.mchjong." + WINDS[Math.min(3, view.round() / players)] + ".short"),
                view.round() % players + 1));
            if (settings.show(TableSettings.Information.REMAINING) && view.remaining() >= 0) {
                if (!summary.getString().isEmpty()) summary.append("  ");
                summary.append(Integer.toString(view.remaining()));
            }
            MahjongUi.text(graphics, font, summary, center.x() + 12, cy - 4, center.width() - 24, MahjongUi.TEXT, true);
            return;
        }
        if (settings.show(TableSettings.Information.ROUND)) MahjongUi.text(graphics, font,
            Component.translatable("ui.mchjong.round.short", Component.translatable("wind.mchjong."
                + WINDS[Math.min(3, view.round() / players)]), view.round() % players + 1),
            center.x() + 14, cy - 10, center.width() - 28, MahjongUi.ACCENT, true);
        if (settings.show(TableSettings.Information.REMAINING) && view.remaining() >= 0) MahjongUi.text(graphics, font,
            Component.translatable("ui.mchjong.remaining.short", view.remaining()), center.x() + 14, cy + 2,
            center.width() - 28, MahjongUi.TEXT, true);
    }

    private static int riverSpan(int width) { return 5 * width + tileHeight(width); }
    private static int tileHeight(int width) { return Math.round(width * TileMesh.HEIGHT / TileMesh.WIDTH); }
    private void tile(GuiGraphics graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                      boolean marked, boolean dimmed, TileFacePreset preset) {
        TileGui.tile(graphics, tile, x, y, width, back, sideways, marked, dimmed, preset, material, this.back);
    }
    private void meld(GuiGraphics graphics, Meld meld, int owner, int x, int y, int width, TileFacePreset preset) {
        TileGui.meld(graphics, meld, owner, x, y, width, preset, material, back);
    }
    private void rememberRotated(int tile, int cx, int cy, int x, int y, int side) {
        rememberRotated(tile, cx, cy, x, y, side, 1);
    }
    private void rememberRotated(int tile, int cx, int cy, int x, int y, int side, double scale) {
        int sx = (int) Math.round(x * scale), sy = (int) Math.round(y * scale);
        switch (side) {
            case 1 -> remember(tile, cx + sy, cy - sx);
            case 2 -> remember(tile, cx - sx, cy - sy);
            case 3 -> remember(tile, cx - sy, cy + sx);
            default -> remember(tile, cx + sx, cy + sy);
        }
    }
    private void remember(int tile, int x, int y) { if (tile >= 0) tiles.put(tile, new Point(x, y)); }
}
