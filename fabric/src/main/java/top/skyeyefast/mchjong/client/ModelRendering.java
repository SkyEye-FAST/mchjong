package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Native model calls with the loader's model-data contract. */
public final class ModelRendering {
    private ModelRendering() {}

    public static void block(BlockState state, PoseStack pose, MultiBufferSource buffers, int light) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light,
            OverlayTexture.NO_OVERLAY);
    }

    public static List<BakedQuad> quads(BakedModel model) {
        return model.getQuads(null, null, RandomSource.create(0));
    }

    public static void stick(PoseStack pose, MultiBufferSource buffers, BakedModel model, int light) {
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
            buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)),
            null, model, 1, 1, 1, light, OverlayTexture.NO_OVERLAY);
    }
}
