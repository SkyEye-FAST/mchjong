package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.item.FurnitureWood;

/** The same few cuboids render furniture in-world and in inventories. */
public final class FurnitureMesh {
    private FurnitureMesh() {}

    public static void table(PoseStack pose, MultiBufferSource buffers, int light,
                             FurnitureWood wood, DyeColor cloth, boolean automatic) {
        var wooden = buffers.getBuffer(texture(wood.getSerializedName() + "_planks"));
        TileMesh.texturedBox(pose, wooden, -1.4375f, .75f, -1.4375f, 1.4375f, .9375f, 1.4375f, light);
        TileMesh.texturedBox(pose, wooden, -1.4375f, .9375f, -1.4375f, -1.3125f, 1, 1.4375f, light);
        TileMesh.texturedBox(pose, wooden, 1.3125f, .9375f, -1.4375f, 1.4375f, 1, 1.4375f, light);
        TileMesh.texturedBox(pose, wooden, -1.3125f, .9375f, -1.4375f, 1.3125f, 1, -1.3125f, light);
        TileMesh.texturedBox(pose, wooden, -1.3125f, .9375f, 1.3125f, 1.3125f, 1, 1.4375f, light);
        if (automatic) {
            var metal = buffers.getBuffer(texture("iron_block"));
            TileMesh.texturedBox(pose, metal, -.375f, .1f, -.375f, .375f, .75f, .375f, light);
            TileMesh.texturedBox(pose, metal, -.85f, 0, -.2f, .85f, .12f, .2f, light);
            TileMesh.texturedBox(pose, metal, -.2f, 0, -.85f, .2f, .12f, .85f, light);
            var copper = buffers.getBuffer(texture("copper_block"));
            TileMesh.texturedBox(pose, copper, -.39f, .45f, -.39f, .39f, .52f, .39f, light);
        } else {
            for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1})
                TileMesh.texturedBox(pose, wooden, x * 1.15f - .09f, 0, z * 1.15f - .09f,
                    x * 1.15f + .09f, .75f, z * 1.15f + .09f, light);
        }
        if (cloth != null) TileMesh.texturedBox(pose, buffers.getBuffer(texture(cloth.getName() + "_wool")),
            -1.3125f, .928f, -1.3125f, 1.3125f, .939f, 1.3125f, light);
    }

    public static void stool(PoseStack pose, MultiBufferSource buffers, int light, FurnitureWood wood, DyeColor color) {
        var wooden = buffers.getBuffer(texture(wood.getSerializedName() + "_planks"));
        TileMesh.texturedBox(pose, wooden, -.375f, .375f, -.375f, .375f, .5f, .375f, light);
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1})
            TileMesh.texturedBox(pose, wooden, x * .25f - .0625f, 0, z * .25f - .0625f,
                x * .25f + .0625f, .375f, z * .25f + .0625f, light);
        TileMesh.texturedBox(pose, buffers.getBuffer(texture(color.getName() + "_wool")),
            -.375f, .5f, -.375f, .375f, .625f, .375f, light);
    }

    public static void box(PoseStack pose, MultiBufferSource buffers, int light) {
        var wooden = buffers.getBuffer(texture("oak_planks"));
        TileMesh.texturedBox(pose, wooden, -.35f, 0, -.25f, .35f, .2f, .25f, light);
        TileMesh.texturedBox(pose, wooden, -.36f, .205f, -.26f, .36f, .28f, .26f, light);
        TileMesh.texturedBox(pose, buffers.getBuffer(texture("copper_block")), -.05f, .12f, .251f, .05f, .25f, .275f, light);
    }

    public static void stick(PoseStack pose, MultiBufferSource buffers, int light, int points) {
        var vertices = buffers.getBuffer(TileRenderTypes.FACES);
        TileMesh.box(pose, vertices, -.35f, 0, -.03f, .35f, .025f, .03f, 0xffeee6d4, light);
        if (points == 0) return;
        int count = points == 100 ? 6 : points == 1000 ? 1 : points == 5000 ? 5 : 2;
        int color = points == 100 ? 0xff252525 : points == 1000 ? 0xffc53737 : points == 5000 ? 0xff315bb8 : 0xffcc9c31;
        for (int i = 0; i < count; i++) {
            float x = (i - (count - 1) / 2f) * .07f;
            TileMesh.box(pose, vertices, x - .012f, .025f, -.012f, x + .012f, .026f, .012f, color, light);
        }
    }

    public static RenderType texture(String texture) {
        return RenderType.entityCutout(ResourceLocation.withDefaultNamespace("textures/block/" + texture + ".png"));
    }
}
