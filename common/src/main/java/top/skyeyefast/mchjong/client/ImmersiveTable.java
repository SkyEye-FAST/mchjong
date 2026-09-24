package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.mixin.GuiGraphicsExtractorAccessor;

/** Recipient-safe miniature 3D scene, projected into the fixed immersive canvas. */
final class ImmersiveTable {
    static final int RIVER_WIDTH = 32;
    private static final int RIVER_START = 140;
    private static final double RATIO = TileMesh.HEIGHT / TileMesh.WIDTH;
    static double thickness(double width) { return width * TileMesh.DEPTH / TileMesh.WIDTH; }
    private int backColor;
    private int bodyColor;
    private Identifier backTexture;
    private record Vertex(double x, double z, double h) {}
    private record Face(Vertex[] vertices, Identifier texture, float u0, float v0, float u1, float v1, int color, boolean contact) {
        double depth() {
            double sum = 0;
            for (var v : vertices) sum += .694 * v.z() + .72 * v.h();
            return sum / 4;
        }
    }
    private record ProjectedFace(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                                 TableProjection.Point[] points, float u0, float v0, float u1, float v1,
                                 int color, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public void buildVertices(VertexConsumer out) {
            for (int i = 3; i >= 0; i--) {
                var p = points[i];
                out.addVertexWith2DPose(pose, p.x(), p.y())
                    .setUv(i == 0 || i == 3 ? u0 : u1, i < 2 ? v0 : v1)
                    .setColor(color);
            }
        }
        @Override public ScreenRectangle scissorArea() { return null; }
    }
    private final List<Face> faces = new ArrayList<>();
    private final Map<Integer, TableBoard.Point> points = new HashMap<>();
    private final Map<Integer, Integer> widths = new HashMap<>();
    private record RiverPose(int side, double x, double z) {}
    private final Map<Integer, RiverPose> rivers = new HashMap<>();
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

    static TableBoard.Rect card(int side) {
        return switch (side) {
            case 1 -> new TableBoard.Rect(1090, 260, 166, 72);
            case 3 -> new TableBoard.Rect(24, 260, 166, 72);
            case 2 -> new TableBoard.Rect(1070, 84, 166, 72);
            default -> new TableBoard.Rect(28, 558, 180, 72);
        };
    }

    void render(GuiGraphicsExtractor graphics, TableBoardState view, TileFacePreset preset, int suppressed,
                TileMaterial material, net.minecraft.world.item.DyeColor dye) {
        this.preset = preset;
        backColor = TileMesh.backColor(material, dye);
        bodyColor = TileMesh.bodyColor(material, dye);
        backTexture = TileRenderTypes.backTexture(material, dye);
        points.clear(); widths.clear(); rivers.clear(); faces.clear();
        // The frame and cloth use exactly the same camera as the tile geometry.
        box(0, 0, 0, 1060, 890, -20, -5, 0xff0e252a, 0xff263f43);
        flat(0, -510, -425, 510, 425, 0, 0xff20584f);
        faces.add(new Face(rectangle(0, -510, -425, 510, 425, .05), FurnitureMesh.CLOTH_PATTERN, 0, 0, 1, 1, 0xffffffff, false));
        flat(0, -508, -423, 508, -420, .1, 0xff54857a);
        flat(0, -508, 420, 508, 423, .1, 0xff102f30);
        paint(graphics);
        box(0, 0, 0, 190, 192, 0, 8, 0xff101d23, 0xff52666b);
        flat(0, -87, -88, 87, 88, 8.1, 0xff30464c);
        flat(0, -72, -69, 72, 69, 8.2, 0xff101f29);
        paint(graphics);
        if (view.turn() >= 0 && TableSettings.get().show(TableSettings.Information.TURN))
            flat(side(view.turn()), -57, 81, 57, 88, 8.5, MahjongUi.ACCENT);
        for (int seat = 0; seat < players; seat++) {
            if (seat != viewer) outer(view.seats().get(seat), seat, view.layHandsOpen());
            else {
                int n = 0;
                for (int tile : view.seats().get(seat).norths()) tile(tile, 0, -270 + n++ * 29, 340, 27, false, false, false, 0);
                melds(seat, List.of(view.seats().get(seat).melds()));
            }
            if (TableSettings.get().showRiver) river(view, seat, suppressed);
        }
        paint(graphics);
    }

    private void paint(GuiGraphicsExtractor graphics) {
        paint(graphics, v -> TableProjection.project(v.x(), v.z(), v.h()), Face::depth);
    }

    private void paint(GuiGraphicsExtractor graphics, java.util.function.Function<Vertex, TableProjection.Point> projection,
                       java.util.function.ToDoubleFunction<Face> depth) {
        faces.sort(Comparator.comparingInt((Face face) -> face.contact() ? 0 : 1).thenComparingDouble(depth));
        for (var face : faces) {
            var points = new TableProjection.Point[4];
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                points[i] = projection.apply(face.vertices()[i]);
                minX = Math.min(minX, points[i].x()); minY = Math.min(minY, points[i].y());
                maxX = Math.max(maxX, points[i].x()); maxY = Math.max(maxY, points[i].y());
            }
            var texture = Minecraft.getInstance().getTextureManager().getTexture(face.texture());
            var setup = TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler());
            var pose = new Matrix3x2f(graphics.pose());
            int left = (int) Math.floor(minX), top = (int) Math.floor(minY);
            var bounds = new ScreenRectangle(left, top, Math.max(1, (int) Math.ceil(maxX) - left),
                Math.max(1, (int) Math.ceil(maxY) - top)).transformMaxBounds(pose);
            ((GuiGraphicsExtractorAccessor) (Object) graphics).mchjong$guiRenderState().addGuiElement(
                new ProjectedFace(RenderPipelines.GUI_TEXTURED, setup, pose, points, face.u0(), face.v0(),
                    face.u1(), face.v1(), face.color(), bounds));
        }
        if (!faces.isEmpty()) graphics.nextStratum();
        faces.clear();
    }

    private void outer(TableView.Seat player, int seat, boolean layHandsOpen) {
        int side = side(seat), w = 30;
        double rail = 410, halfLength = 300;
        var rails = outerRails(player, seat);
        double handX = handLeft(player, seat);
        for (int tile : player.hand()) {
            if (player.exposed() || layHandsOpen) tile(tile, side, handX + w / 2.0, rail, w, false, false, false, 0);
            else standing(tile, side, handX + w / 2.0, rail, w);
            handX += w;
        }
        melds(seat, rails);
        double x = -halfLength;
        for (int tile : player.norths()) {
            tile(tile, side, x, rail - 55, w, false, false, false, 0);
            x += w;
        }
    }

    private void melds(int seat, List<List<Meld>> rails) {
        int side = side(seat), w = 30;
        for (int row = 0; row < rails.size(); row++) {
            double x = 300;
            // The inner corner keeps wrapped melds clear of the adjacent river.
            double z = row == 0 ? 410 : 285;
            for (var meld : rails.get(row)) {
                x -= TileGui.meldWidth(meld, seat, w);
                for (var part : MeldLayout.of(meld, seat).parts()) {
                    double scale = w / (double) TileMesh.WIDTH;
                    tile(part.tile(), side, x + part.x() * scale, z + part.z() * scale, w, part.back(), part.sideways(), false, 0);
                }
                x -= 5;
            }
        }
    }

    private static double handLeft(TableView.Seat player, int seat) {
        double meldSpan = outerRails(player, seat).getFirst().stream().mapToDouble(m -> TileGui.meldWidth(m, seat, 30) + 5).sum();
        double handSpan = player.hand().size() * 30;
        return Math.min(-handSpan / 2, 300 - meldSpan - 18 - handSpan);
    }

    static double discardSourceX(TableView.Seat player, int seat, int tile, boolean tsumogiri) {
        int index = player.hand().indexOf(tile);
        // Hidden identities remain unknown; a draw still has a public end-of-hand position.
        double slot = index >= 0 ? index + .5 : tsumogiri ? player.hand().size() - .5 : player.hand().size() / 2.0;
        return handLeft(player, seat) + slot * 30;
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
            double z = RIVER_START + row * 50 + depth / 2;
            rivers.put(discard.tile(), new RiverPose(side(seat), x + width / 2, z));
            anchor(discard.tile(), side(seat), x + width / 2, z, thickness(RIVER_WIDTH), RIVER_WIDTH);
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
        double top = h + thickness(width);
        double scale = thickness(width) / TileMesh.DEPTH;
        double backLayer = (TileMesh.CORE_BACK + TileMesh.DEPTH / 2) * scale;
        double bodyLayer = (TileMesh.CORE_FRONT - TileMesh.CORE_BACK) * scale;
        double faceLayer = thickness(width) - backLayer - bodyLayer;
        boolean showBack = back || tile < 0;
        double bottomLayer = showBack ? faceLayer : backLayer;
        int faceColor = dim ? 0xffb1b9b2 : 0xfff4f0e5;
        int bottomColor = showBack ? faceColor : backColor;
        int topColor = showBack ? backColor : faceColor;
        contact(side, x, z, w, d);
        box(side, x, z, w, d, h, h + bottomLayer, bottomColor, bottomColor);
        box(side, x, z, w, d, h + bottomLayer, h + bottomLayer + bodyLayer, bodyColor, bodyColor);
        box(side, x, z, w, d, h + bottomLayer + bodyLayer, top, topColor, topColor);
        Vertex[] face = rectangle(side, x - w / 2 + 1, z - d / 2 + 1, x + w / 2 - 1, z + d / 2 - 1, top + .2);
        if (sideways) face = new Vertex[]{face[1], face[2], face[3], face[0]};
        artwork(face, tile, back, dim);
        anchor(tile, side, x, z, top, width);
    }

    private void standing(int tile, int side, double x, double z, int w) {
        double d = thickness(w), h = w * RATIO;
        contact(side, x, z, w, d);
        box(side, x, z, w, d, 0, h, bodyColor, backColor);
        artwork(new Vertex[]{vertex(side, x + w / 2.0 - 1, z - d / 2 - .1, h - 2),
            vertex(side, x - w / 2.0 + 1, z - d / 2 - .1, h - 2),
            vertex(side, x - w / 2.0 + 1, z - d / 2 - .1, 2),
            vertex(side, x + w / 2.0 - 1, z - d / 2 - .1, 2)}, -1, true, false);
        var front = new Vertex[]{vertex(side, x - w / 2.0, z + d / 2 + .1, h),
            vertex(side, x + w / 2.0, z + d / 2 + .1, h),
            vertex(side, x + w / 2.0, z + d / 2 + .1, 0),
            vertex(side, x - w / 2.0, z + d / 2 + .1, 0)};
        if (tile < 0) artwork(front, tile, true, false);
        else {
            solid(front, 0xfff4f0e5);
            artwork(new Vertex[]{vertex(side, x - w / 2.0 + 1, z + d / 2 + .2, h - 2),
                vertex(side, x + w / 2.0 - 1, z + d / 2 + .2, h - 2),
                vertex(side, x + w / 2.0 - 1, z + d / 2 + .2, 2),
                vertex(side, x - w / 2.0 + 1, z + d / 2 + .2, 2)}, tile, false, false);
        }
    }

    /** The moving tile lands using exactly the river's solid, camera and material. */
    void discard(GuiGraphicsExtractor graphics, int tile, TableHand.Point source, int sourceWidth,
                 double opponentX, boolean tsumogiri, boolean riichi, double fraction) {
        var target = rivers.get(tile);
        if (target == null) return;
        tile(tile, 0, 0, 0, RIVER_WIDTH, false, riichi, false, 0);
        faces.removeIf(Face::contact);
        anchor(tile, target.side(), target.x(), target.z(), thickness(RIVER_WIDTH), RIVER_WIDTH);
        double progress = ImmersiveMotion.smooth(fraction);
        double angle = riichi ? Math.PI / 2 * ImmersiveMotion.smooth((fraction - .65) / .35) : 0;
        paint(graphics, v -> {
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
                    410 + (thickness(RIVER_WIDTH) / 2 - v.h()) * scale,
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
        });
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
        faces.add(new Face(vertices, TileMesh.atlas(preset), TileMesh.SWATCH_U, TileMesh.SWATCH_V, TileMesh.SWATCH_U, TileMesh.SWATCH_V, color, false));
    }
    private void contact(int side, double x, double z, double w, double d) {
        faces.add(new Face(rectangle(side, x - w / 2 - 1, z - d / 2 - 1, x + w / 2 + 1, z + d / 2 + 1, .1),
            TileMesh.atlas(preset), TileMesh.SWATCH_U, TileMesh.SWATCH_V, TileMesh.SWATCH_U, TileMesh.SWATCH_V, 0x33000000, true));
    }
    private void artwork(Vertex[] vertices, int tile, boolean back, boolean dim) {
        if (back || tile < 0) {
            faces.add(new Face(vertices, backTexture, 0, 0, 1, 1, backColor, false));
            faces.add(new Face(vertices, TileMesh.BACK, 0, 0, 1, 1, 0xffffffff, false));
            return;
        }
        int face = TileMesh.face(tile);
        faces.add(new Face(vertices, TileMesh.atlas(preset), (face % 8 * 256 + .5f) / 2048, (face / 8 * 384 + .5f) / 4096,
            (face % 8 * 256 + 255.5f) / 2048, (face / 8 * 384 + 383.5f) / 4096, dim ? 0xffa5afa9 : 0xffffffff, false));
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
