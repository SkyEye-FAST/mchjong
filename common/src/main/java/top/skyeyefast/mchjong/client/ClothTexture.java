package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import java.io.IOException;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Resource-pack pattern and dyed weave baked into one opaque surface on every reload. */
final class ClothTexture extends SimpleTexture {
    private final DyeColor dye;
    private final boolean folded;
    ClothTexture(DyeColor dye, boolean folded) {
        super(FurnitureMesh.CLOTH_PATTERN);
        this.dye = dye;
        this.folded = folded;
    }

    @Override public void load(ResourceManager resources) throws IOException {
        try (var patternData = getTextureImage(resources);
             var stream = resources.open(MahjongContent.id("textures/furniture/felt.png"));
             var felt = NativeImage.read(stream)) {
            var pattern = patternData.getImage();
            int width = Math.max(256, pattern.getWidth()), height = Math.max(256, pattern.getHeight());
            try (var image = new NativeImage(width, height, false)) {
                float spanX = folded ? .56875f : 2.625f, spanZ = folded ? .53125f : 2.625f;
                int rgb = TileMesh.dyeColor(dye);
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int fx = Math.floorMod((int) Math.floor((x / (float) width - .5f) * spanX * felt.getWidth()), felt.getWidth());
                    int fy = Math.floorMod((int) Math.floor((y / (float) height - .5f) * spanZ * felt.getHeight()), felt.getHeight());
                    float edge = Math.min(Math.min(x, width - 1 - x) / (float) width,
                        Math.min(y, height - 1 - y) / (float) height);
                    float shade = !folded && edge >= .012f && edge < .024f ? .82f : 1;
                    int base = felt.getPixelRGBA(fx, fy), tinted = 0xff000000;
                    for (int channel = 0; channel < 3; channel++)
                        tinted |= Math.round((base >>> (channel * 8) & 255) * (rgb >>> ((2 - channel) * 8) & 255) / 255f * shade) << (channel * 8);
                    int ink = pattern.getPixelRGBA(x * pattern.getWidth() / width, y * pattern.getHeight() / height);
                    image.setPixelRGBA(x, y, over(tinted, ink));
                }
                TextureUtil.prepareImage(getId(), width, height);
                image.upload(0, 0, 0, 0, 0, width, height, false, true, false, false);
                setFilter(false, false);
            }
        }
    }

    static int over(int base, int ink) {
        int alpha = ink >>> 24, result = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8)
            result |= (((ink >>> shift & 255) * alpha + (base >>> shift & 255) * (255 - alpha) + 127) / 255) << shift;
        return result;
    }
}
