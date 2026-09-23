package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

public final class MahjongTableRenderer
        implements BlockEntityRenderer<MahjongTableBlockEntity, MahjongTableRenderer.State> {
    private enum Layer { BACK, BODY, FACE, PATTERN, OUTLINE }

    public static final class State extends BlockEntityRenderState {
        TableView view;
        FurnitureWood wood;
        net.minecraft.world.item.DyeColor cloth;
        boolean automatic;
        TileMaterial material;
        net.minecraft.world.item.DyeColor back;
        TileFacePreset preset;
        TableAnimation animation;
        boolean animated;
        long now;
        List<TableAnimation.Frame> frames = List.of();
    }

    public MahjongTableRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(MahjongTableBlockEntity table, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(table, state, partialTick, cameraPos, crumbling);
        state.view = table.clientView();
        state.wood = table.wood();
        state.cloth = table.equipment().hasCloth() ? table.equipment().clothColor() : null;
        state.automatic = table.automatic();
        state.material = table.equipment().material();
        state.back = table.equipment().back();
        state.preset = table.equipment().preset();
        if (state.view == null) {
            state.animation = null;
            state.frames = List.of();
            return;
        }
        state.now = Util.getMillis();
        state.animation = TableAnimation.of(table);
        state.animation.accept(state.view, state.now);
        state.animated = TableSettings.get().animations;
        state.frames = state.animated ? state.animation.sample(state.now) : state.animation.settled();
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        var buffers = new DeferredBuffers(collector);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        FurnitureMesh.table(pose, buffers, state.lightCoords, state.wood, state.cloth, state.automatic);
        pose.popPose();
        if (state.view == null) {
            buffers.submit(pose);
            return;
        }

        pose.pushPose();
        pose.translate(.5, 0, .5);
        boolean glass = state.material == TileMaterial.GLASS;
        tiles(state, pose, buffers, Layer.BACK);
        if (!glass) tiles(state, pose, buffers, Layer.BODY);
        tiles(state, pose, buffers, Layer.FACE);
        if (state.automatic) TableIndicator.render(state.view, pose, buffers, state.lightCoords);
        TableDeposits.render(state.view, state.automatic, state.animation, state.animated, state.now, pose, buffers, state.lightCoords);
        TableDice.renderWorld(state.view, pose, collector, state.lightCoords);
        if (glass) tiles(state, pose, buffers, Layer.BODY);
        tiles(state, pose, buffers, Layer.PATTERN);
        tiles(state, pose, buffers, Layer.OUTLINE);
        buffers.submit(pose);
        pose.popPose();
    }

    private static void tiles(State state, PoseStack pose, DeferredBuffers buffers, Layer layer) {
        var vertices = buffers.getBuffer(switch (layer) {
            case BACK -> TileRenderTypes.back(state.material, state.back);
            case BODY -> TileRenderTypes.body(state.material);
            case FACE -> TileRenderTypes.faces(state.preset);
            case PATTERN -> TileRenderTypes.BACK_PATTERN;
            case OUTLINE -> RenderTypes.lines();
        });
        TableScreen screen = TableScreen.active(Minecraft.getInstance().screen);
        for (TableAnimation.Frame frame : state.frames) {
            TableScene.Piece piece = frame.piece();
            if (piece.area() == TableScene.Area.RIVER && !TableSettings.get().showRiver) continue;
            int highlight = layer == Layer.OUTLINE && screen != null ? screen.highlight(state.blockPos, piece) : 0;
            if (layer == Layer.OUTLINE && highlight == 0) continue;
            pose.pushPose();
            boolean selected = screen != null && screen.selected(state.blockPos, piece);
            var position = piece.position().add(screen == null ? Vec3.ZERO : screen.handlingOffset(state.blockPos, piece));
            pose.translate(position.x, position.y + (selected ? .035 : 0), position.z);
            pose.mulPose(Axis.YP.rotationDegrees(piece.yaw()));
            pose.mulPose(Axis.XP.rotationDegrees(frame.pitch()));
            pose.scale(TableScene.TILE_SCALE, TableScene.TILE_SCALE, TableScene.TILE_SCALE);
            switch (layer) {
                case FACE -> TileMesh.drawFace(pose, vertices, piece.tile(), piece.back(), state.lightCoords);
                case BODY -> TileMesh.drawBody(pose, vertices, state.lightCoords, state.material, state.back);
                case BACK -> TileMesh.drawBack(pose, vertices, piece.back() || piece.tile() < 0,
                    state.lightCoords, state.material, state.back);
                case PATTERN -> TileMesh.drawBackPattern(pose, vertices, piece.back() || piece.tile() < 0, state.lightCoords);
                case OUTLINE -> TileMesh.drawOutline(pose, vertices, highlight);
            }
            pose.popPose();
        }
    }

    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 32; }
}
