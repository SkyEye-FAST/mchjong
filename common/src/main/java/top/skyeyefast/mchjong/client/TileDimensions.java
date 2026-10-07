package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.AABB;
import top.skyeyefast.mchjong.engine.MahjongVariant;

/** Physical envelope in blocks; the 2.625-block felt represents 850 mm. */
public record TileDimensions(float width, float height, float depth) {
    public static final TileDimensions SMALL = new TileDimensions(24 * 2.625f / 850, 33 * 2.625f / 850, 17.5f * 2.625f / 850);
    public static final TileDimensions LARGE = new TileDimensions(.104f, .160f, .0726f);

    public static TileDimensions of(MahjongVariant variant) {
        return switch (variant) {
            case RIICHI, TAIWAN -> SMALL;
            case MCR, SICHUAN -> LARGE;
        };
    }

    public float ratio() { return height / width; }
    public AABB bounds() {
        return new AABB(-width / 2.0, -height / 2.0, -depth / 2.0, width / 2.0, height / 2.0, depth / 2.0);
    }

    /** Map the shared canonical mesh to this envelope once, after its pose rotations. */
    public void apply(PoseStack pose) {
        pose.scale(width / LARGE.width, height / LARGE.height, depth / LARGE.depth);
    }
}
