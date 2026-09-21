package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;

/** Recipient-safe miniature 3D scene, projected into the fixed immersive canvas. */
final class ImmersiveTable {
    static final int RIVER_WIDTH = 32;
    private static final double RATIO = TileMesh.HEIGHT / TileMesh.WIDTH;
    private int backColor;
    private int bodyColor;
    private ResourceLocation backTexture;
    private record Vertex(double x, double z, double h) {}
    private record Face(Vertex[] vertices, ResourceLocation texture, float u0, float v0, float u1, float v1, int color) {
        double depth() {
            double sum = 0;
            for (var v : vertices) sum += .694 * v.z() + .72 * v.h();
            return sum / 4;
        }
    }
    private final List<Face> faces = new ArrayList<>();
    private final Map<Integer, TableBoard.Point> points = new HashMap<>();
    private final Map<Integer, Integer> widths = new HashMap<>();
    private final int viewer, players;
    private final int[] rows = new int[4];
    private TileFacePreset preset;

    ImmersiveTable(TableBoardState view) {
        viewer = view.viewerSeat();
        players = view.players();
        for (int seat = 0; seat < players; seat++) rows[side(seat)] = Math.max(2,
            ((int) view.seats().get(seat).river().stream().filter(d -> !d.called()).count() + 5) / 6);
    }

    private int side(int seat) { return TableBoard.side(seat, viewer, players); }
    TableBoard.Point point(int tile) { return points.get(tile); }
    int width(int tile, int fallback) { return widths.getOrDefault(tile, fallback); }
    int riverWidth(int seat, int row) {
        var a = TableProjection.seat(side(seat), -16, 124 + row * 50, 9);
        var b = TableProjection.seat(side(seat), 16, 124 + row * 50, 9);
        return (int) Math.round(Math.hypot(b.x() - a.x(), b.y() - a.y()));
    }

    TableBoard.Rect riverArea(int seat) {
        int side = side(seat);
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (double x : new double[]{-114, 114}) for (double z : new double[]{124, 124 + rows[side] * 50})
            for (int h : new int[]{0, 10}) {
                var p = TableProjection.seat(side, x, z, h);
                minX = Math.min(minX, p.x()); maxX = Math.max(maxX, p.x());
                minY = Math.min(minY, p.y()); maxY = Math.max(maxY, p.y());
            }
        return new TableBoard.Rect((int) minX, (int) minY, (int) Math.ceil(maxX - minX), (int) Math.ceil(maxY - minY));
    }

    static TableBoard.Rect card(int side) {
        return switch (side) {
            case 1 -> new TableBoard.Rect(1090, 260, 166, 72);
            case 3 -> new TableBoard.Rect(24, 260, 166, 72);
            case 2 -> new TableBoard.Rect(1070, 84, 166, 72);
            default -> new TableBoard.Rect(28, 558, 180, 72);
        };
    }

    void render(GuiGraphics graphics, TableBoardState view, TileFacePreset preset, int suppressed,
                TileMaterial material, net.minecraft.world.item.DyeColor dye) {
        this.preset = preset;
        backColor = TileMesh.backColor(material, dye);
        bodyColor = TileMesh.bodyColor(material, dye);
        backTexture = TileRenderTypes.backTexture(material, dye);
        points.clear(); widths.clear(); faces.clear();
        // The frame and cloth use exactly the same camera as the tile geometry.
        box(0, 0, 0, 1060, 890, -20, -5, 0xff0e252a, 0xff263f43);
        flat(0, -510, -425, 510, 425, 0, 0xff20584f);
        flat(0, -508, -423, 508, -420, .1, 0xff54857a);
        flat(0, -508, 420, 508, 423, .1, 0xff102f30);
        paint(graphics);
        box(0, 0, 0, 190, 192, 0, 8, 0xff101d23, 0xff52666b);
        flat(0, -87, -88, 87, 88, 8.1, 0xff30464c);
        flat(0, -72, -69, 72, 69, 8.2, 0xff101f29);
        if (view.turn() >= 0 && TableSettings.get().show(TableSettings.Information.TURN))
            flat(side(view.turn()), -57, 81, 57, 88, 8.5, MahjongUi.ACCENT);
        for (int seat = 0; seat < players; seat++) {
            if (seat != viewer) outer(view.seats().get(seat), seat);
            else {
                int n = 0;
                for (int tile : view.seats().get(seat).norths()) tile(tile, 0, -270 + n++ * 29, 340, 27, false, false, false, 0);
            }
            if (TableSettings.get().showRiver) river(view, seat, suppressed);
        }
        paint(graphics);
    }

    private void paint(GuiGraphics graphics) {
        graphics.flush();
        faces.sort(Comparator.comparingDouble(Face::depth));
        for (var face : faces) {
            var out = graphics.bufferSource().getBuffer(TileRenderTypes.gui(face.texture()));
            for (int i = 3; i >= 0; i--) {
                var v = face.vertices()[i];
                var p = TableProjection.project(v.x(), v.z(), v.h());
                out.addVertex(graphics.pose().last().pose(), p.x(), p.y(), 0).setColor(face.color())
                    .setUv(i == 0 || i == 3 ? face.u0() : face.u1(), i < 2 ? face.v0() : face.v1()).setLight(0xf000f0);
            }
        }
        graphics.flush();
        faces.clear();
    }

    private void outer(TableView.Seat player, int seat) {
        int side = side(seat), w = 30;
        double rail = 410, halfLength = 300;
        var rails = outerRails(player, seat);
        double meldSpan = rails.getFirst().stream().mapToDouble(m -> TileGui.meldWidth(m, seat, w) + 5).sum();
        double handSpan = player.hand().size() * w;
        double handX = Math.min(-handSpan / 2, halfLength - meldSpan - 18 - handSpan);
        for (int tile : player.hand()) {
            if (tile < 0) standing(side, handX + w / 2.0, rail, w);
            else tile(tile, side, handX + w / 2.0, rail, w, false, false, false, 0);
            handX += w;
        }
        for (int row = 0; row < rails.size(); row++) {
            double span = rails.get(row).stream().mapToDouble(m -> TileGui.meldWidth(m, seat, w) + 5).sum();
            double x = halfLength - span;
            // The inner corner keeps wrapped melds clear of the adjacent river.
            double z = row == 0 ? rail : 285;
            for (var meld : rails.get(row)) {
                for (var part : MeldLayout.of(meld, seat).parts()) {
                    double scale = w / (double) TileMesh.WIDTH;
                    tile(part.tile(), side, x + part.x() * scale, z + part.z() * scale, w, part.back(), part.sideways(), false, 0);
                }
                x += TileGui.meldWidth(meld, seat, w) + 5;
            }
        }
        double x = -halfLength;
        for (int tile : player.norths()) {
            tile(tile, side, x, rail - 55, w, false, false, false, 0);
            x += w;
        }
    }

    static List<List<Meld>> outerRails(TableView.Seat player, int seat) {
        var rails = new ArrayList<List<Meld>>();
        List<Meld> row = new ArrayList<>();
        rails.add(row);
        int occupied = player.hand().size() * 30 + 18;
        for (var meld : player.melds()) {
            int width = TileGui.meldWidth(meld, seat, 30) + 5;
            if (!row.isEmpty() && occupied + width > 600) {
                row = new ArrayList<>(); rails.add(row); occupied = 0;
            }
            row.add(meld); occupied += width;
        }
        return rails;
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
            double z = 124 + row * 50 + depth / 2;
            anchor(discard.tile(), side(seat), x + width / 2, z, 10, RIVER_WIDTH);
            if (discard.tile() == suppressed) continue;
            boolean focus = view.focus() != null && view.focus().tile() == discard.tile();
            if (focus || view.markTedashi() && !discard.tsumogiri())
                flat(side(seat), x - 2, z - depth / 2 - 2, x + width + 2, z + depth / 2 + 2, .2, MahjongUi.ACCENT);
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

    private void tile(int tile, int side, double x, double z, int width, boolean back, boolean sideways, boolean dim, double h) {
        double w = sideways ? width * RATIO : width, d = sideways ? width : width * RATIO;
        flat(side, x - w / 2 + 3, z - d / 2 + 4, x + w / 2 + 4, z + d / 2 + 5, h + .1, 0x44000000);
        box(side, x, z, w - .7, d - .7, h, h + 3, bodyColor, bodyColor);
        box(side, x, z, w - .7, d - .7, h + 3, h + 10, dim ? 0xffa0a9a5 : 0xffe1ded0,
            dim ? 0xffb1b9b2 : 0xfff4f0e5);
        Vertex[] face = rectangle(side, x - w / 2 + 1, z - d / 2 + 1, x + w / 2 - 1, z + d / 2 - 1, h + 10.2);
        if (sideways) face = new Vertex[]{face[1], face[2], face[3], face[0]};
        artwork(face, tile, back, dim);
        anchor(tile, side, x, z, h + 10, width);
    }

    private void standing(int side, double x, double z, int w) {
        double d = 16, h = w * RATIO;
        flat(side, x - w / 2.0, z - d / 2, x + w / 2.0 + 8, z + d / 2 + 15, .1, 0x44000000);
        box(side, x, z, w - .7, d, 0, h, bodyColor, bodyColor);
        artwork(new Vertex[]{vertex(side, x + w / 2.0 - 1, z - d / 2 - .1, h - 2),
            vertex(side, x - w / 2.0 + 1, z - d / 2 - .1, h - 2),
            vertex(side, x - w / 2.0 + 1, z - d / 2 - .1, 2),
            vertex(side, x + w / 2.0 - 1, z - d / 2 - .1, 2)}, -1, true, false);
    }

    private void box(int side, double x, double z, double w, double d, double bottom, double top, int body, int cap) {
        Vertex[] low = rectangle(side, x - w / 2, z - d / 2, x + w / 2, z + d / 2, bottom);
        Vertex[] high = rectangle(side, x - w / 2, z - d / 2, x + w / 2, z + d / 2, top);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            solid(new Vertex[]{low[i], low[j], high[j], high[i]}, shade(body, i == 0 || i == 3 ? .74 : .9));
        }
        solid(high, cap);
    }

    private static int shade(int color, double scale) {
        return (color & 0xff000000) | (int) (((color >> 16) & 255) * scale) << 16
            | (int) (((color >> 8) & 255) * scale) << 8 | (int) ((color & 255) * scale);
    }

    private void flat(int side, double x0, double z0, double x1, double z1, double h, int color) {
        solid(rectangle(side, x0, z0, x1, z1, h), color);
    }
    private void solid(Vertex[] vertices, int color) {
        faces.add(new Face(vertices, TileMesh.atlas(preset), TileMesh.SWATCH_U, TileMesh.SWATCH_V, TileMesh.SWATCH_U, TileMesh.SWATCH_V, color));
    }
    private void artwork(Vertex[] vertices, int tile, boolean back, boolean dim) {
        if (back || tile < 0) {
            faces.add(new Face(vertices, backTexture, 0, 0, 1, 1, backColor));
            return;
        }
        int face = TileMesh.face(tile);
        faces.add(new Face(vertices, TileMesh.atlas(preset), (face % 8 * 256 + .5f) / 2048, (face / 8 * 384 + .5f) / 4096,
            (face % 8 * 256 + 255.5f) / 2048, (face / 8 * 384 + 383.5f) / 4096, dim ? 0xffa5afa9 : 0xffffffff));
    }
    private static Vertex[] rectangle(int side, double x0, double z0, double x1, double z1, double h) {
        return new Vertex[]{vertex(side, x0, z0, h), vertex(side, x1, z0, h), vertex(side, x1, z1, h), vertex(side, x0, z1, h)};
    }
    private static Vertex vertex(int side, double x, double z, double h) {
        return switch (side) {
            case 1 -> new Vertex(z, -x, h);
            case 2 -> new Vertex(-x, -z, h);
            case 3 -> new Vertex(-z, x, h);
            default -> new Vertex(x, z, h);
        };
    }
}
