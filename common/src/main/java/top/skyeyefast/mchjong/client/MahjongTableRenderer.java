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
    private enum Layer { BACK, BODY, FACE, PATTERN, OUTLINE }
    public MahjongTableRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(MahjongTableBlockEntity table, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        TableView view = table.clientView();
        pose.pushPose();
        pose.translate(.5, 0, .5);
        FurnitureMesh.table(pose, buffers, light, table.wood(), table.equipment().hasCloth() ? table.equipment().clothColor() : null,
            table.getBlockState().is(top.skyeyefast.mchjong.world.MahjongContent.AUTO_TABLE));
        pose.popPose();
        if (view == null) return;
        long now = Util.getMillis();
        TableAnimation animation = TableAnimation.of(table);
        animation.accept(view, now);
        boolean animated = TableSettings.get().animations;
        var frames = animated ? animation.sample(now) : animation.settled();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        // Opaque shells and furniture first; glass shells are sorted and blended with the glass body pass.
        boolean glass = table.equipment().material() == top.skyeyefast.mchjong.item.TileMaterial.GLASS;
        tiles(table, frames, pose, buffers, light, Layer.BACK);
        if (!glass) tiles(table, frames, pose, buffers, light, Layer.BODY);
        tiles(table, frames, pose, buffers, light, Layer.FACE);
        if (table.getBlockState().is(top.skyeyefast.mchjong.world.MahjongContent.AUTO_TABLE))
            TableIndicator.render(view, pose, buffers, light);
        TableDeposits.render(view, table.automatic(), animation, animated, now, pose, buffers, light);
        TableDice.renderWorld(view, pose, buffers, light);
        if (glass) tiles(table, frames, pose, buffers, light, Layer.BODY);
        tiles(table, frames, pose, buffers, light, Layer.PATTERN);
        tiles(table, frames, pose, buffers, light, Layer.OUTLINE);
        pose.popPose();
    }

    private static void tiles(MahjongTableBlockEntity table, java.util.List<TableAnimation.Frame> frames,
            PoseStack pose, MultiBufferSource buffers, int light, Layer layer) {
        var material = table.equipment().material();
        var back = table.equipment().back();
        boolean glass = material == top.skyeyefast.mchjong.item.TileMaterial.GLASS;
        var vertices = buffers.getBuffer(switch (layer) {
            case BACK -> TileRenderTypes.back(material, back);
            case BODY -> TileRenderTypes.body(material);
            case FACE -> TileRenderTypes.faces(table.equipment().preset());
            case PATTERN -> TileRenderTypes.backPattern(table.equipment().backPreset());
            case OUTLINE -> net.minecraft.client.renderer.RenderType.lines();
        });
        TableScreen screen = TableScreen.active(Minecraft.getInstance().screen);
        for (TableAnimation.Frame frame : frames) {
            TableScene.Piece piece = frame.piece();
            if (piece.area() == TableScene.Area.RIVER && !TableSettings.get().showRiver) continue;
            int highlight = layer == Layer.OUTLINE && screen != null ? screen.highlight(table.getBlockPos(), piece) : 0;
            if (layer == Layer.OUTLINE && highlight == 0) continue;
            pose.pushPose();
            boolean selected = screen != null && screen.selected(table.getBlockPos(), piece);
            var position = piece.position().add(screen == null ? net.minecraft.world.phys.Vec3.ZERO : screen.handlingOffset(table.getBlockPos(), piece));
            pose.translate(position.x, position.y + (selected ? 0.035 : 0), position.z);
            pose.mulPose(Axis.YP.rotationDegrees(piece.yaw()));
            pose.mulPose(Axis.XP.rotationDegrees(frame.pitch()));
            pose.scale(TableScene.TILE_SCALE, TableScene.TILE_SCALE, TableScene.TILE_SCALE);
            // A face-down tile turns the back image toward the table center, including during a flip.
            boolean faceDown = frame.pitch() > 0;
            switch (layer) {
                case FACE -> TileMesh.drawFace(pose, vertices, piece.tile(), piece.back(), light);
                case BODY -> TileMesh.drawBody(pose, vertices, light, material, back);
                case BACK -> TileMesh.drawBack(pose, vertices, piece.back() || piece.tile() < 0, faceDown, light, material, back);
                case PATTERN -> TileMesh.drawBackPattern(pose, vertices, piece.back() || piece.tile() < 0, faceDown, light);
                case OUTLINE -> TileMesh.drawOutline(pose, vertices, highlight);
            }
            pose.popPose();
        }
    }

    @Override public boolean shouldRenderOffScreen(MahjongTableBlockEntity table) { return true; }
    @Override public int getViewDistance() { return 32; }
}
