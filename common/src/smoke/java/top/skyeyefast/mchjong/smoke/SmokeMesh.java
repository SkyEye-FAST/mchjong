package top.skyeyefast.mchjong.smoke;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.BiConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;

/** Native picture-in-picture rendering for smoke-only three-dimensional fixtures. */
public final class SmokeMesh {
    private SmokeMesh() {}
    public record State(int x0, int y0, int x1, int y1, float scale,
                        BiConsumer<PoseStack, MultiBufferSource.BufferSource> draw) implements PictureInPictureRenderState {
        @Override public ScreenRectangle scissorArea() { return null; }
        @Override public ScreenRectangle bounds() { return new ScreenRectangle(x0, y0, x1 - x0, y1 - y0); }
    }
    static void extract(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, float scale,
                        BiConsumer<PoseStack, MultiBufferSource.BufferSource> draw) {
        ((top.skyeyefast.mchjong.smoke.mixin.SmokeGuiStateAccessor) graphics).mchjong$state().addPicturesInPictureState(new State(left, top, right, bottom, scale, draw));
    }
    public static final class Renderer extends PictureInPictureRenderer<State> {
        public Renderer(MultiBufferSource.BufferSource source) { super(source); }
        @Override public Class<State> getRenderStateClass() { return State.class; }
        @Override protected void renderToTexture(State state, PoseStack pose) { state.draw().accept(pose, bufferSource); }
        @Override protected float getTranslateY(int height, int guiScale) { return height / 2f; }
        @Override protected String getTextureLabel() { return "mchjong smoke mesh"; }
    }
}
