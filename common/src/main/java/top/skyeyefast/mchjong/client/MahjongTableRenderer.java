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
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.TileFacePreset;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;

public final class MahjongTableRenderer
        implements BlockEntityRenderer<MahjongTableBlockEntity, MahjongTableRenderer.State> {
    private enum Layer { BACK, BODY, FACE, PATTERN, OUTLINE }

    public static final class State extends BlockEntityRenderState {
        RiichiView view;
        top.skyeyefast.mchjong.engine.McrView mcrView;
        top.skyeyefast.mchjong.engine.SichuanView sichuanView;
        top.skyeyefast.mchjong.engine.TaiwanSession.View taiwanView;
        List<TaiwanTableScene.Piece> taiwanPieces;
        top.skyeyefast.mchjong.item.TaiwanDeck taiwanDeck;
        TableAnimation tableAnimation;
        FurnitureWood wood;
        net.minecraft.world.item.DyeColor cloth;
        boolean automatic;
        boolean lobby;
        List<McrTableScene.Piece> mcrPieces;
        top.skyeyefast.mchjong.item.McrDeck mcrDeck;
        List<SichuanTableScene.Piece> sichuanPieces;
        top.skyeyefast.mchjong.item.SichuanDeck sichuanDeck;
        TileMaterial material;
        net.minecraft.world.item.DyeColor back;
        TileFacePreset preset;
        net.minecraft.resources.Identifier backPreset;
        RiichiAnimation animation;
        boolean animated;
        long now;
        List<RiichiAnimation.Frame> frames = List.of();
    }

    public MahjongTableRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(MahjongTableBlockEntity table, State state, float partialTick,
            Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(table, state, partialTick, cameraPos, crumbling);
        state.view = table.clientView();
        state.mcrView = table.clientMcrView() == null ? null : table.clientMcrView().game();
        state.sichuanView = table.clientSichuanView() == null ? null : table.clientSichuanView().game();
        state.taiwanView = table.clientTaiwanView();
        state.taiwanDeck = table.clientTaiwanDeck();
        state.taiwanPieces = state.taiwanView == null ? null : TaiwanTableScene.build(state.taiwanView.game());
        state.tableAnimation = TableAnimation.of(table);
        state.now = Util.getMillis();
        state.wood = table.wood();
        state.cloth = table.equipment().hasCloth() ? table.equipment().clothColor() : null;
        state.automatic = table.automatic();
        state.lobby = table.clientRoom() == null || table.clientRoom().lobby();
        state.mcrDeck = table.clientMcrDeck();
        state.mcrPieces = table.clientMcrView() == null ? null : McrTableScene.build(table.clientMcrView().game());
        state.sichuanDeck = table.clientSichuanDeck();
        state.sichuanPieces = table.clientSichuanView() == null ? null : SichuanTableScene.build(table.clientSichuanView().game());
        state.material = table.equipment().material();
        state.back = table.equipment().back();
        state.preset = table.equipment().preset();
        state.backPreset = table.equipment().backPreset();
        if (state.view == null) {
            state.animation = null;
            state.frames = List.of();
            return;
        }
        state.now = Util.getMillis();
        state.animation = RiichiAnimation.of(table);
        state.animation.accept(state.view, state.now);
        state.animated = TableSettings.get().animations;
        state.frames = state.animated ? state.animation.sample(state.now) : state.animation.settled();
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        var buffers = new DeferredBuffers(collector);
        pose.pushPose();
        pose.translate(.5, 0, .5);
        FurnitureMesh.table(pose, buffers, state.lightCoords, state.wood, state.cloth, state.automatic);
        if (state.automatic && state.lobby) TableIndicator.renderStandby(pose, buffers, state.lightCoords);
        if (state.taiwanPieces != null && state.taiwanDeck != null) {
            var screen = TaiwanTableScreen.active(Minecraft.getInstance().screen);
            var active = screen != null && screen.tablePos().equals(state.blockPos) ? screen : null;
            TaiwanSceneRenderer.render(state.taiwanPieces, state.taiwanDeck, pose, buffers, state.lightCoords,
                piece -> active == null ? 0 : active.highlight(piece), piece -> active != null && active.selected(piece), state.tableAnimation, state.now);
            if (state.automatic) TableIndicator.render(state.taiwanView, pose, buffers, state.lightCoords);
        } else if (state.sichuanPieces != null && state.sichuanDeck != null) {
            var screen = SichuanTableScreen.active(Minecraft.getInstance().screen);
            var active = screen != null && screen.tablePos().equals(state.blockPos) ? screen : null;
            SichuanSceneRenderer.render(state.sichuanPieces, state.sichuanDeck, pose, buffers, state.lightCoords,
                piece -> active == null ? 0 : active.highlight(piece), piece -> active != null && active.selected(piece), state.tableAnimation, state.now);
            if (state.automatic) TableIndicator.render(state.sichuanView, pose, buffers, state.lightCoords);
        } else if (state.mcrPieces != null && state.mcrDeck != null) {
            var screen = Minecraft.getInstance().screen instanceof McrTableScreen mcr
                && mcr.tablePos().equals(state.blockPos) ? mcr : null;
            McrSceneRenderer.render(state.mcrPieces, state.mcrDeck, pose, buffers, state.lightCoords,
                piece -> screen == null ? 0 : screen.highlight(piece), piece -> screen != null && screen.selected(piece), state.tableAnimation, state.now);
            if (state.automatic) TableIndicator.render(state.mcrView, pose, buffers, state.lightCoords);
        }
        pose.popPose();
        if (state.view == null) {
            buffers.submit(pose);
            return;
        }

        pose.pushPose();
        pose.translate(.5, 0, .5);
        boolean glass = state.material == TileMaterial.GLASS;
        if (!glass) {
            tiles(state, pose, buffers, Layer.BACK);
            tiles(state, pose, buffers, Layer.BODY);
        }
        tiles(state, pose, buffers, Layer.FACE);
        if (state.automatic) TableIndicator.render(state.view, pose, buffers, state.lightCoords);
        RiichiDeposits.render(state.view, state.automatic, state.animation, state.animated, state.now, pose, buffers, collector, state.lightCoords);
        RiichiDice.renderWorld(state.view, pose, collector, state.lightCoords);
        if (glass) {
            tiles(state, pose, buffers, Layer.BACK);
            tiles(state, pose, buffers, Layer.BODY);
        }
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
            case PATTERN -> TileRenderTypes.backPattern(state.backPreset);
            case OUTLINE -> RenderTypes.lines();
        });
        RiichiTableScreen screen = RiichiTableScreen.active(Minecraft.getInstance().screen);
        for (RiichiAnimation.Frame frame : state.frames) {
            RiichiTableScene.Piece piece = frame.piece();
            if (piece.area() == RiichiTableScene.Area.RIVER && !TableSettings.get().showRiver) continue;
            int highlight = layer == Layer.OUTLINE && screen != null ? screen.highlight(state.blockPos, piece) : 0;
            if (layer == Layer.OUTLINE && highlight == 0) continue;
            pose.pushPose();
            boolean selected = screen != null && screen.selected(state.blockPos, piece);
            var position = piece.position().add(screen == null ? Vec3.ZERO : screen.handlingOffset(state.blockPos, piece));
            pose.translate(position.x, position.y + (selected ? .035 : 0), position.z);
            pose.mulPose(Axis.YP.rotationDegrees(piece.yaw()));
            pose.mulPose(Axis.XP.rotationDegrees(frame.pitch()));
            RiichiTableScene.DIMENSIONS.apply(pose);
            // A face-down tile turns the back image toward the table center, including during a flip.
            boolean faceDown = frame.pitch() > 0;
            switch (layer) {
                case FACE -> TileMesh.drawFace(pose, vertices, piece.tile(), piece.back(), state.lightCoords);
                case BODY -> TileMesh.drawBody(pose, vertices, state.lightCoords, state.material, state.back);
                case BACK -> TileMesh.drawBack(pose, vertices, faceDown, state.lightCoords, state.material, state.back);
                case PATTERN -> TileMesh.drawBackPattern(pose, vertices, faceDown, state.lightCoords);
                case OUTLINE -> TileMesh.drawOutline(pose, vertices, highlight);
            }
            pose.popPose();
        }
    }

    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 32; }
}
