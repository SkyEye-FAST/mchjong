package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.mixin.GuiGraphicsExtractorAccessor;


/** Rule-independent tile solids, materials and depth painting through TableProjection. */
final class ImmersiveTable {
    private static final RenderPipeline GUI_DEPTH = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
        .withLocation("mchjong/gui_tile_depth")
        .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true)).build();
    static final double RATIO = TileMesh.HEIGHT / TileMesh.WIDTH;
    static double thickness(double width) { return width * TileMesh.DEPTH / TileMesh.WIDTH; }
    private int backColor;
    private int bodyColor;
    private Identifier backTexture;
    private Identifier backPattern;
    private boolean depthTest;
    record Vertex(double x, double z, double h) {}
    record Face(Vertex[] vertices, Identifier texture, float u0, float v0, float u1, float v1, int color, boolean contact) {
        double depth() {
            double sum = 0;
            for (var v : vertices) sum += .694 * v.z() + .72 * v.h();
            return sum / 4;
        }
    }
    private record ProjectedFace(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose,
                                 TableProjection.Point[] points, float[] depths, float u0, float v0, float u1, float v1,
                                 int color, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public void buildVertices(VertexConsumer out) {
            for (int i = 3; i >= 0; i--) {
                var p = points[i];
                out.addVertex(pose.m00() * p.x() + pose.m10() * p.y() + pose.m20(),
                    pose.m01() * p.x() + pose.m11() * p.y() + pose.m21(), depths[i])
                    .setUv(i == 0 || i == 3 ? u0 : u1, i < 2 ? v0 : v1)
                    .setColor(color);
            }
        }
        @Override public ScreenRectangle scissorArea() { return null; }
    }
    private final List<Face> faces = new ArrayList<>();

    private TileFacePreset preset;
    private java.util.function.IntUnaryOperator artworkIndex;

    void begin(GuiGraphicsExtractor graphics, TileFacePreset preset, TileMaterial material,
               net.minecraft.world.item.DyeColor dye, Identifier backPreset,
               java.util.function.IntUnaryOperator artworkIndex) {
        this.preset = preset;
        this.artworkIndex = artworkIndex;
        backColor = TileMesh.backColor(material, dye);
        bodyColor = TileMesh.bodyColor(material, dye);
        backTexture = TileRenderTypes.backTexture(material, dye);
        backPattern = TileBackPresets.texture(backPreset);
        depthTest = material != TileMaterial.GLASS;
        faces.clear();

    }

    int size() { return faces.size(); }

    /** Transform a queued solid while removing its stationary contact shadow. */
    void transformFrom(int first, java.util.function.UnaryOperator<Vertex> transform) {
        for (int i = faces.size() - 1; i >= first; i--) {
            var face = faces.get(i);
            if (face.contact()) { faces.remove(i); continue; }
            var vertices = new Vertex[4];
            for (int j = 0; j < 4; j++) vertices[j] = transform.apply(face.vertices()[j]);
            faces.set(i, new Face(vertices, face.texture(), face.u0(), face.v0(), face.u1(), face.v1(), face.color(), false));
        }
    }

    void cloth(int side, double x0, double z0, double x1, double z1, double h) {
        faces.add(new Face(rectangle(side, x0, z0, x1, z1, h),
            FurnitureMesh.CLOTH_PATTERN, 0, 0, 1, 1, 0xffffffff, false));
    }

    void paint(GuiGraphicsExtractor graphics) {
        paint(graphics, v -> TableProjection.project(v.x(), v.z(), v.h()), Face::depth, depthTest);
    }

    void paint(GuiGraphicsExtractor graphics, java.util.function.Function<Vertex, TableProjection.Point> projection,
                       java.util.function.ToDoubleFunction<Face> depth, boolean useDepth) {
        faces.sort(Comparator.comparingInt((Face face) -> face.contact() ? 0 : 1).thenComparingDouble(depth));
        for (var face : faces) {
            var points = new TableProjection.Point[4];
            var depths = new float[4];
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                points[i] = projection.apply(face.vertices()[i]);
                if (useDepth) {
                    var vertex = face.vertices()[i];
                    // Perspective scale is reciprocal camera distance and interpolates across the face.
                    depths[i] = (float) (-100 + 100 * (TableProjection.scale(vertex.z(), vertex.h()) - 1));
                }
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
                new ProjectedFace(useDepth ? GUI_DEPTH : RenderPipelines.GUI_TEXTURED, setup, pose, points, depths, face.u0(), face.v0(),
                    face.u1(), face.v1(), face.color(), bounds));
        }
        if (!faces.isEmpty()) graphics.nextStratum();
        faces.clear();
    }

    void tile(int tile, int side, double x, double z, int width, boolean back, boolean sideways, boolean dim, double h) {
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
    }

    void standing(int tile, int side, double x, double z, int w) {
        double d = thickness(w), h = w * RATIO;
        double scale = w / (double) TileMesh.WIDTH;
        double coreBack = TileMesh.CORE_BACK * scale, coreFront = TileMesh.CORE_FRONT * scale;
        contact(side, x, z, w, d);
        box(side, x, z + (coreBack - d / 2) / 2, w, coreBack + d / 2, 0, h, backColor, backColor);
        box(side, x, z + (coreBack + coreFront) / 2, w, coreFront - coreBack, 0, h, bodyColor, bodyColor);
        // Keep the opaque face plate closed so its rear is visible through glass.
        box(side, x, z + (coreFront + d / 2) / 2, w, d / 2 - coreFront, 0, h, 0xfff4f0e5, 0xfff4f0e5);
        artwork(new Vertex[]{vertex(side, x + w / 2.0 - 1, z - d / 2 - .1, h - 2),
            vertex(side, x - w / 2.0 + 1, z - d / 2 - .1, h - 2),
            vertex(side, x - w / 2.0 + 1, z - d / 2 - .1, 2),
            vertex(side, x + w / 2.0 - 1, z - d / 2 - .1, 2)}, -1, true, false);
        var front = new Vertex[]{vertex(side, x - w / 2.0, z + d / 2 + .1, h),
            vertex(side, x + w / 2.0, z + d / 2 + .1, h),
            vertex(side, x + w / 2.0, z + d / 2 + .1, 0),
            vertex(side, x - w / 2.0, z + d / 2 + .1, 0)};
        solid(front, 0xfff4f0e5);
        if (tile >= 0) {
            artwork(new Vertex[]{vertex(side, x - w / 2.0 + 1, z + d / 2 + .2, h - 2),
                vertex(side, x + w / 2.0 - 1, z + d / 2 + .2, h - 2),
                vertex(side, x + w / 2.0 - 1, z + d / 2 + .2, 2),
                vertex(side, x - w / 2.0 + 1, z + d / 2 + .2, 2)}, tile, false, false);
        }
    }

    void box(int side, double x, double z, double w, double d, double bottom, double top, int body, int cap) {
        Vertex[] low = rectangle(side, x - w / 2, z - d / 2, x + w / 2, z + d / 2, bottom);
        Vertex[] high = rectangle(side, x - w / 2, z - d / 2, x + w / 2, z + d / 2, top);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            solid(new Vertex[]{low[i], low[j], high[j], high[i]}, shade(body, i == 0 || i == 3 ? .74 : .9));
        }
        solid(high, cap);
    }

    static int shade(int color, double scale) {
        return (color & 0xff000000) | (int) (((color >> 16) & 255) * scale) << 16
            | (int) (((color >> 8) & 255) * scale) << 8 | (int) ((color & 255) * scale);
    }

    void flat(int side, double x0, double z0, double x1, double z1, double h, int color) {
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
            faces.add(new Face(vertices, backPattern, 0, 0, 1, 1, 0xffffffff, false));
            return;
        }
        int face = artworkIndex.applyAsInt(tile);
        faces.add(new Face(vertices, TileMesh.atlas(preset), (face % 8 * 256 + .5f) / 2048, (face / 8 * 384 + .5f) / 4096,
            (face % 8 * 256 + 255.5f) / 2048, (face / 8 * 384 + 383.5f) / 4096, dim ? 0xffa5afa9 : 0xffffffff, false));
    }
    private static Vertex[] rectangle(int side, double x0, double z0, double x1, double z1, double h) {
        return new Vertex[]{vertex(side, x0, z0, h), vertex(side, x1, z0, h), vertex(side, x1, z1, h), vertex(side, x0, z1, h)};
    }
    static Vertex vertex(int side, double x, double z, double h) {
        return switch (side) {
            case 1 -> new Vertex(z, -x, h);
            case 2 -> new Vertex(-x, -z, h);
            case 3 -> new Vertex(-z, x, h);
            default -> new Vertex(x, z, h);
        };
    }

}
