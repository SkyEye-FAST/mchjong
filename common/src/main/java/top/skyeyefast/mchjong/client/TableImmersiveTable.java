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

/** Native immersive public rails, rivers, deposits and presentation motion. */
final class TableImmersiveTable {
    static final int RIVER_WIDTH = 32;
    private static final int RIVER_START = 120;
    private final ImmersiveTable mesh;
    private final TileDimensions dimensions;
    private final double ratio;
    private final double tileWidth;
    private final Map<Integer, ChineseTableLayout.PublicArea> publicAreas = new HashMap<>();
    private final Map<Integer, List<net.minecraft.world.phys.Vec3>> riverPositions = new HashMap<>();
    private static final double WORLD_SCALE = 320;
    private double thickness(double width) { return mesh.thickness(width); }
    private final Map<Integer, TableBoard.Point> points = new HashMap<>();
    private final Map<Integer, Integer> widths = new HashMap<>();
    private record RiverPose(int side, double x, double z) {}
    private final Map<Integer, RiverPose> rivers = new HashMap<>();
    private final int viewer, players;
    private final int[] rows = new int[4];

    TableImmersiveTable(TableBoardState view) {
        dimensions = TileDimensions.of(view.seats().getFirst().variant());
        ratio = dimensions.ratio();
        tileWidth = dimensions.width() * WORLD_SCALE;
        mesh = new ImmersiveTable(dimensions);
        viewer = view.viewerSeat();
        players = view.players();
        for (int seat = 0; seat < players; seat++) rows[side(seat)] = Math.max(2,
            ((int) view.seats().get(seat).river().stream().filter(d -> !d.called()).count() + 5) / 6);
    }

    private int side(int seat) { return TableBoard.side(seat, viewer, players); }
    TableBoard.Point point(int tile) { return points.get(tile); }
    int width(int tile, int fallback) { return widths.getOrDefault(tile, fallback); }
    int riverWidth(int seat, int row) {
        var a = TableProjection.seat(side(seat), -tileWidth / 2, RIVER_START + row * tileWidth * ratio, thickness(tileWidth));
        var b = TableProjection.seat(side(seat), tileWidth / 2, RIVER_START + row * tileWidth * ratio, thickness(tileWidth));
        return (int) Math.round(Math.hypot(b.x() - a.x(), b.y() - a.y()));
    }

    TableBoard.Rect riverArea(int seat) {
        int side = side(seat);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (double x : new double[]{-114, 114}) for (double z : new double[]{RIVER_START, RIVER_START + rows[side] * 50})
            for (double h : new double[]{0, thickness(tileWidth)}) {
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
        points.clear(); widths.clear(); rivers.clear(); publicAreas.clear(); riverPositions.clear();
        if (view.seats().getFirst().variant() != top.skyeyefast.mchjong.engine.MahjongVariant.RIICHI) {
            var occupied = new java.util.ArrayList<ChineseTableLayout.Bounds>();
            occupied.add(new ChineseTableLayout.Bounds(net.minecraft.world.phys.Vec3.ZERO, 0, .53, .53));
            for (int seat = 0; seat < players; seat++) {
                var player = view.seats().get(seat);
                int owner = seat;
                var layouts = player.melds().stream().map(meld -> player.layout(meld, owner)).toList();
                if (seat == viewer) {
                    var handTiles = new java.util.ArrayList<>(player.hand());
                    if (player.drawn() >= 0 && handTiles.remove(Integer.valueOf(player.drawn()))) handTiles.add(player.drawn());
                    var hand = new TableHand(handTiles, player.drawn(), player.melds(), player.norths().size(),
                        seat, TableCanvas.WIDTH, TableCanvas.HEIGHT - 60, 58, true, player.variant());
                    foregroundClearance(occupied, seat, hand.span(), hand.top());
                }
                var area = ChineseTableLayout.publicArea(layouts, player.norths().size(), player.hand().size(),
                    player.drawn() != top.skyeyefast.mchjong.engine.Tile.ABSENT, seat, occupied, dimensions);
                publicAreas.put(seat, area);
                for (int i = 0; i < player.hand().size(); i++) occupied.add(ChineseTableLayout.bounds(
                    top.skyeyefast.mchjong.world.TableGeometry.orient(area.handLeft() + i * dimensions.width()
                        + (i == player.hand().size() - 1 && player.drawn() != top.skyeyefast.mchjong.engine.Tile.ABSENT
                            ? RiichiTableScene.DRAW_GAP : 0), 0,
                        ChineseTableLayout.handZ(dimensions), seat), seat * 90, player.exposed() || view.layHandsOpen(), dimensions));
            }
            var positions = ChineseTableLayout.rivers(view.seats().stream().map(player ->
                (int) player.river().stream().filter(d -> !d.called()).count()).toList(), occupied, dimensions);
            for (int seat = 0; seat < players; seat++) riverPositions.put(seat, positions.get(seat));
        }
        // The frame and cloth use exactly the same camera as the tile geometry.
        mesh.box(0, 0, 0, 1060, 890, -20, -5, 0xff0e252a, 0xff263f43);
        int felt = cloth == null ? 0xff20584f : 0xff000000 | cloth.getTextureDiffuseColor();
        mesh.flat(0, -510, -425, 510, 425, 0, felt);
        mesh.cloth(0, -510, -425, 510, 425, .05);
        mesh.flat(0, -508, -423, 508, -420, .1, shade(felt, .82));
        mesh.flat(0, -508, 420, 508, 423, .1, shade(felt, .82));
        mesh.paint(graphics);
        int half = (int) Math.round(TableIndicator.HALF_WIDTH * WORLD_SCALE);
        mesh.box(0, 0, 0, half * 2, half * 2, 0, 8, 0xff101d23, 0xff52666b);
        mesh.flat(0, -half + 8, -half + 8, half - 8, half - 8, 8.1, 0xff30464c);
        mesh.flat(0, -72, -69, 72, 69, 8.2, 0xff101f29);
        mesh.paint(graphics);
        if (view.turn() >= 0 && TableSettings.get().show(TableSettings.Information.TURN))
            mesh.flat(side(view.turn()), -57, 81, 57, 88, 8.5, MahjongUi.ACCENT);
        for (int seat = 0; seat < players; seat++) {
            if (seat != viewer) outer(view.seats().get(seat), seat, view.layHandsOpen(), deal, now);
            else {
                norths(seat, view.seats().get(seat));
                melds(seat, view.seats().get(seat));
            }
            if (TableSettings.get().showRiver) river(view, seat, suppressed);
        }
        mesh.paint(graphics);
    }

    private void outer(TableBoardState.Seat player, int seat, boolean layHandsOpen, TableDeal deal, long now) {
        int side = side(seat);
        double w = tileWidth;
        double rail = publicAreas.containsKey(seat) ? ChineseTableLayout.handZ(dimensions) * WORLD_SCALE : outerRail(side);
        double handX = publicAreas.containsKey(seat) ? publicAreas.get(seat).handLeft() * WORLD_SCALE - w / 2.0 : handLeft(player, seat, side);
        for (int index = 0; index < player.hand().size(); index++) {
            int tile = player.hand().get(index);
            if (index == player.hand().size() - 1 && player.drawn() != top.skyeyefast.mchjong.engine.Tile.ABSENT)
                handX += RiichiTableScene.DRAW_GAP * WORLD_SCALE;
            double fraction = deal == null ? 1 : deal.dealProgress(seat, index, now);
            if (fraction > 0) {
                if (fraction < 1) dealTile(tile, side, handX + w / 2.0, rail, player.exposed() || layHandsOpen, fraction);
                else if (player.exposed() || layHandsOpen) tile(tile, side, handX + w / 2.0, rail, w, false, false, false, 0);
                else mesh.standing(tile, side, handX + w / 2.0, rail, w);
            }
            handX += w;
        }
        melds(seat, player);
        norths(seat, player);
    }

    private void norths(int seat, TableBoardState.Seat player) {
        int side = side(seat);
        if (publicAreas.containsKey(seat)) {
            var positions = publicAreas.get(seat).flowers();
            for (int i = 0; i < positions.size(); i++) {
                var pos = positions.get(i);
                tile(player.norths().get(i), side, pos.x * WORLD_SCALE, pos.z * WORLD_SCALE, tileWidth, false, false, false, 0);
            }
            return;
        }
        double x = -meldCorner(side) + tileWidth * ratio + 5;
        double z = outerRail(side);
        for (int tile : player.norths()) {
            tile(tile, side, x + tileWidth / 2, z, tileWidth, false, false, false, 0);
            x += tileWidth;
        }
    }
    private void dealTile(int tile, int side, double x, double z, boolean open, double fraction) {
        int first = mesh.size();
        if (open) mesh.tile(tile, 0, 0, 0, tileWidth, fraction < .45, false, false, 0);
        else mesh.standing(fraction < .45 ? -1 : tile, 0, 0, 0, tileWidth);
        double progress = ImmersiveMotion.smooth(fraction);
        mesh.transformFrom(first, v -> {
            double sourceZ = open ? v.z() : tileWidth * ratio / 2 - v.h();
            double sourceH = open ? v.h() : thickness(tileWidth) / 2 + v.z();
            return vertex(side, 170 + (x - 170) * progress + v.x(),
                z - 75 + sourceZ + (75 + v.z() - sourceZ) * progress,
                sourceH + (v.h() - sourceH) * progress + Math.sin(Math.PI * fraction) * 24);
        });
    }

    private void melds(int seat, TableBoardState.Seat player) {
        int side = side(seat);
        double w = tileWidth;
        boolean left = player.variant() != top.skyeyefast.mchjong.engine.MahjongVariant.RIICHI;
        double x = (left ? -1 : 1) * meldCorner(side);
        double z = outerRail(side);
        int group = 0;
        for (var meld : player.melds()) {
            if (publicAreas.containsKey(seat)) {
                var origin = publicAreas.get(seat).melds().get(group++);
                x = origin.x * WORLD_SCALE;
                z = origin.z * WORLD_SCALE;
            }
            double span = player.layout(meld, seat).width() * w / dimensions.width();
            if (!left) x -= span;
            for (var part : player.layout(meld, seat).parts()) {
                double scale = publicAreas.containsKey(seat) ? WORLD_SCALE : w / (double) dimensions.width();
                tile(part.tile(), side, x + part.x() * scale, z + part.z() * scale, w, part.back(), part.sideways(), false, 0);
            }
            x += left ? span + 5 : -5;
        }
    }

    /** Reserve only the screen band needed by the present private hand at its rightmost position. */
    private static void foregroundClearance(java.util.List<ChineseTableLayout.Bounds> occupied,
                                            int seat, int span, int handTop) {
        if (span == 0) return;
        double screenY = handTop - 342;
        double z = screenY / (.72 + .694 * screenY / 1500) / WORLD_SCALE;
        double edge = top.skyeyefast.mchjong.world.TableGeometry.FELT_HALF_WIDTH;
        double screenX = TableCanvas.WIDTH - 24 - span - 8 - 640;
        double x = Math.min(screenX / TableProjection.scale(z * WORLD_SCALE, 0),
            screenX / TableProjection.scale(edge * WORLD_SCALE, 0)) / WORLD_SCALE;
        occupied.add(new ChineseTableLayout.Bounds(top.skyeyefast.mchjong.world.TableGeometry.orient(
            (x + edge) / 2, 0, (z + edge) / 2, seat), seat * 90, edge - x, edge - z));
    }

    /** Move the enlarged foreground rack only past public bodies that reach its screen band. */
    static int foregroundLeft(java.util.List<top.skyeyefast.mchjong.engine.Meld> melds, int flowers,
                              int handCount, boolean drawn, int owner, top.skyeyefast.mchjong.engine.MahjongVariant variant,
                              int centered, int span, int handTop) {
        var size = TileDimensions.of(variant);
        var layouts = melds.stream().map(m -> MeldLayout.of(m, owner, variant, true)).toList();
        var occupied = new java.util.ArrayList<ChineseTableLayout.Bounds>();
        foregroundClearance(occupied, owner, span, handTop);
        var area = ChineseTableLayout.publicArea(layouts, flowers, handCount, drawn, owner, occupied, size);
        var boxes = new java.util.ArrayList<double[]>();
        for (int group = 0; group < layouts.size(); group++) {
            var origin = area.melds().get(group);
            for (var part : layouts.get(group).parts()) boxes.add(new double[]{origin.x + part.x(), origin.z + part.z(),
                part.sideways() ? size.height() : size.width(), part.sideways() ? size.width() : size.height()});
        }
        for (var flower : area.flowers()) boxes.add(new double[]{flower.x, flower.z, size.width(), size.height()});
        double left = centered;
        for (var box : boxes) {
            double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) for (int h : new int[]{0, 1}) {
                var point = TableProjection.seat(0, (box[0] + x * box[2] / 2) * WORLD_SCALE,
                    (box[1] + z * box[3] / 2) * WORLD_SCALE, h * size.depth() * WORLD_SCALE);
                minX = Math.min(minX, point.x()); maxX = Math.max(maxX, point.x()); maxY = Math.max(maxY, point.y());
            }
            if (maxY >= handTop && minX < left + span && maxX + 8 > left) left = maxX + 8;
        }
        return (int) Math.ceil(left);
    }

    static double meldCorner(int side) { return (side % 2 == 0 ? 510 : 425) - 3; }

    static double outerRail(int side) {
        return (side % 2 == 0 ? 425 : 510) - 3 - TileDimensions.SMALL.height() * WORLD_SCALE / 2;
    }

    static double handLeft(TableBoardState.Seat player, int seat, int side) {
        double w = TileDimensions.of(player.variant()).width() * WORLD_SCALE;
        double handWidth = player.hand().size() * w + (player.drawn() != top.skyeyefast.mchjong.engine.Tile.ABSENT ? RiichiTableScene.DRAW_GAP * WORLD_SCALE : 0);
        if (player.variant() != top.skyeyefast.mchjong.engine.MahjongVariant.RIICHI) {
            var layouts = player.melds().stream().map(meld -> player.layout(meld, seat)).toList();
            return ChineseTableLayout.publicArea(layouts, player.norths().size(), player.hand().size(),
                player.drawn() != top.skyeyefast.mchjong.engine.Tile.ABSENT, seat, new java.util.ArrayList<>(),
                TileDimensions.of(player.variant())).handLeft() * WORLD_SCALE - w / 2;

        }
        double meldLeft = meldCorner(side);
        for (var meld : player.melds()) meldLeft -= player.layout(meld, seat).width() * WORLD_SCALE + 5;
        return Math.min(-handWidth / 2.0, meldLeft - 18 - handWidth);
    }

    static double discardSourceX(TableBoardState.Seat player, int seat, int viewer, int players, int tile, boolean tsumogiri) {
        int index = player.hand().indexOf(tile);
        // Hidden identities remain unknown; a draw still has a public end-of-hand position.
        double slot = index >= 0 ? index + .5 : tsumogiri ? player.hand().size() - .5 : player.hand().size() / 2.0;
        return handLeft(player, seat, TableBoard.side(seat, viewer, players)) + slot * TileDimensions.of(player.variant()).width() * WORLD_SCALE
            + (tsumogiri && player.drawn() != top.skyeyefast.mchjong.engine.Tile.ABSENT ? RiichiTableScene.DRAW_GAP * WORLD_SCALE : 0);
    }

    private void river(TableBoardState view, int seat, int suppressed) {
        var river = view.seats().get(seat).river().stream().filter(d -> !d.called()).toList();
        for (int i = 0; i < river.size(); i++) {
            int row = i / 6, start = row * 6;
            double x = -3 * tileWidth;
            for (int j = start; j < i; j++) x += river.get(j).riichi() ? tileWidth * ratio : tileWidth;
            var discard = river.get(i);
            double width = discard.riichi() ? tileWidth * ratio : tileWidth;
            double depth = discard.riichi() ? tileWidth : tileWidth * ratio;
            double z = RIVER_START + row * tileWidth * ratio + depth / 2;
            if (riverPositions.containsKey(seat)) {
                var position = riverPositions.get(seat).get(i);
                x = position.x * WORLD_SCALE - width / 2;
                z = position.z * WORLD_SCALE;
            }
            rivers.put(discard.tile(), new RiverPose(side(seat), x + width / 2, z));
            anchor(discard.tile(), side(seat), x + width / 2, z, thickness(tileWidth), tileWidth);
            if (discard.tile() == suppressed) continue;
            boolean focus = view.focus() != null && view.focus().tile() == discard.tile();
            if (focus || view.markTedashi() && !discard.tsumogiri())
                mesh.flat(side(seat), x - 2, z - depth / 2 - 2, x + width + 2, z + depth / 2 + 2, .2, MahjongUi.ACCENT);
            tile(discard.tile(), side(seat), x + width / 2, z, tileWidth, false, discard.riichi(),
                view.dimTsumogiri() && discard.tsumogiri(), 0);
        }
    }

    private void anchor(int tile, int side, double x, double z, double h, double width) {
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
        tile(tile, 0, 0, 0, tileWidth, false, riichi, false, 0);
        mesh.transformFrom(0, java.util.function.UnaryOperator.identity());
        anchor(tile, target.side(), target.x(), target.z(), thickness(tileWidth), tileWidth);
        double progress = ImmersiveMotion.smooth(fraction);
        double angle = riichi ? Math.PI / 2 * ImmersiveMotion.smooth((fraction - .65) / .35) : 0;
        mesh.paint(graphics, v -> {
            double localX = riichi ? v.z() : v.x(), localZ = riichi ? -v.x() : v.z();
            double x = localX * Math.cos(angle) - localZ * Math.sin(angle);
            double z = localX * Math.sin(angle) + localZ * Math.cos(angle);
            var end = TableProjection.seat(target.side(), target.x() + x, target.z() + z, v.h());
            TableProjection.Point start;
            if (source != null) {
                double scale = sourceWidth / (double) tileWidth;
                start = new TableProjection.Point((float) (source.x() + localX * scale),
                    (float) (source.y() + localZ * scale + (thickness(tileWidth) - v.h())
                        / thickness(tileWidth) * Math.max(2, sourceWidth / 8)));
            } else {
                double scale = 1;
                start = TableProjection.seat(target.side(), opponentX + localX * scale,
                    (publicAreas.isEmpty() ? outerRail(target.side()) : ChineseTableLayout.handZ(dimensions) * WORLD_SCALE) + (thickness(tileWidth) / 2 - v.h()) * scale,
                    (tileWidth * ratio / 2 - localZ) * scale);
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

    private void tile(int tile, int side, double x, double z, double width, boolean back, boolean sideways, boolean dim, double h) {
        mesh.tile(tile, side, x, z, width, back, sideways, dim, h);
        anchor(tile, side, x, z, h + thickness(width), width);
    }
}
