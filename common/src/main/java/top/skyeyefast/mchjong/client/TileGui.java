package top.skyeyefast.mchjong.client;

import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import top.skyeyefast.mchjong.engine.Meld;

/** Tile faces use the same HD resource atlas as the physical tiles. */
public final class TileGui {
    private TileGui() {}

    public static void tile(GuiGraphics graphics, int tile, int x, int y, int width, boolean back, boolean sideways, boolean marked) {
        int height = Math.round(width * TileMesh.HEIGHT / TileMesh.WIDTH);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        if (sideways) {
            graphics.pose().translate(height, 0, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(90));
        }
        graphics.fill(0, 0, width, height, 0xfff4eedb);
        if (back || tile < 0) {
            graphics.blit(TileMesh.BACK, 1, 1, width - 2, height - 2, 0, 0,
                TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT, TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT);
        } else {
            int face = TileMesh.face(tile);
            graphics.blit(TileMesh.ATLAS, 1, 1, width - 2, height - 2,
                face % 8 * TileMesh.TILE_WIDTH, face / 8 * TileMesh.TILE_HEIGHT,
                TileMesh.TILE_WIDTH, TileMesh.TILE_HEIGHT, TileMesh.ATLAS_WIDTH, TileMesh.ATLAS_HEIGHT);
        }
        graphics.renderOutline(0, 0, width, height, marked ? 0xffffbd51 : 0xffa99c80);
        graphics.pose().popPose();
    }

    public static int meldWidth(Meld meld, int owner, int tileWidth) {
        return (int) Math.ceil(MeldLayout.of(meld, owner).width() * tileWidth / TileMesh.WIDTH);
    }

    public static void meld(GuiGraphics graphics, Meld meld, int owner, int x, int y, int tileWidth) {
        double scale = tileWidth / (double) TileMesh.WIDTH;
        for (var part : MeldLayout.of(meld, owner).parts()) {
            double span = part.sideways() ? TileMesh.HEIGHT : TileMesh.WIDTH;
            double depth = part.sideways() ? TileMesh.WIDTH : TileMesh.HEIGHT;
            tile(graphics, part.tile(), x + (int) Math.round((part.x() - span / 2) * scale),
                y + (int) Math.round((part.z() + TileMesh.HEIGHT / 2.0 - depth / 2) * scale),
                tileWidth, part.back(), part.sideways(), part.sideways());
        }
    }
}
