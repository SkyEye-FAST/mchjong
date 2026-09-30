package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import top.skyeyefast.mchjong.engine.McrView;
import top.skyeyefast.mchjong.item.McrDeck;
import top.skyeyefast.mchjong.world.TableGeometry;
import static top.skyeyefast.mchjong.client.ImmersiveTable.RATIO;

/** MCR immersive presentation consumes the same left-corner rails and rivers as the world scene. */
final class McrImmersiveTable {
    private final int viewer;
    private final ImmersiveTable mesh = new ImmersiveTable();

    McrImmersiveTable(int viewer) { this.viewer = Math.max(0, viewer); }

    void render(GuiGraphics graphics, McrView view, McrDeck deck, net.minecraft.world.item.DyeColor cloth) {
        mesh.begin(graphics, deck.preset(), deck.material(), deck.back(), deck.backPreset(),
            tile -> TileMesh.artwork(deck.tile(tile)));
        double edge = TableGeometry.FELT_HALF_WIDTH * 300;
        mesh.box(0, 0, 0, edge * 2 + 36, edge * 2 + 36, -18, -2, 0xff0e252a, 0xff263f43);
        int felt = cloth == null ? 0xff20584f : 0xff000000 | cloth.getTextureDiffuseColor();
        mesh.flat(0, -edge, -edge, edge, edge, 0, felt);
        mesh.cloth(0, -edge, -edge, edge, edge, .05);
        mesh.paint(graphics);
        for (var piece : McrTableScene.immersive(view)) {
            if (piece.area() == McrTableScene.Area.HAND && piece.seat() == view.viewerSeat()) continue;
            var local = TableGeometry.orient(piece.position().x, 0,
                piece.position().z, Math.floorMod(-piece.seat(), 4));
            int side = Math.floorMod(piece.seat() - viewer, 4);
            int w = Math.max(1, Math.round(TileMesh.WIDTH * piece.scale() * 300));
            double x = local.x * 300, z = local.z * 300;
            if (piece.flat()) {
                if (view.focus() != null && view.focus().tile() == piece.tile())
                    mesh.flat(side, x - w / 2.0 - 2, z - w * RATIO / 2 - 2, x + w / 2.0 + 2,
                        z + w * RATIO / 2 + 2, .1, MahjongUi.ACCENT);
                mesh.tile(piece.tile(), side, x, z, w, piece.back(), piece.yaw() != piece.seat() * 90, false, 0);
            } else mesh.standing(piece.tile(), side, x, z, w);
        }
        mesh.paint(graphics);
    }
}
