package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** Cosmetic images use the renderer's shared linear sampler. */
final class PresetTexture extends DynamicTexture {
    PresetTexture(NativeImage image) { super(() -> "mchjong preset", image); }
    @Override public GpuSampler getSampler() { return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR); }
}
