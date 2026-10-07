package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import top.skyeyefast.mchjong.item.McrDeck;

/** Render MCR scene poses using the shared tile meshes; no room or rule state is read here. */
public final class McrSceneRenderer {
    private enum Layer { BACK, BODY, FACE, PATTERN, OUTLINE }
    private McrSceneRenderer() {}

    public static void render(List<McrTableScene.Piece> pieces, McrDeck deck,
                              PoseStack pose, MultiBufferSource buffers, int light) {
        render(pieces, deck, pose, buffers, light, piece -> 0, piece -> false);
    }

    public static void render(List<McrTableScene.Piece> pieces, McrDeck deck,
                              PoseStack pose, MultiBufferSource buffers, int light,
                              java.util.function.ToIntFunction<McrTableScene.Piece> highlight,
                              java.util.function.Predicate<McrTableScene.Piece> selected) {
        render(pieces, deck, pose, buffers, light, highlight, selected, null, 0);
    }
    static void render(List<McrTableScene.Piece> pieces, McrDeck deck, PoseStack pose, MultiBufferSource buffers, int light,
                       java.util.function.ToIntFunction<McrTableScene.Piece> highlight, java.util.function.Predicate<McrTableScene.Piece> selected,
                       TableAnimation animation, long now) {
        var order = deck.material() == top.skyeyefast.mchjong.item.TileMaterial.GLASS
            ? new Layer[]{Layer.FACE, Layer.BACK, Layer.BODY, Layer.PATTERN, Layer.OUTLINE} : Layer.values();
        for (var layer : order) {
            var vertices = buffers.getBuffer(switch (layer) {
                case BACK -> TileRenderTypes.back(deck.material(), deck.back());
                case BODY -> TileRenderTypes.body(deck.material());
                case FACE -> TileRenderTypes.faces(deck.preset());
                case PATTERN -> TileRenderTypes.backPattern(deck.backPreset());
                case OUTLINE -> net.minecraft.client.renderer.rendertype.RenderTypes.lines();
            });
            for (var piece : pieces) {
                int color = layer == Layer.OUTLINE ? highlight.applyAsInt(piece) : 0;
                if (layer == Layer.OUTLINE && color == 0) continue;
                pose.pushPose();
                var frame = animation == null ? new TableAnimation.Pose(piece.position(), piece.yaw(), piece.flat() ? piece.back() ? 90 : -90 : 0, piece.tile(), piece.back()) : animation.worldPose(piece, now);
                var position = frame.position();
                pose.translate(position.x, position.y + (selected.test(piece) ? .035 : 0), position.z);
                pose.mulPose(Axis.YP.rotationDegrees(frame.yaw()));
                boolean faceDown = frame.back() && frame.pitch() > 0;
                pose.mulPose(Axis.XP.rotationDegrees(frame.pitch()));
                piece.dimensions().apply(pose);
                switch (layer) {
                    case BACK -> TileMesh.drawBack(pose, vertices, faceDown, light, deck.material(), deck.back());
                    case BODY -> TileMesh.drawBody(pose, vertices, light, deck.material(), deck.back());
                    case FACE -> {
                        if (frame.tile() >= 0 && !frame.back())
                            TileMesh.drawArtwork(pose, vertices, TileMesh.artwork(deck.tile(frame.tile())), light);
                        else TileMesh.drawFace(pose, vertices, -1, true, light);
                    }
                    case PATTERN -> TileMesh.drawBackPattern(pose, vertices, faceDown, light);
                    case OUTLINE -> TileMesh.drawOutline(pose, vertices, color);
                }
                pose.popPose();
            }
        }
    }
}
