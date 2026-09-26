package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/** Vanilla resource-pack elements and textures, baked by each loader's native model pipeline. */
public final class RiichiStickModel {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("mchjong", "item/riichi_stick");
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("mchjong", "textures/item/riichi_stick.png");
    private static java.util.function.Supplier<BakedModel> model;
    private RiichiStickModel() {}
    public static void initialize(java.util.function.Supplier<BakedModel> lookup) { model = lookup; }
    public static BakedModel baked() { return model.get(); }
    public static void render(PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(-.5, 0, -.5);
        ModelRendering.stick(pose, buffers, model.get(), light);
        pose.popPose();
    }
}
