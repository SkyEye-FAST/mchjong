package top.skyeyefast.mchjong.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import top.skyeyefast.mchjong.world.SeatEntity;

/** The block draws the furniture; this renderer leaves only the seated player visible. */
public final class SeatRenderer extends EntityRenderer<SeatEntity, EntityRenderState> {
    public SeatRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; }
    @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }
    @Override public void submit(EntityRenderState state, com.mojang.blaze3d.vertex.PoseStack pose,
            net.minecraft.client.renderer.SubmitNodeCollector collector,
            net.minecraft.client.renderer.state.level.CameraRenderState camera) {}
}
