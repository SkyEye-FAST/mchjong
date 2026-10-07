package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

public final class MahjongTableRenderer implements BlockEntityRenderer<MahjongTableBlockEntity> {
    private enum Layer { BACK, BODY, FACE, PATTERN, OUTLINE }
    public MahjongTableRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(MahjongTableBlockEntity table, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        RiichiView view = table.clientView();
        pose.pushPose();
        pose.translate(.5, 0, .5);
        FurnitureMesh.table(pose, buffers, light, table.wood(), table.equipment().hasCloth() ? table.equipment().clothColor() : null,
            table.getBlockState().is(top.skyeyefast.mchjong.world.MahjongContent.AUTO_TABLE));
        if (table.automatic() && (table.clientRoom() == null || table.clientRoom().lobby()))
            TableIndicator.renderStandby(pose, buffers, light);
        pose.popPose();
        if (table.clientVariant() == top.skyeyefast.mchjong.engine.MahjongVariant.TAIWAN) {
            if (table.clientTaiwanView() == null || table.clientTaiwanDeck() == null) return;
            var screen = TaiwanTableScreen.active(Minecraft.getInstance().screen);
            var active = screen != null && screen.tablePos().equals(table.getBlockPos()) ? screen : null;
            pose.pushPose();
            pose.translate(.5, 0, .5);
            TaiwanSceneRenderer.render(TaiwanTableScene.build(table.clientTaiwanView().game()), table.clientTaiwanDeck(),
                pose, buffers, light, piece -> active == null ? 0 : active.highlight(piece),
                piece -> active != null && active.selected(piece), TableAnimation.of(table), Util.getMillis());
            if (table.automatic()) TableIndicator.render(table.clientTaiwanView(), pose, buffers, light);
            pose.popPose();
            return;
        }
        if (table.clientVariant() == top.skyeyefast.mchjong.engine.MahjongVariant.SICHUAN) {
            if (table.clientSichuanView() == null || table.clientSichuanDeck() == null) return;
            var screen = SichuanTableScreen.active(Minecraft.getInstance().screen);
            var active = screen != null && screen.tablePos().equals(table.getBlockPos()) ? screen : null;
            pose.pushPose();
            pose.translate(.5, 0, .5);
            SichuanSceneRenderer.render(SichuanTableScene.build(table.clientSichuanView().game()), table.clientSichuanDeck(),
                pose, buffers, light, piece -> active == null ? 0 : active.highlight(piece),
                piece -> active != null && active.selected(piece), TableAnimation.of(table), Util.getMillis());
            if (table.automatic()) TableIndicator.render(table.clientSichuanView().game(), pose, buffers, light);
            pose.popPose();
            return;
        }
        if (table.clientVariant() == top.skyeyefast.mchjong.engine.MahjongVariant.MCR && table.clientMcrView() != null && table.clientMcrDeck() != null) {
            var screen = Minecraft.getInstance().screen instanceof McrTableScreen mcr
                && mcr.tablePos().equals(table.getBlockPos()) ? mcr : null;
            pose.pushPose();
            pose.translate(.5, 0, .5);
            McrSceneRenderer.render(McrTableScene.build(table.clientMcrView().game()), table.clientMcrDeck(),
                pose, buffers, light, piece -> screen == null ? 0 : screen.highlight(piece),
                piece -> screen != null && screen.selected(piece), TableAnimation.of(table), Util.getMillis());
            if (table.automatic()) TableIndicator.render(table.clientMcrView().game(), pose, buffers, light);
            pose.popPose();
            return;
        }
        if (view == null) return;
        long now = Util.getMillis();
        RiichiAnimation animation = RiichiAnimation.of(table);
        animation.accept(view, now);
        boolean animated = TableSettings.get().animations;
        var frames = animated ? animation.sample(now) : animation.settled();
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        // Opaque shells and furniture first; glass shells are sorted and blended with the glass body pass.
        boolean glass = table.equipment().material() == top.skyeyefast.mchjong.item.TileMaterial.GLASS;
        if (!glass) {
            tiles(table, frames, pose, buffers, light, Layer.BACK);
            tiles(table, frames, pose, buffers, light, Layer.BODY);
        }
        tiles(table, frames, pose, buffers, light, Layer.FACE);
        if (table.getBlockState().is(top.skyeyefast.mchjong.world.MahjongContent.AUTO_TABLE))
            TableIndicator.render(view, pose, buffers, light);
        RiichiDeposits.render(view, table.automatic(), animation, animated, now, pose, buffers, light);
        RiichiDice.renderWorld(view, pose, buffers, light);
        if (glass) {
            tiles(table, frames, pose, buffers, light, Layer.BACK);
            tiles(table, frames, pose, buffers, light, Layer.BODY);
        }
        tiles(table, frames, pose, buffers, light, Layer.PATTERN);
        tiles(table, frames, pose, buffers, light, Layer.OUTLINE);
        pose.popPose();
    }

    private static void tiles(MahjongTableBlockEntity table, java.util.List<RiichiAnimation.Frame> frames,
            PoseStack pose, MultiBufferSource buffers, int light, Layer layer) {
        var material = table.equipment().material();
        var back = table.equipment().back();
        var vertices = buffers.getBuffer(switch (layer) {
            case BACK -> TileRenderTypes.back(material, back);
            case BODY -> TileRenderTypes.body(material);
            case FACE -> TileRenderTypes.faces(table.equipment().preset());
            case PATTERN -> TileRenderTypes.backPattern(table.equipment().backPreset());
            case OUTLINE -> net.minecraft.client.renderer.RenderType.lines();
        });
        RiichiTableScreen screen = RiichiTableScreen.active(Minecraft.getInstance().screen);
        for (RiichiAnimation.Frame frame : frames) {
            RiichiTableScene.Piece piece = frame.piece();
            if (piece.area() == RiichiTableScene.Area.RIVER && !TableSettings.get().showRiver) continue;
            int highlight = layer == Layer.OUTLINE && screen != null ? screen.highlight(table.getBlockPos(), piece) : 0;
            if (layer == Layer.OUTLINE && highlight == 0) continue;
            pose.pushPose();
            boolean selected = screen != null && screen.selected(table.getBlockPos(), piece);
            var position = piece.position().add(screen == null ? net.minecraft.world.phys.Vec3.ZERO : screen.handlingOffset(table.getBlockPos(), piece));
            pose.translate(position.x, position.y + (selected ? 0.035 : 0), position.z);
            pose.mulPose(Axis.YP.rotationDegrees(piece.yaw()));
            pose.mulPose(Axis.XP.rotationDegrees(frame.pitch()));
            RiichiTableScene.DIMENSIONS.apply(pose);
            // A face-down tile turns the back image toward the table center, including during a flip.
            boolean faceDown = frame.pitch() > 0;
            switch (layer) {
                case FACE -> TileMesh.drawFace(pose, vertices, piece.tile(), piece.back(), light);
                case BODY -> TileMesh.drawBody(pose, vertices, light, material, back);
                case BACK -> TileMesh.drawBack(pose, vertices, faceDown, light, material, back);
                case PATTERN -> TileMesh.drawBackPattern(pose, vertices, faceDown, light);
                case OUTLINE -> TileMesh.drawOutline(pose, vertices, highlight);
            }
            pose.popPose();
        }
    }

    @Override public boolean shouldRenderOffScreen(MahjongTableBlockEntity table) { return true; }
    @Override public int getViewDistance() { return 32; }
}
