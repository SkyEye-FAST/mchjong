package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.DynamicTexture;

/** World engravings are filtered over their opaque white plate, without alpha-cutout loss. */
final class TileFaceTexture extends DynamicTexture {
    TileFaceTexture(NativeImage image) { super(image); }

    @Override public void upload() {
        NativeImage base = getPixels();
        if (base == null) throw new IllegalStateException("Tile face texture is closed");
        // Composite before reducing: transparent black must not darken the white rim,
        // and fine antialiased strokes must not disappear at the cutout threshold.
        base.applyToAllPixels(TileFaceTexture::onWhite);
        int levels = mipLevels(base.getWidth(), base.getHeight());
        NativeImage[] images = MipmapGenerator.generateMipLevels(new NativeImage[]{base}, levels);
        try {
            TextureUtil.prepareImage(getId(), levels, base.getWidth(), base.getHeight());
            for (int level = 0; level < images.length; level++) {
                NativeImage image = images[level];
                image.upload(level, 0, 0, 0, 0, image.getWidth(), image.getHeight(), true, true, true, false);
            }
            setFilter(true, true);
        } finally {
            for (int level = 1; level < images.length; level++) images[level].close();
        }
    }

    static int mipLevels(int width, int height) {
        // Cell boundaries and the 32px white swatch remain exact through level five.
        int swatch = Math.min(width / 64, height / 128);
        return swatch <= 1 ? 0 : 31 - Integer.numberOfLeadingZeros(swatch);
    }

    static int onWhite(int abgr) {
        int alpha = abgr >>> 24;
        int result = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int channel = abgr >>> shift & 255;
            result |= (255 - ((255 - channel) * alpha + 127) / 255) << shift;
        }
        return result;
    }
}
