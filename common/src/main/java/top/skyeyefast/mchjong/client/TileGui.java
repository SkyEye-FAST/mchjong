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
        drawTile(graphics, tile, x, y, width, back, sideways, marked, dimmed, 0, preset);
    }

    public static void tile3d(GuiGraphics graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                              boolean marked, boolean dimmed, int depth, TileFacePreset preset) {
        drawTile(graphics, tile, x, y, width, back, sideways, marked, dimmed, Math.max(1, depth), preset);
    }

    private static void drawTile(GuiGraphics graphics, int tile, int x, int y, int width, boolean back, boolean sideways,
                                 boolean marked, boolean dimmed, int depth, TileFacePreset preset) {
        int height = Math.round(width * TileMesh.HEIGHT / TileMesh.WIDTH);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        if (sideways) {
            graphics.pose().translate(height, 0, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(90));
        }
        if (depth > 0) {
            graphics.fill(depth + 1, depth + 2, width + depth + 1, height + depth + 2, 0x66000000);
            graphics.fill(width, depth, width + depth, height + depth, 0xff8f8067);
            graphics.fill(depth, height, width + depth, height + depth, 0xffc2b291);
            if (depth > 1) {
                graphics.fill(width + depth - 1, depth + 1, width + depth, height + depth - 1, 0xff665b4b);
                graphics.fill(depth + 1, height + depth - 1, width + depth - 1, height + depth, 0xff8e8069);
            }
        }
        graphics.fill(0, 0, width, height, 0xfff4eedb);
        if (back || tile < 0) {
            graphics.blit(TileMesh.BACK, 1, 1, width - 2, height - 2, 0, 0,
                TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT, TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT);
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
        meld(graphics, meld, owner, x, y, tileWidth, 0, preset);
    }

    public static void meld3d(GuiGraphics graphics, Meld meld, int owner, int x, int y, int tileWidth, int depth, TileFacePreset preset) {
        meld(graphics, meld, owner, x, y, tileWidth, Math.max(1, depth), preset);
    }

    private static void meld(GuiGraphics graphics, Meld meld, int owner, int x, int y, int tileWidth, int depth, TileFacePreset preset) {
        double scale = tileWidth / (double) TileMesh.WIDTH;
        for (var part : MeldLayout.of(meld, owner).parts()) {
            double span = part.sideways() ? TileMesh.HEIGHT : TileMesh.WIDTH;
            double tileDepth = part.sideways() ? TileMesh.WIDTH : TileMesh.HEIGHT;
            int px = x + (int) Math.round((part.x() - span / 2) * scale);
            int py = y + (int) Math.round((part.z() + TileMesh.HEIGHT / 2.0 - tileDepth / 2) * scale);
            if (depth > 0) tile3d(graphics, part.tile(), px, py, tileWidth, part.back(), part.sideways(), part.sideways(), false, depth, preset);
            else tile(graphics, part.tile(), px, py, tileWidth, part.back(), part.sideways(), part.sideways(), preset);
        }
    }
}
