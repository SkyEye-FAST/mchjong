package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Component-aware supply geometry for the 26.x special-item render pipeline. */
public final class MahjongItemRenderer implements SpecialModelRenderer<ItemStack> {
    public static final Identifier TYPE = MahjongContent.id("supply");
    public static final MahjongItemRenderer INSTANCE = new MahjongItemRenderer();
    public static final Unbaked UNBAKED = new Unbaked();
    public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(UNBAKED);

    public static Item[] items() {
        return new Item[]{MahjongContent.TABLE_ITEM, MahjongContent.AUTO_TABLE_ITEM, MahjongContent.STOOL_ITEM,
            MahjongContent.CLOTH_ITEM, MahjongContent.TILE_ITEM, MahjongContent.BOX_ITEM, MahjongContent.POINT_STICK};
    }

    private MahjongItemRenderer() {}

    @Override public ItemStack extractArgument(ItemStack stack) { return stack.copy(); }

    @Override public void submit(ItemStack stack, PoseStack pose, SubmitNodeCollector collector,
            int light, int overlay, boolean foil, int outlineColor) {
        if (stack == null || stack.isEmpty()) return;
        HeldSupplyArm.render(stack, pose, collector, light);
        var buffers = new DeferredBuffers(collector);
        pose.pushPose();
        pose.translate(.5, .15, .5);
        var wood = stack.getOrDefault(MahjongComponents.WOOD, FurnitureWood.OAK);
        if (stack.is(MahjongContent.TABLE_ITEM) || stack.is(MahjongContent.AUTO_TABLE_ITEM)) {
            pose.translate(0, .18, 0);
            float scale = (float) (.9 / (2 * top.skyeyefast.mchjong.world.TableGeometry.OUTER_HALF_WIDTH));
            pose.scale(scale, scale, scale);
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
            FurnitureMesh.stick(pose, buffers, light, stack.getOrDefault(MahjongComponents.POINTS, 0));
        } else if (stack.is(MahjongContent.TILE_ITEM)) {
            var data = MahjongSupplies.tile(stack);
            var back = MahjongSupplies.back(stack);
            pose.translate(0, .35, 0);
            pose.scale(4.5f, 4.5f, 4.5f);
            TileMesh.drawBack(pose, buffers.getBuffer(TileRenderTypes.back(data.material(), back)), false, false, light, data.material(), back);
            if (data.blank()) TileMesh.drawBlankFront(pose, buffers.getBuffer(TileRenderTypes.body(data.material())), light, data.material());
            else TileMesh.drawArtwork(pose, buffers.getBuffer(TileRenderTypes.faces(MahjongSupplies.facePreset(stack))), TileMesh.artwork(data), light);
            TileMesh.drawBody(pose, buffers.getBuffer(TileRenderTypes.body(data.material())),
                light, data.material(), back);
            TileMesh.drawBackPattern(pose, buffers.getBuffer(TileRenderTypes.BACK_PATTERN), false, false, light);
        }
        buffers.submit(pose);
        pose.popPose();
    }

    @Override public void getExtents(Consumer<Vector3fc> consumer) {
        consumer.accept(new Vector3f(-1, -1, -1));
        consumer.accept(new Vector3f(1, 1.5f, 1));
    }

    public static final class Unbaked implements SpecialModelRenderer.Unbaked<ItemStack> {
        private Unbaked() {}
        @Override public SpecialModelRenderer<ItemStack> bake(SpecialModelRenderer.BakingContext context) { return INSTANCE; }
        @Override public MapCodec<Unbaked> type() { return MAP_CODEC; }
    }
}
