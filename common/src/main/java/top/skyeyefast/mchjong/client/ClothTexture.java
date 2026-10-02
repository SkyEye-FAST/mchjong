package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Resource-pack pattern and dyed weave baked into one opaque surface on every reload. */
final class ClothTexture extends DynamicTexture {
    ClothTexture(DyeColor dye, boolean folded) {
        super(() -> "mchjong cloth", bake(dye, folded));
    }

    private static NativeImage bake(DyeColor dye, boolean folded) {
        var resources = net.minecraft.client.Minecraft.getInstance().getResourceManager();
        try (var patternStream = resources.open(FurnitureMesh.CLOTH_PATTERN);
             var pattern = NativeImage.read(patternStream);
             var stream = resources.open(MahjongContent.id("textures/furniture/felt.png"));
             var felt = NativeImage.read(stream)) {
            int width = Math.max(256, pattern.getWidth()), height = Math.max(256, pattern.getHeight());
            var image = new NativeImage(width, height, false);
            try {
                float spanX = folded ? .56875f : 2.625f, spanZ = folded ? .53125f : 2.625f;
                int rgb = dye.getTextureDiffuseColor();
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    int fx = Math.floorMod((int) Math.floor((x / (float) width - .5f) * spanX * felt.getWidth()), felt.getWidth());
                    int fy = Math.floorMod((int) Math.floor((y / (float) height - .5f) * spanZ * felt.getHeight()), felt.getHeight());
                    float edge = Math.min(Math.min(x, width - 1 - x) / (float) width,
                        Math.min(y, height - 1 - y) / (float) height);
                    float shade = !folded && edge >= .012f && edge < .024f ? .82f : 1;
                    int base = felt.getPixel(fx, fy), tinted = 0xff000000;
                    for (int shift = 0; shift <= 16; shift += 8)
                        tinted |= Math.round((base >>> shift & 255) * (rgb >>> shift & 255) / 255f * shade) << shift;
                    int ink = pattern.getPixel(x * pattern.getWidth() / width, y * pattern.getHeight() / height);
                    image.setPixel(x, y, over(tinted, ink));
                }
                return image;
            } catch (RuntimeException | Error error) {
                image.close();
                throw error;
            }
        } catch (IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    static int over(int base, int ink) {
        int alpha = ink >>> 24, result = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8)
            result |= (((ink >>> shift & 255) * alpha + (base >>> shift & 255) * (255 - alpha) + 127) / 255) << shift;
        return result;
    }
}
