package top.skyeyefast.mchjong.mixin;

import java.util.List;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows optional client integrations to order their own layers before mutable third-party layers. */
@Mixin(LivingEntityRenderer.class)
public interface LivingRendererAccessor {
    @Accessor("layers") List<RenderLayer<?, ?>> mchjong$layers();
}
