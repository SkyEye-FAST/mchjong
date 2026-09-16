package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableGeometry;

public final class MahjongTableRenderer implements BlockEntityRenderer<MahjongTableBlockEntity> {
    private final Font font;
    public MahjongTableRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }

    @Override public void render(MahjongTableBlockEntity table, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        TableView view = table.clientView();
        if (view == null) return;
        var pieces = TableScene.build(view);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        // Batch by material rather than switching buffers for every tile in the wall.
        for (int pass = 0; pass < 2; pass++) {
            var vertices = buffers.getBuffer(pass == 0 ? TileRenderTypes.FACES : TileRenderTypes.BACKS);
            for (TableScene.Piece piece : pieces) {
                pose.pushPose();
                TableScreen screen = TableScreen.active(Minecraft.getInstance().screen);
                boolean selected = screen != null && screen.selected(table.getBlockPos(), piece);
                pose.translate(piece.position().x, piece.position().y + (selected ? 0.035 : 0), piece.position().z);
                pose.mulPose(Axis.YP.rotationDegrees(piece.yaw()));
                if (piece.flat()) pose.mulPose(Axis.XP.rotationDegrees(-90));
                pose.scale(TableScene.TILE_SCALE, TableScene.TILE_SCALE, TableScene.TILE_SCALE);
                if (pass == 0) TileMesh.drawFace(pose, vertices, piece.tile(), piece.back(), light);
                else TileMesh.drawBack(pose, vertices, piece.back() || piece.tile() < 0, light);
                pose.popPose();
            }
        }
        var vertices = buffers.getBuffer(TileRenderTypes.FACES);
        TileMesh.box(pose, vertices, -0.25f, (float) TableGeometry.FELT_Y, -0.25f,
            0.25f, (float) TableGeometry.FELT_Y + 0.035f, 0.25f, 0xff243c40, light);
        TileMesh.box(pose, vertices, -0.22f, (float) TableGeometry.FELT_Y + 0.035f, -0.22f,
            0.22f, (float) TableGeometry.FELT_Y + 0.038f, 0.22f, 0xff16272d, light);
        for (int seat = 0; seat < view.seats().size(); seat++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(seat * 90));
            pose.translate(0, TableGeometry.FELT_Y + 0.040, 0.17);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(0.006f, -0.006f, 0.006f);
            int wind = Math.floorMod(seat - view.dealer(), view.rules().players());
            Component label = Component.translatable("wind.mchjong." + new String[]{"east","south","west","north"}[wind])
                .append(" " + view.seats().get(seat).points());
            font.drawInBatch(label, -font.width(label) / 2f, -4, seat == view.turn() ? 0xffe3c47e : 0xffa8d6c1,
                false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
            pose.popPose();
        }
        pose.pushPose();
        pose.translate(0, TableGeometry.FELT_Y + 0.041, -0.025);
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.scale(0.005f, -0.005f, 0.005f);
        Component round = TableScreen.roundName(view);
        font.drawInBatch(round, -font.width(round) / 2f, -3, 0xffeed6a0, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        String reserve = view.remaining() + " / " + view.riichiSticks();
        font.drawInBatch(reserve, -font.width(reserve) / 2f, 8, 0xffb8d0c0, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
        pose.popPose();
    }

    @Override public boolean shouldRenderOffScreen(MahjongTableBlockEntity table) { return true; }
    @Override public int getViewDistance() { return 32; }
}
