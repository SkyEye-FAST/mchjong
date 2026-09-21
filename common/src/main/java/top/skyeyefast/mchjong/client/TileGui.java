package top.skyeyefast.mchjong.client;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Tile faces use the same HD resource atlas as the physical tiles. */
public final class TileGui {
    private TileGui() {}

    public static void tile(GuiGraphics graphics, int tile, int x, int y, int width, boolean back, boolean sideways, boolean marked, TileFacePreset preset) {
        tile(graphics, tile, x, y, width, back, sideways, marked, false, preset);
    }

    public static void tile(GuiGraphics graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                            boolean marked, boolean dimmed, TileFacePreset preset) {
        drawTile(graphics, tile, x, y, width, back, sideways, marked, dimmed, 0, preset, 0xffffffff);
    }

    public static void tile3d(GuiGraphics graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                              boolean marked, boolean dimmed, int depth, TileFacePreset preset, int backColor) {
        drawTile(graphics, tile, x, y, width, back, sideways, marked, dimmed, Math.max(1, depth), preset, backColor);
    }

    private static void drawTile(GuiGraphics graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                                 boolean marked, boolean dimmed, int depth, TileFacePreset preset, int backColor) {
        int height = Math.round(width * TileMesh.HEIGHT / TileMesh.WIDTH);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        if (depth > 0) {
            int w = sideways ? height : width, h = sideways ? width : height;
            graphics.fill(depth + 1, depth + 2, w + depth + 1, h + depth + 2, 0x44000000);
            graphics.fill(w, 2, w + depth, h + depth, 0xffb3b5ad);
            graphics.fill(1, h, w + depth, h + depth, 0xffd5d7cd);
            graphics.fill(1, h + depth - 2, w + depth, h + depth, backColor);
        }
        if (sideways) {
            graphics.pose().translate(height, 0, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(90));
        }
        graphics.fill(0, 0, width, height, 0xfff4eedb);
        if (back || tile < 0) {
            if (depth > 0) graphics.setColor(((backColor >> 16) & 255) / 255f,
                ((backColor >> 8) & 255) / 255f, (backColor & 255) / 255f, 1);
            graphics.blit(TileMesh.BACK, 1, 1, width - 2, height - 2, 0, 0,
                TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT, TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT);
            if (depth > 0) graphics.setColor(1, 1, 1, 1);
        } else {
            int face = TileMesh.face(tile);
            graphics.blit(TileMesh.atlas(preset), 1, 1, width - 2, height - 2,
                face % 8 * TileMesh.TILE_WIDTH, face / 8 * TileMesh.TILE_HEIGHT,
                TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT, TileMesh.ATLAS_WIDTH, TileMesh.ATLAS_HEIGHT);
        }
        if (depth > 0 && !dimmed) {
            graphics.fill(1, 1, width - 1, 2, 0x55ffffff);
            graphics.fill(1, 2, 2, height - 1, 0x33ffffff);
            graphics.fill(width - 2, 2, width - 1, height - 1, 0x33000000);
            graphics.fill(2, height - 2, width - 1, height - 1, 0x44000000);
        }
        if (dimmed) graphics.fill(1, 1, width - 1, height - 1, 0x660b1418);
        graphics.renderOutline(0, 0, width, height, marked ? 0xffffbd51 : 0xffa99c80);
        graphics.pose().popPose();
    }

    public static int meldWidth(Meld meld, int owner, int tileWidth) {
        return (int) Math.ceil(MeldLayout.of(meld, owner).width() * tileWidth / TileMesh.WIDTH);
    }

    public static void meld(GuiGraphics graphics, Meld meld, int owner, int x, int y, int tileWidth, TileFacePreset preset) {
        meld(graphics, meld, owner, x, y, tileWidth, 0, preset, 0xffffffff);
    }

    public static void meld3d(GuiGraphics graphics, Meld meld, int owner, int x, int y, int tileWidth, int depth, TileFacePreset preset, int backColor) {
        meld(graphics, meld, owner, x, y, tileWidth, Math.max(1, depth), preset, backColor);
    }

    private static void meld(GuiGraphics graphics, Meld meld, int owner, int x, int y, int tileWidth, int depth, TileFacePreset preset, int backColor) {
        double scale = tileWidth / (double) TileMesh.WIDTH;
        for (var part : MeldLayout.of(meld, owner).parts()) {
            double span = part.sideways() ? TileMesh.HEIGHT : TileMesh.WIDTH;
            double tileDepth = part.sideways() ? TileMesh.WIDTH : TileMesh.HEIGHT;
            int px = x + (int) Math.round((part.x() - span / 2) * scale);
            int py = y + (int) Math.round((part.z() + TileMesh.HEIGHT / 2.0 - tileDepth / 2) * scale);
            if (depth > 0) tile3d(graphics, part.tile(), px, py, tileWidth, part.back(), part.sideways(), part.sideways(), false, depth, preset, backColor);
            else tile(graphics, part.tile(), px, py, tileWidth, part.back(), part.sideways(), part.sideways(), preset);
        }
    }
}
