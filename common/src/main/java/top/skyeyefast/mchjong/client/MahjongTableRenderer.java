package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

public final class MahjongTableRenderer implements BlockEntityRenderer<MahjongTableBlockEntity> {
    private enum Layer { BACK, BODY, FACE }
    public MahjongTableRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(MahjongTableBlockEntity table, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        TableView view = table.clientView();
        pose.pushPose();
        pose.translate(.5, 0, .5);
        FurnitureMesh.table(pose, buffers, light, table.wood(), table.equipment().hasCloth() ? table.equipment().clothColor() : null,
            table.getBlockState().is(top.skyeyefast.mchjong.world.MahjongContent.AUTO_TABLE));
        for (int side = 0; side < 4; side++) if (table.equipment().stickCount(side) > 0) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-side * 90));
            pose.translate(.83, 1.006, TableGeometry.FELT_HALF_WIDTH + .0775);
            pose.scale(.7f, 1, 1);
            for (int count = 0; count < Math.min(4, table.equipment().stickCount(side)); count++) {
                FurnitureMesh.stick(pose, buffers, light, table.equipment().stickValue(side));
                pose.translate(0, .028, 0);
            }
            pose.popPose();
        }
        if (table.equipment().hasBox() && (view == null || view.phase() == top.skyeyefast.mchjong.engine.Game.Phase.LOBBY)) {
            pose.pushPose();
            pose.translate(0, TableGeometry.FELT_Y + .02, -TableScene.HAND_Z);
            pose.scale(.45f, .45f, .45f);
            FurnitureMesh.box(pose, buffers, light);
            pose.popPose();
        }
        pose.popPose();
        if (view == null) return;
        long now = Util.getMillis();
        TableAnimation animation = TableAnimation.of(table);
        animation.accept(view, now);
        boolean animated = TableSettings.get().animations;
        var frames = animated ? animation.sample(now) : animation.settled();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        // Opaque backs and furniture first; glass is sorted and blended after the opaque display.
        boolean glass = table.equipment().material() == top.skyeyefast.mchjong.item.TileMaterial.GLASS;
        tiles(table, frames, pose, buffers, light, Layer.BACK);
        if (!glass) tiles(table, frames, pose, buffers, light, Layer.BODY);
        tiles(table, frames, pose, buffers, light, Layer.FACE);
        if (table.getBlockState().is(top.skyeyefast.mchjong.world.MahjongContent.AUTO_TABLE))
            TableIndicator.render(view, pose, buffers, light);
        for (int seat = 0; seat < view.seats().size(); seat++) {
            if (view.seats().get(seat).riichi()) {
                var vertices = buffers.getBuffer(TileRenderTypes.FACES);
                double progress = animated ? animation.riichiProgress(seat, now) : 1;
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(seat * 90));
                pose.translate(0.65 * progress, TableGeometry.FELT_Y + 0.013 + Math.sin(progress * Math.PI) * 0.07,
                    TableScene.HAND_Z + (0.68 - TableScene.HAND_Z) * progress);
                TileMesh.box(pose, vertices, -0.12f, 0, -0.014f, 0.12f, 0.014f, 0.014f, 0xfff1ead9, light);
                TileMesh.box(pose, vertices, -0.01f, 0.014f, -0.01f, 0.01f, 0.016f, 0.01f, 0xffbb3737, light);
                pose.popPose();
            }
        }
        if (glass) tiles(table, frames, pose, buffers, light, Layer.BODY);
        pose.popPose();
    }

    private static void tiles(MahjongTableBlockEntity table, java.util.List<TableAnimation.Frame> frames,
            PoseStack pose, MultiBufferSource buffers, int light, Layer layer) {
        var material = table.equipment().material();
        boolean glass = material == top.skyeyefast.mchjong.item.TileMaterial.GLASS;
        var vertices = buffers.getBuffer(switch (layer) {
            case BACK -> TileRenderTypes.BACKS;
            case BODY -> glass ? TileRenderTypes.GLASS : TileRenderTypes.FACES;
            case FACE -> TileRenderTypes.FACES;
        });
        TableScreen screen = TableScreen.active(Minecraft.getInstance().screen);
        for (TableAnimation.Frame frame : frames) {
            TableScene.Piece piece = frame.piece();
            if (piece.area() == TableScene.Area.RIVER && !TableSettings.get().showRiver) continue;
            pose.pushPose();
            boolean selected = screen != null && screen.selected(table.getBlockPos(), piece);
            pose.translate(piece.position().x, piece.position().y + (selected ? 0.035 : 0), piece.position().z);
            pose.mulPose(Axis.YP.rotationDegrees(piece.yaw()));
            pose.mulPose(Axis.XP.rotationDegrees(frame.pitch()));
            pose.scale(TableScene.TILE_SCALE, TableScene.TILE_SCALE, TableScene.TILE_SCALE);
            switch (layer) {
                case FACE -> TileMesh.drawFace(pose, vertices, piece.tile(), piece.back(), light);
                case BODY -> TileMesh.drawBody(pose, vertices, light, material);
                case BACK -> TileMesh.drawBack(pose, vertices, !glass && (piece.back() || piece.tile() < 0), light, table.equipment().back());
            }
            pose.popPose();
        }
    }

    @Override public boolean shouldRenderOffScreen(MahjongTableBlockEntity table) { return true; }
    @Override public int getViewDistance() { return 32; }
}
