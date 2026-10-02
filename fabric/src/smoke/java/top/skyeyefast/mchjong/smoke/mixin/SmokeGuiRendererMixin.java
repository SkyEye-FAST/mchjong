package top.skyeyefast.mchjong.smoke.mixin;

import java.util.Map;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.skyeyefast.mchjong.smoke.SmokeMesh;

@Mixin(GuiRenderer.class)
public abstract class SmokeGuiRendererMixin {
    @Shadow @Final private MultiBufferSource.BufferSource bufferSource;
    @Shadow @Final @org.spongepowered.asm.mixin.Mutable private Map<Class<? extends PictureInPictureRenderState>, PictureInPictureRenderer<?>> pictureInPictureRenderers;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void mchjong$mesh(CallbackInfo callback) {
        pictureInPictureRenderers = new java.util.HashMap<>(pictureInPictureRenderers);
        pictureInPictureRenderers.put(SmokeMesh.State.class, new SmokeMesh.Renderer(bufferSource));
    }
}
