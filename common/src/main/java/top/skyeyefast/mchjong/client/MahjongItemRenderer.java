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
import top.skyeyefast.mchjong.item.TileMaterial;
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
        pose.translate(.5, .15, .5);
        var wood = stack.getOrDefault(MahjongComponents.WOOD, FurnitureWood.OAK);
        if (stack.is(MahjongContent.TABLE_ITEM) || stack.is(MahjongContent.AUTO_TABLE_ITEM)) {
            pose.scale(.29f, .6f, .29f);
            FurnitureMesh.table(pose, buffers, light, wood, null, stack.is(MahjongContent.AUTO_TABLE_ITEM));
        } else if (stack.is(MahjongContent.STOOL_ITEM)) {
            FurnitureMesh.stool(pose, buffers, light, wood, MahjongSupplies.color(stack));
        } else if (stack.is(MahjongContent.BOX_ITEM)) {
            pose.translate(0, .15, 0);
            FurnitureMesh.box(pose, buffers, light);
        } else if (stack.is(MahjongContent.CLOTH_ITEM)) {
            var cloth = buffers.getBuffer(FurnitureMesh.texture(MahjongSupplies.color(stack).getName() + "_wool"));
            TileMesh.texturedBox(pose, cloth, -.3f, .2f, -.3f, .3f, .25f, .3f, light);
            TileMesh.texturedBox(pose, cloth, -.3f, .25f, -.3f, .2f, .3f, .3f, light);
        } else if (stack.is(MahjongContent.POINT_STICK)) {
            pose.translate(0, .3, 0);
            pose.scale(1, 2, 2);
            FurnitureMesh.stick(pose, buffers, light, stack.getOrDefault(MahjongComponents.POINTS, 0));
        } else if (stack.is(MahjongContent.TILE_ITEM)) {
            var data = MahjongSupplies.tile(stack);
            boolean five = data.face() == 4 || data.face() == 13 || data.face() == 22;
            int tile = data.blank() ? -2 : data.face() * 4 + (five && !data.red() ? 1 : 0);
            pose.translate(0, .35, 0);
            pose.scale(4.5f, 4.5f, 4.5f);
            TileMesh.drawBack(pose, buffers.getBuffer(TileRenderTypes.BACKS), false, light, MahjongSupplies.color(stack));
            TileMesh.drawFace(pose, buffers.getBuffer(TileRenderTypes.FACES), tile, false, light);
            TileMesh.drawBody(pose, buffers.getBuffer(data.material() == TileMaterial.GLASS ? TileRenderTypes.GLASS : TileRenderTypes.FACES),
                light, data.material());
        }
        pose.popPose();
    }
}
