package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import top.skyeyefast.mchjong.config.PresetArchives;

/** Compose independently supplied tile engravings into the renderer's fixed cell layout. */
final class TileFaceImages {
    record Pair(NativeImage atlas, NativeImage glyphs) {}
    private TileFaceImages() {}

    static Pair compose(Map<String, byte[]> tiles) throws IOException {
        var atlas = new NativeImage(2048, 4096, true);
        var glyphs = new NativeImage(2048, 4096, true);
        try {
            for (int face = 0; face < PresetArchives.TILE_KEYS.size(); face++) {
                int cellX = face % 8 * 256, cellY = face / 8 * 384;
                for (int y = 0; y < 384; y++) for (int x = 0; x < 256; x++)
                    atlas.setPixelRGBA(cellX + x, cellY + y,
                        x < 4 || x >= 252 || y < 4 || y >= 380 ? 0xffd4d0cb : 0xffffffff);
                byte[] bytes = tiles.get(PresetArchives.TILE_KEYS.get(face));
                if (bytes == null) throw new IOException("Missing tile image: " + PresetArchives.TILE_KEYS.get(face));
                try (var source = NativeImage.read(new ByteArrayInputStream(bytes))) {
                    if (source.getWidth() < 1 || source.getHeight() < 1 || source.getWidth() > 2048 || source.getHeight() > 2048)
                        throw new IOException("Invalid tile image dimensions: " + PresetArchives.TILE_KEYS.get(face));
                    if (face == 31) continue;
                    for (int y = 0; y < 320; y++) for (int x = 0; x < 240; x++) {
                        int color = sample(source, (x + .5) * source.getWidth() / 240 - .5,
                            (y + .5) * source.getHeight() / 320 - .5);
                        int px = cellX + x + 8, py = cellY + y + 32;
                        glyphs.setPixelRGBA(px, py, color);
                        atlas.setPixelRGBA(px, py, overWhite(color));
                    }
                }
            }
            for (int y = 4064; y < 4096; y++) for (int x = 2016; x < 2048; x++) {
                atlas.setPixelRGBA(x, y, 0xffffffff);
                glyphs.setPixelRGBA(x, y, 0xffffffff);
            }
            glyphs.applyToAllPixels(TileFaceTexture::onWhite);
            return new Pair(atlas, glyphs);
        } catch (IOException | RuntimeException failure) {
            atlas.close(); glyphs.close();
            throw failure;
        }
    }

    private static int overWhite(int abgr) {
        int alpha = abgr >>> 24;
        int result = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8)
            result |= (255 - ((255 - (abgr >>> shift & 255)) * alpha + 127) / 255) << shift;
        return result;
    }

    private static int sample(NativeImage image, double x, double y) {
        x = net.minecraft.util.Mth.clamp(x, 0, image.getWidth() - 1);
        y = net.minecraft.util.Mth.clamp(y, 0, image.getHeight() - 1);
        int left = (int) Math.floor(x);
        int top = (int) Math.floor(y);
        int right = Math.min(left + 1, image.getWidth() - 1);
        int bottom = Math.min(top + 1, image.getHeight() - 1);
        double fx = x - left, fy = y - top;
        int a = image.getPixelRGBA(left, top), b = image.getPixelRGBA(right, top);
        int c = image.getPixelRGBA(left, bottom), d = image.getPixelRGBA(right, bottom);
        int color = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            double value = ((a >>> shift & 255) * (1 - fx) + (b >>> shift & 255) * fx) * (1 - fy)
                + ((c >>> shift & 255) * (1 - fx) + (d >>> shift & 255) * fx) * fy;
            color |= net.minecraft.util.Mth.clamp((int) Math.round(value), 0, 255) << shift;
        }
        return color;
    }
}
