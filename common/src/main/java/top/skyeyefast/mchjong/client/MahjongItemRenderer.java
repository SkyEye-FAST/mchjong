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
public final class MahjongItemRenderer implements SpecialModelRenderer<MahjongItemRenderer.Argument> {
    public record Argument(ItemStack stack, net.minecraft.world.item.ItemDisplayContext context) {}
    public static final Identifier TYPE = Identifier.fromNamespaceAndPath("mchjong", "supply");
    public static final MahjongItemRenderer INSTANCE = new MahjongItemRenderer();
    public static final Unbaked UNBAKED = new Unbaked();
    public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(UNBAKED);

    public static Item[] items() {
        return new Item[]{MahjongContent.TABLE_ITEM, MahjongContent.AUTO_TABLE_ITEM, MahjongContent.STOOL_ITEM,
            MahjongContent.CLOTH_ITEM, MahjongContent.TILE_ITEM, MahjongContent.BOX_ITEM, MahjongContent.POINT_STICK};
    }

    private MahjongItemRenderer() {}

    @Override public Argument extractArgument(ItemStack stack) { return new Argument(stack.copy(), net.minecraft.world.item.ItemDisplayContext.NONE); }

    @Override public void submit(Argument argument, PoseStack pose, SubmitNodeCollector collector,
            int light, int overlay, boolean foil, int outlineColor) {
        if (argument == null || argument.stack().isEmpty()) return;
        var stack = argument.stack();
        HeldSupplyArm.render(stack, pose, collector, light);
        var buffers = new DeferredBuffers(collector);
        pose.pushPose();
        boolean table = stack.is(MahjongContent.TABLE_ITEM) || stack.is(MahjongContent.AUTO_TABLE_ITEM);
        if (table || stack.is(MahjongContent.STOOL_ITEM)) furniturePose(pose, argument.context(), table);
        else pose.translate(.5, .15, .5);
        var wood = stack.getOrDefault(MahjongComponents.WOOD, FurnitureWood.OAK);
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
            FurnitureMesh.stick(pose, buffers, light, stack.getOrDefault(MahjongComponents.POINTS, 0));
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
        buffers.submit(pose);
        pose.popPose();
    }

    /** Native item callers share the same centered, upright furniture envelope. */
    static void furniturePose(PoseStack pose, net.minecraft.world.item.ItemDisplayContext context, boolean table) {
        float width = table ? (float) (2 * top.skyeyefast.mchjong.world.TableGeometry.OUTER_HALF_WIDTH) : .78125f;
        float height = table ? 1 : (float) top.skyeyefast.mchjong.world.TableGeometry.STOOL_HEIGHT;
        pose.translate(.5, .5, .5);
        float scale;
        switch (context) {
            case GUI, FIXED, NONE -> {
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(context == net.minecraft.world.item.ItemDisplayContext.GUI ? 15 : 0));
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(context == net.minecraft.world.item.ItemDisplayContext.GUI ? 225 : 0));
                scale = (context == net.minecraft.world.item.ItemDisplayContext.GUI && !table ? .5f : .9f) / width;
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
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(context == net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? -30 : 30));
                scale = .63f / width;
                pose.scale(scale, scale, scale);
                pose.translate(0, -height / 2, 0);
            }
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> {
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(75));
                pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(context == net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -45 : 45));
                scale = .54f / width;
                pose.scale(scale, scale, scale);
                pose.translate(0, -height / 2, 0);
            }
        }
    }

    @Override public void getExtents(Consumer<Vector3fc> consumer) {
        consumer.accept(new Vector3f(-1, -1, -1));
        consumer.accept(new Vector3f(1, 1.5f, 1));
    }

    public static final class Unbaked implements SpecialModelRenderer.Unbaked<Argument> {
        private Unbaked() {}
        @Override public SpecialModelRenderer<MahjongItemRenderer.Argument> bake(SpecialModelRenderer.BakingContext context) { return INSTANCE; }
        @Override public MapCodec<Unbaked> type() { return MAP_CODEC; }
    }
}
