package top.skyeyefast.mchjong.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import top.skyeyefast.mchjong.world.SeatEntity;

/** The block draws the furniture; this renderer leaves only the seated player visible. */
public final class SeatRenderer extends EntityRenderer<SeatEntity> {
    public SeatRenderer(EntityRendererProvider.Context context) { super(context); shadowRadius = 0; }
    @Override public ResourceLocation getTextureLocation(SeatEntity entity) { return TileMesh.ATLAS; }
}
