package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Loader adapters only register this renderer; every component and mesh decision is shared. */
public final class MahjongItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static Item[] items() {
        return new Item[]{MahjongContent.TABLE_ITEM, MahjongContent.AUTO_TABLE_ITEM, MahjongContent.STOOL_ITEM,
            MahjongContent.CLOTH_ITEM, MahjongContent.TILE_ITEM, MahjongContent.BOX_ITEM, MahjongContent.POINT_STICK};
    }
    public MahjongItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                       MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        boolean table = stack.is(MahjongContent.TABLE_ITEM) || stack.is(MahjongContent.AUTO_TABLE_ITEM);
        boolean furniture = table || stack.is(MahjongContent.STOOL_ITEM);
        if (furniture) furniturePose(pose, context, table);
        else pose.translate(.5, .15, .5);
        var wood = MahjongComponents.wood(stack);
        if (stack.is(MahjongContent.TABLE_ITEM) || stack.is(MahjongContent.AUTO_TABLE_ITEM)) {
            FurnitureMesh.table(pose, buffers, light, wood, null, stack.is(MahjongContent.AUTO_TABLE_ITEM));
        } else if (stack.is(MahjongContent.STOOL_ITEM)) {
            FurnitureMesh.stool(pose, buffers, light, wood, MahjongSupplies.color(stack));
        } else if (stack.is(MahjongContent.BOX_ITEM)) {
            pose.translate(0, .15, 0);
            FurnitureMesh.box(pose, buffers, light);
        } else if (stack.is(MahjongContent.CLOTH_ITEM)) {
            FurnitureMesh.foldedCloth(pose, buffers, light, MahjongSupplies.color(stack));
        } else if (stack.is(MahjongContent.POINT_STICK)) {
            pose.translate(0, .3, 0);
            FurnitureMesh.stick(pose, buffers, light, MahjongComponents.points(stack));
        } else if (stack.is(MahjongContent.TILE_ITEM)) {
            var data = MahjongSupplies.tile(stack);
            var back = MahjongSupplies.back(stack);
            pose.translate(0, .35, 0);
            pose.scale(4.5f, 4.5f, 4.5f);
            if (data.blank()) TileMesh.drawBlankFront(pose, buffers.getBuffer(TileRenderTypes.body(data.material())), light, data.material());
            else TileMesh.drawArtwork(pose, buffers.getBuffer(TileRenderTypes.faces(MahjongSupplies.facePreset(stack))), TileMesh.artwork(data), light);
            TileMesh.drawBack(pose, buffers.getBuffer(TileRenderTypes.back(data.material(), back)), false, light, data.material(), back);
            TileMesh.drawBody(pose, buffers.getBuffer(TileRenderTypes.body(data.material())),
                light, data.material(), back);
            TileMesh.drawBackPattern(pose, buffers.getBuffer(TileRenderTypes.backPattern(MahjongSupplies.backPreset(stack))), false, light);
        }
        pose.popPose();
    }
    /** Native item callers share the same centered, upright furniture envelope. */
    static void furniturePose(PoseStack pose, ItemDisplayContext context, boolean table) {
        float width = table ? (float) (2 * top.skyeyefast.mchjong.world.TableGeometry.OUTER_HALF_WIDTH) : .78125f;
        float height = table ? 1 : (float) top.skyeyefast.mchjong.world.TableGeometry.STOOL_HEIGHT;
        pose.translate(.5, .5, .5);
        float scale;
        switch (context) {
            case GUI, FIXED, NONE -> {
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(context == ItemDisplayContext.GUI ? 15 : 0));
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(context == ItemDisplayContext.GUI ? 225 : 0));
                scale = (context == ItemDisplayContext.GUI && !table ? .5f : .9f) / width;
                pose.scale(scale, scale, scale);
                pose.translate(0, -height / 2, 0);
            }
            case GROUND -> {
                scale = .45f / width;
                pose.translate(0, -.35, 0);
                pose.scale(scale, scale, scale);
            }
            case HEAD -> {
                // Native head Y inversion maps item-up to model-up; feet sit on the head crown.
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));
                pose.translate(0, .4, 0);
                scale = 1.1f / width;
                pose.scale(scale, scale, scale);
            }
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> {
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -30 : 30));
                scale = .63f / width;
                pose.scale(scale, scale, scale);
                pose.translate(0, -height / 2, 0);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(75));
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -45 : 45));
                scale = .54f / width;
                pose.scale(scale, scale, scale);
                pose.translate(0, -height / 2, 0);
            }
        }
    }
}
