package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import top.skyeyefast.mchjong.engine.SichuanView;
import top.skyeyefast.mchjong.item.SichuanDeck;
import top.skyeyefast.mchjong.world.TableGeometry;

final class SichuanImmersiveTable {
    private final int viewer;
    private final ImmersiveTable mesh = new ImmersiveTable();

    SichuanImmersiveTable(int viewer) { this.viewer = Math.max(0, viewer); }

    void render(GuiGraphics graphics, SichuanView view, SichuanDeck deck, net.minecraft.world.item.DyeColor cloth) {
        render(graphics, SichuanTableScene.immersive(view), view.viewerSeat(), deck, cloth);
    }
    void renderReplay(GuiGraphics graphics, top.skyeyefast.mchjong.engine.SichuanReplayPlayback.Frame frame,
                      SichuanDeck deck, net.minecraft.world.item.DyeColor cloth) {
        render(graphics, SichuanTableScene.replay(frame), -1, deck, cloth);
    }
    private void render(GuiGraphics graphics, java.util.List<SichuanTableScene.Piece> pieces, int privateSeat,
                        SichuanDeck deck, net.minecraft.world.item.DyeColor cloth) {
        mesh.begin(graphics, deck.preset(), deck.material(), deck.back(), deck.backPreset(),
            tile -> TileMesh.artwork(deck.tile(tile)));
        double edge = TableGeometry.FELT_HALF_WIDTH * 300;
        mesh.box(0, 0, 0, edge * 2 + 36, edge * 2 + 36, -18, -2, MahjongUi.INPUT, MahjongUi.SURFACE);
        int felt = cloth == null ? 0xff20584f : 0xff000000 | cloth.getTextureDiffuseColor();
        mesh.flat(0, -edge, -edge, edge, edge, 0, felt);
        mesh.cloth(0, -edge, -edge, edge, edge, .05);
        mesh.paint(graphics);
        for (var piece : pieces) {
            if (piece.area() == SichuanTableScene.Area.HAND && piece.seat() == privateSeat) continue;
            var local = TableGeometry.orient(piece.position().x, 0, piece.position().z, Math.floorMod(-piece.seat(), 4));
            int side = Math.floorMod(piece.seat() - viewer, 4);
            int span = Math.max(1, Math.round(TileMesh.WIDTH * piece.scale() * 300));
            if (piece.flat()) mesh.tile(piece.tile(), side, local.x * 300, local.z * 300, span, piece.back(), false, false, 0);
            else mesh.standing(piece.tile(), side, local.x * 300, local.z * 300, span);
        }
        mesh.paint(graphics);
    }
}
