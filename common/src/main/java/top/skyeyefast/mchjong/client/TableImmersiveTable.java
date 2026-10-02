package top.skyeyefast.mchjong.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;

import static top.skyeyefast.mchjong.client.ImmersiveTable.*;

/** Riichi immersive rails, rivers, deposits and presentation motion. */
final class TableImmersiveTable {
    static final int RIVER_WIDTH = 32;
    private static final int RIVER_START = 120;
    private final ImmersiveTable mesh = new ImmersiveTable();
    private final Map<Integer, TableBoard.Point> points = new HashMap<>();
    private final Map<Integer, Integer> widths = new HashMap<>();
    private record RiverPose(int side, double x, double z) {}
    private final Map<Integer, RiverPose> rivers = new HashMap<>();
    private final int viewer, players;
    private final int[] rows = new int[4];

    TableImmersiveTable(TableBoardState view) {
        viewer = view.viewerSeat();
        players = view.players();
        for (int seat = 0; seat < players; seat++) rows[side(seat)] = Math.max(2,
            ((int) view.seats().get(seat).river().stream().filter(d -> !d.called()).count() + 5) / 6);
    }

    private int side(int seat) { return TableBoard.side(seat, viewer, players); }
    TableBoard.Point point(int tile) { return points.get(tile); }
    int width(int tile, int fallback) { return widths.getOrDefault(tile, fallback); }
    int riverWidth(int seat, int row) {
        var a = TableProjection.seat(side(seat), -16, RIVER_START + row * 50, thickness(RIVER_WIDTH));
        var b = TableProjection.seat(side(seat), 16, RIVER_START + row * 50, thickness(RIVER_WIDTH));
        return (int) Math.round(Math.hypot(b.x() - a.x(), b.y() - a.y()));
    }

    TableBoard.Rect riverArea(int seat) {
        int side = side(seat);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (double x : new double[]{-114, 114}) for (double z : new double[]{RIVER_START, RIVER_START + rows[side] * 50})
            for (double h : new double[]{0, thickness(RIVER_WIDTH)}) {
                var p = TableProjection.seat(side, x, z, h);
                minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
                minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
            }
        return new TableBoard.Rect((int) minX, (int) minY, (int) Math.ceil(maxX - minX), (int) Math.ceil(maxY - minY));
    }

    void render(GuiGraphicsExtractor graphics, TableBoardState view, TileFacePreset preset, int suppressed,
                TileMaterial material, net.minecraft.world.item.DyeColor dye, Identifier backPreset,
                net.minecraft.world.item.DyeColor cloth, TableDeal deal, long now, java.util.function.IntUnaryOperator artwork) {
        mesh.begin(graphics, preset, material, dye, backPreset, artwork);
        points.clear(); widths.clear(); rivers.clear();
        // The frame and cloth use exactly the same camera as the tile geometry.
        mesh.box(0, 0, 0, 1060, 890, -20, -5, 0xff0e252a, 0xff263f43);
        int felt = cloth == null ? 0xff20584f : 0xff000000 | cloth.getTextureDiffuseColor();
        mesh.flat(0, -510, -425, 510, 425, 0, felt);
        mesh.cloth(0, -510, -425, 510, 425, .05);
        mesh.flat(0, -508, -423, 508, -420, .1, shade(felt, .82));
        mesh.flat(0, -508, 420, 508, 423, .1, shade(felt, .82));
        mesh.paint(graphics);
        int half = view.indicator() == null ? 95 : 110;
        mesh.box(0, 0, 0, half * 2, half * 2, 0, 8, 0xff101d23, 0xff52666b);
        mesh.flat(0, -half + 8, -half + 8, half - 8, half - 8, 8.1, 0xff30464c);
        mesh.flat(0, -72, -69, 72, 69, 8.2, 0xff101f29);
        mesh.paint(graphics);
        if (view.turn() >= 0 && TableSettings.get().show(TableSettings.Information.TURN))
            mesh.flat(side(view.turn()), -57, 81, 57, 88, 8.5, MahjongUi.ACCENT);
        for (int seat = 0; seat < players; seat++) {
            if (seat != viewer) outer(view.seats().get(seat), seat, view.layHandsOpen(), deal, now);
            else {
                norths(seat, view.seats().get(seat).norths());
                melds(seat, view.seats().get(seat));
            }
            if (TableSettings.get().showRiver) river(view, seat, suppressed);
        }
        mesh.paint(graphics);
    }

    private void outer(TableBoardState.Seat player, int seat, boolean layHandsOpen, TableDeal deal, long now) {
        int side = side(seat), w = 30;
        double rail = outerRail(side);
        double handX = handLeft(player, seat, side);
        for (int index = 0; index < player.hand().size(); index++) {
            int tile = player.hand().get(index);
            double fraction = deal == null ? 1 : deal.dealProgress(seat, index, now);
            if (fraction > 0) {
                if (fraction < 1) dealTile(tile, side, handX + w / 2.0, rail, player.exposed() || layHandsOpen, fraction);
                else if (player.exposed() || layHandsOpen) tile(tile, side, handX + w / 2.0, rail, w, false, false, false, 0);
                else mesh.standing(tile, side, handX + w / 2.0, rail, w);
            }
            handX += w;
        }
        melds(seat, player);
        norths(seat, player.norths());
    }

    private void norths(int seat, List<Integer> norths) {
        int side = side(seat);
        double x = -meldCorner(side) + 30 * RATIO + 5;
        for (int tile : norths) {
            tile(tile, side, x + 15, outerRail(side), 30, false, false, false, 0);
            x += 30;
        }
    }
    private void dealTile(int tile, int side, double x, double z, boolean open, double fraction) {
        int first = mesh.size();
        if (open) mesh.tile(tile, 0, 0, 0, 30, fraction < .45, false, false, 0);
        else mesh.standing(fraction < .45 ? -1 : tile, 0, 0, 0, 30);
        double progress = ImmersiveMotion.smooth(fraction);
        mesh.transformFrom(first, v -> {
            double sourceZ = open ? v.z() : 30 * RATIO / 2 - v.h();
            double sourceH = open ? v.h() : thickness(30) / 2 + v.z();
            return vertex(side, 170 + (x - 170) * progress + v.x(),
                z - 75 + sourceZ + (75 + v.z() - sourceZ) * progress,
                sourceH + (v.h() - sourceH) * progress + Math.sin(Math.PI * fraction) * 24);
        });
    }

    private void melds(int seat, TableBoardState.Seat player) {
        int side = side(seat), w = 30;
        double x = meldCorner(side);
        double z = outerRail(side);
        for (var meld : player.melds()) {
            x -= TileGui.meldWidth(player.layout(meld, seat), w);
            for (var part : player.layout(meld, seat).parts()) {
                double scale = w / (double) TileMesh.WIDTH;
                tile(part.tile(), side, x + part.x() * scale, z + part.z() * scale, w, part.back(), part.sideways(), false, 0);
            }
            x -= 5;
        }
    }

    static double meldCorner(int side) { return (side % 2 == 0 ? 510 : 425) - 3; }

    static double outerRail(int side) {
        return (side % 2 == 0 ? 425 : 510) - 3 - 30 * RATIO / 2;
    }

    static double handLeft(TableBoardState.Seat player, int seat, int side) {
        int handWidth = player.hand().size() * 30;
        double meldLeft = meldCorner(side);
        for (var meld : player.melds()) meldLeft -= TileGui.meldWidth(player.layout(meld, seat), 30) + 5;
        return Math.min(-handWidth / 2.0, meldLeft - 18 - handWidth);
    }

    static double discardSourceX(TableBoardState.Seat player, int seat, int viewer, int players, int tile, boolean tsumogiri) {
        int index = player.hand().indexOf(tile);
        // Hidden identities remain unknown; a draw still has a public end-of-hand position.
        double slot = index >= 0 ? index + .5 : tsumogiri ? player.hand().size() - .5 : player.hand().size() / 2.0;
        return handLeft(player, seat, TableBoard.side(seat, viewer, players)) + slot * 30;
    }

    private void river(TableBoardState view, int seat, int suppressed) {
        var river = view.seats().get(seat).river().stream().filter(d -> !d.called()).toList();
        for (int i = 0; i < river.size(); i++) {
            int row = i / 6, start = row * 6;
            double x = -96;
            for (int j = start; j < i; j++) x += river.get(j).riichi() ? RIVER_WIDTH * RATIO : RIVER_WIDTH;
            var discard = river.get(i);
            double width = discard.riichi() ? RIVER_WIDTH * RATIO : RIVER_WIDTH;
            double depth = discard.riichi() ? RIVER_WIDTH : RIVER_WIDTH * RATIO;
            double z = RIVER_START + row * 50 + depth / 2;
            rivers.put(discard.tile(), new RiverPose(side(seat), x + width / 2, z));
            anchor(discard.tile(), side(seat), x + width / 2, z, thickness(RIVER_WIDTH), RIVER_WIDTH);
            if (discard.tile() == suppressed) continue;
            boolean focus = view.focus() != null && view.focus().tile() == discard.tile();
            if (focus || view.markTedashi() && !discard.tsumogiri())
                mesh.flat(side(seat), x - 2, z - depth / 2 - 2, x + width + 2, z + depth / 2 + 2, .2, MahjongUi.ACCENT);
            tile(discard.tile(), side(seat), x + width / 2, z, RIVER_WIDTH, false, discard.riichi(),
                view.dimTsumogiri() && discard.tsumogiri(), 0);
        }
    }

    private void anchor(int tile, int side, double x, double z, double h, int width) {
        if (tile < 0) return;
        var p = TableProjection.seat(side, x, z, h);
        points.put(tile, new TableBoard.Point(Math.round(p.x()), Math.round(p.y())));
        var world = vertex(side, x, z, h);
        widths.put(tile, (int) Math.round(width * TableProjection.scale(world.z(), h)));
    }

    /** The moving tile lands using exactly the river's solid, camera and material. */
    void discard(GuiGraphicsExtractor graphics, int tile, TableHand.Point source, int sourceWidth,
                 double opponentX, boolean tsumogiri, boolean riichi, double fraction) {
        var target = rivers.get(tile);
        if (target == null) return;
        tile(tile, 0, 0, 0, RIVER_WIDTH, false, riichi, false, 0);
        mesh.transformFrom(0, java.util.function.UnaryOperator.identity());
        anchor(tile, target.side(), target.x(), target.z(), thickness(RIVER_WIDTH), RIVER_WIDTH);
        double progress = ImmersiveMotion.smooth(fraction);
        double angle = riichi ? Math.PI / 2 * ImmersiveMotion.smooth((fraction - .65) / .35) : 0;
        mesh.paint(graphics, v -> {
            double localX = riichi ? v.z() : v.x(), localZ = riichi ? -v.x() : v.z();
            double x = localX * Math.cos(angle) - localZ * Math.sin(angle);
            double z = localX * Math.sin(angle) + localZ * Math.cos(angle);
            var end = TableProjection.seat(target.side(), target.x() + x, target.z() + z, v.h());
            TableProjection.Point start;
            if (source != null) {
                double scale = sourceWidth / (double) RIVER_WIDTH;
                start = new TableProjection.Point((float) (source.x() + localX * scale),
                    (float) (source.y() + localZ * scale + (thickness(RIVER_WIDTH) - v.h())
                        / thickness(RIVER_WIDTH) * Math.max(2, sourceWidth / 8)));
            } else {
                double scale = 30.0 / RIVER_WIDTH;
                start = TableProjection.seat(target.side(), opponentX + localX * scale,
                    outerRail(target.side()) + (thickness(RIVER_WIDTH) / 2 - v.h()) * scale,
                    (RIVER_WIDTH * RATIO / 2 - localZ) * scale);
            }
            return ImmersiveMotion.interpolate(start, end, progress, fraction, tsumogiri);
        }, face -> {
            double depth = 0;
            for (var v : face.vertices()) {
                double localX = riichi ? v.z() : v.x(), localZ = riichi ? -v.x() : v.z();
                double x = localX * Math.cos(angle) - localZ * Math.sin(angle);
                double z = localX * Math.sin(angle) + localZ * Math.cos(angle);
                var world = vertex(target.side(), target.x() + x, target.z() + z, v.h());
                depth += .694 * world.z() + .72 * world.h();
            }
            return depth / 4;
        }, false);
    }

    private void tile(int tile, int side, double x, double z, int width, boolean back, boolean sideways, boolean dim, double h) {
        mesh.tile(tile, side, x, z, width, back, sideways, dim, h);
        anchor(tile, side, x, z, h + thickness(width), width);
    }
}
