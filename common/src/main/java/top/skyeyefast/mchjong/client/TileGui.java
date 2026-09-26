package top.skyeyefast.mchjong.client;

import com.mojang.math.Axis;
import java.util.Comparator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.DyeColor;
import net.minecraft.resources.Identifier;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;

/** Tile faces use the same HD resource atlas as the physical tiles. */
public final class TileGui {
    private TileGui() {}

    public static void tile(GuiGraphicsExtractor graphics, int tile, int x, int y, int width, boolean back, boolean sideways, boolean marked, TileFacePreset preset) {
        tile(graphics, tile, x, y, width, back, sideways, marked, false, preset, TileMaterial.BONE, null);
    }

    public static void tile(GuiGraphicsExtractor graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                            boolean marked, boolean dimmed, TileFacePreset preset) {
        tile(graphics, tile, x, y, width, back, sideways, marked, dimmed, preset, TileMaterial.BONE, null);
    }

    public static void tile(GuiGraphicsExtractor graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                            boolean marked, boolean dimmed, TileFacePreset preset, TileMaterial material, DyeColor dye) {
        tile(graphics, tile, x, y, width, back, sideways, marked, dimmed, preset, material, dye, TileBackPresets.DEFAULT);
    }
    public static void tile(GuiGraphicsExtractor graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                            boolean marked, boolean dimmed, TileFacePreset preset, TileMaterial material, DyeColor dye,
                            Identifier backPreset) {
        drawTile(graphics, tile, x, y, width, back, sideways, marked, dimmed, 0, preset, material, dye, backPreset);
    }

    public static void tile3d(GuiGraphicsExtractor graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                              boolean marked, boolean dimmed, int depth, TileFacePreset preset, TileMaterial material, DyeColor dye) {
        tile3d(graphics, tile, x, y, width, back, sideways, marked, dimmed, depth, preset, material, dye, TileBackPresets.DEFAULT);
    }
    public static void tile3d(GuiGraphicsExtractor graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                              boolean marked, boolean dimmed, int depth, TileFacePreset preset, TileMaterial material, DyeColor dye,
                              Identifier backPreset) {
        drawTile(graphics, tile, x, y, width, back, sideways, marked, dimmed, Math.max(1, depth), preset, material, dye, backPreset);
    }

    private static void drawTile(GuiGraphicsExtractor graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                                 boolean marked, boolean dimmed, int depth, TileFacePreset preset, TileMaterial material, DyeColor dye,
                                 Identifier backPreset) {
        int height = Math.round(width * TileMesh.HEIGHT / TileMesh.WIDTH);
        int bodyColor = TileMesh.bodyColor(material, dye);
        int backColor = TileMesh.backColor(material, dye);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        if (depth > 0) {
            int w = sideways ? height : width, h = sideways ? width : height;
            graphics.fill(0, h, w, h + depth, shade(bodyColor, .92));
            graphics.fill(0, h + depth - Math.max(1, depth / 3), w, h + depth, backColor);
        }
        if (sideways) {
            graphics.pose().translate(height, 0);
            graphics.pose().rotate((float) Math.toRadians(90));
        }
        if (back || tile < 0) {
            var texture = TileRenderTypes.backTexture(material, dye);
            int textureWidth = 16;
            int textureHeight = 16;
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture,
                1, 1, 0, 0, width - 2, height - 2,
                textureWidth, textureHeight, textureWidth, textureHeight, backColor);
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TileBackPresets.texture(backPreset),
                1, 1, 0, 0, width - 2, height - 2,
                TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT, TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT);
        } else {
            graphics.fill(0, 0, width, height, 0xfff4eedb);
            int face = TileMesh.face(tile);
            graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TileMesh.atlas(preset),
                1, 1, face % 8 * TileMesh.TILE_WIDTH, face / 8 * TileMesh.TILE_HEIGHT,
                width - 2, height - 2, TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT,
                TileMesh.ATLAS_WIDTH, TileMesh.ATLAS_HEIGHT);
        }
        if (depth > 0 && !dimmed) {
            graphics.fill(1, 1, width - 1, 2, 0x55ffffff);
            graphics.fill(1, 2, 2, height - 1, 0x33ffffff);
            graphics.fill(width - 2, 2, width - 1, height - 1, 0x33000000);
            graphics.fill(2, height - 2, width - 1, height - 1, 0x44000000);
        }
        if (dimmed) graphics.fill(1, 1, width - 1, height - 1, 0x660b1418);
        graphics.outline(0, 0, width, height, marked ? 0xffffbd51 : 0xffa99c80);
        graphics.pose().popMatrix();
    }

    private static int shade(int color, double scale) {
        return color & 0xff000000 | (int) (((color >> 16) & 255) * scale) << 16
            | (int) (((color >> 8) & 255) * scale) << 8 | (int) ((color & 255) * scale);
    }

    public static int meldWidth(Meld meld, int owner, int tileWidth) {
        return (int) Math.ceil(MeldLayout.of(meld, owner).width() * tileWidth / TileMesh.WIDTH);
    }

    public static void meld(GuiGraphicsExtractor graphics, Meld meld, int owner, int x, int y, int tileWidth, TileFacePreset preset) {
        meld(graphics, meld, owner, x, y, tileWidth, 0, preset, TileMaterial.BONE, null, TileBackPresets.DEFAULT);
    }

    public static void meld(GuiGraphicsExtractor graphics, Meld meld, int owner, int x, int y, int tileWidth, TileFacePreset preset,
                            TileMaterial material, DyeColor dye) {
        meld(graphics, meld, owner, x, y, tileWidth, 0, preset, material, dye, TileBackPresets.DEFAULT);
    }
    public static void meld(GuiGraphicsExtractor graphics, Meld meld, int owner, int x, int y, int tileWidth, TileFacePreset preset,
                            TileMaterial material, DyeColor dye, Identifier backPreset) {
        meld(graphics, meld, owner, x, y, tileWidth, 0, preset, material, dye, backPreset);
    }

    public static void meld3d(GuiGraphicsExtractor graphics, Meld meld, int owner, int x, int y, int tileWidth, int depth,
                              TileFacePreset preset, TileMaterial material, DyeColor dye) {
        meld(graphics, meld, owner, x, y, tileWidth, Math.max(1, depth), preset, material, dye, TileBackPresets.DEFAULT);
    }

    private static void meld(GuiGraphicsExtractor graphics, Meld meld, int owner, int x, int y, int tileWidth, int depth,
                             TileFacePreset preset, TileMaterial material, DyeColor dye, Identifier backPreset) {
        double scale = tileWidth / (double) TileMesh.WIDTH;
        // Paint the rear added-kan tile first so its body and shadow stay behind the called tile.
        for (var part : MeldLayout.of(meld, owner).parts().stream()
                .sorted(Comparator.comparingDouble(MeldLayout.Part::z).thenComparingDouble(MeldLayout.Part::x)).toList()) {
            double span = part.sideways() ? TileMesh.HEIGHT : TileMesh.WIDTH;
            double tileDepth = part.sideways() ? TileMesh.WIDTH : TileMesh.HEIGHT;
            int px = x + (int) Math.round((part.x() - span / 2) * scale);
            int py = y + (int) Math.round((part.z() + TileMesh.HEIGHT / 2.0 - tileDepth / 2) * scale);
            if (depth > 0) tile3d(graphics, part.tile(), px, py, tileWidth, part.back(), part.sideways(), part.sideways(), false,
                depth, preset, material, dye, backPreset);
            else tile(graphics, part.tile(), px, py, tileWidth, part.back(), part.sideways(), part.sideways(), false,
                preset, material, dye, backPreset);
        }
    }
}
