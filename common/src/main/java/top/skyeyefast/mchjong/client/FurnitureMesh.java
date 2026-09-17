package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.world.TableGeometry;

/** Original furniture materials and compact meshes shared by world and item rendering. */
public final class FurnitureMesh {
    private static final int WHITE = 0xffffffff;
    private static final int SHADE = 0xffb7aca0;
    private FurnitureMesh() {}

    public static void table(PoseStack pose, MultiBufferSource buffers, int light,
                             FurnitureWood wood, DyeColor cloth, boolean automatic) {
        var wooden = buffers.getBuffer(texture("wood_" + wood.getSerializedName()));
        float felt = (float) TableGeometry.FELT_HALF_WIDTH;
        float outer = (float) TableGeometry.OUTER_HALF_WIDTH;
        float leg = felt - .1625f;
        FurnitureShape.box(pose, wooden, -felt - .0625f, .78125f, -felt - .0625f,
            felt + .0625f, .890625f, felt + .0625f, SHADE, light);
        // Both a bare playing surface and an installed mat finish at TableGeometry.FELT_Y.
        FurnitureShape.box(pose, wooden, -felt, .875f, -felt, felt,
            cloth == null ? .9375f : .925f, felt, WHITE, light);
        FurnitureShape.frame(pose, wooden, felt, outer, .859375f, 1, .015625f, WHITE, light);
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(side * 90));
            FurnitureShape.box(pose, wooden, -felt + .03125f, .640625f, felt - .09375f,
                felt - .03125f, .84375f, felt + .03125f, WHITE, light);
            FurnitureShape.box(pose, wooden, -felt + .03125f, .625f, felt - .109375f,
                felt - .03125f, .671875f, felt + .046875f, SHADE, light);
            if (!automatic) {
                var drawer = TableGeometry.STICK_DRAWER;
                var edge = buffers.getBuffer(texture("edge"));
                FurnitureShape.box(pose, edge, (float) drawer.minX, (float) drawer.minY, (float) drawer.minZ,
                    (float) drawer.maxX, (float) drawer.maxY, (float) drawer.maxZ - .006f, WHITE, light);
                wooden = buffers.getBuffer(texture("wood_" + wood.getSerializedName()));
                FurnitureShape.box(pose, wooden, (float) drawer.minX + .012f, (float) drawer.minY + .012f, (float) drawer.minZ,
                    (float) drawer.maxX - .012f, (float) drawer.maxY - .012f, (float) drawer.maxZ - .003f, WHITE, light);
                var handle = buffers.getBuffer(texture("brass"));
                FurnitureShape.box(pose, handle, -.105f, .715f, (float) drawer.maxZ - .003f,
                    .105f, .76f, (float) drawer.maxZ, WHITE, light);
                wooden = buffers.getBuffer(texture("wood_" + wood.getSerializedName()));
            }
            if (!automatic) FurnitureShape.box(pose, wooden, -leg, .171875f, leg - .040625f,
                leg, .234375f, leg + .0375f, SHADE, light);
            pose.popPose();
        }
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            if (!automatic) {
                FurnitureShape.tapered(pose, wooden, x * leg - .109375f, 0, z * leg - .109375f,
                    x * leg + .109375f, .796875f, z * leg + .109375f, .03125f, WHITE, light);
                FurnitureShape.box(pose, wooden, x * leg - .1171875f, .5625f, z * leg - .1171875f,
                    x * leg + .1171875f, .609375f, z * leg + .1171875f, SHADE, light);
            }
        }
        if (automatic) automaticBase(pose, buffers, light);
        var brass = buffers.getBuffer(texture("brass"));
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(side * 90));
            if (automatic) FurnitureShape.bevel(pose, brass, -.11f, .707f, felt + .0315f, .11f, .758f, felt + .0475f, .004f, WHITE, light);
            FurnitureShape.box(pose, brass, felt + .03125f, 1, felt + .03125f, felt + .09375f, 1.002f, felt + .09375f, WHITE, light);
            pose.popPose();
        }
        if (cloth != null) tableCloth(pose, buffers, light, cloth);
    }

    private static void automaticBase(PoseStack pose, MultiBufferSource buffers, int light) {
        var metal = buffers.getBuffer(texture("steel"));
        FurnitureShape.bevel(pose, metal, -.75f, .025f, -.75f, .75f, .14f, .75f, .05f, WHITE, light);
        FurnitureShape.tapered(pose, metal, -.34375f, .125f, -.34375f, .34375f, .6875f, .34375f, .03125f, WHITE, light);
        FurnitureShape.bevel(pose, metal, -.453125f, .640625f, -.453125f, .453125f, .8125f, .453125f, .03125f, WHITE, light);
        var brass = buffers.getBuffer(texture("brass"));
        FurnitureShape.box(pose, brass, -.35f, .4375f, -.35f, .35f, .4765625f, .35f, WHITE, light);
        var edge = buffers.getBuffer(texture("edge"));
        FurnitureShape.box(pose, edge, -.703125f, 0, -.703125f, .703125f, .03125f, .703125f, WHITE, light);
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(side * 90));
            for (int vent = 0; vent < 3; vent++) {
                float x = (vent - 1) * .125f;
                FurnitureShape.box(pose, edge, x - .03125f, .53125f, .339f, x + .03125f, .59375f, .346f, WHITE, light);
            }
            pose.popPose();
        }
    }

    private static void tableCloth(PoseStack pose, MultiBufferSource buffers, int light, DyeColor color) {
        var felt = buffers.getBuffer(texture("felt"));
        float half = (float) TableGeometry.FELT_HALF_WIDTH;
        FurnitureShape.box(pose, felt, -half, .925f, -half, half, .9375f, half, tint(color, 1), light);
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(side * 90));
            FurnitureShape.box(pose, felt, -half + .03125f, .9375f, half - .0625f,
                half - .03125f, .938f, half - .03125f, tint(color, .82f), light);
            pose.popPose();
        }
    }

    public static void stool(PoseStack pose, MultiBufferSource buffers, int light, FurnitureWood wood, DyeColor color) {
        var wooden = buffers.getBuffer(texture("wood_" + wood.getSerializedName()));
        FurnitureShape.bevel(pose, wooden, -.390625f, .40625f, -.390625f, .390625f, .515625f, .390625f, .0234375f, WHITE, light);
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1})
            FurnitureShape.tapered(pose, wooden, x * .265625f - .078125f, 0, z * .265625f - .078125f,
                x * .265625f + .078125f, .4375f, z * .265625f + .078125f, .0234375f, WHITE, light);
        for (int side = 0; side < 4; side++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(side * 90));
            FurnitureShape.box(pose, wooden, -.265625f, .14f, .24f, .265625f, .1875f, .29f, SHADE, light);
            FurnitureShape.box(pose, wooden, -.28f, .34375f, .265625f, .28f, .421875f, .328125f, SHADE, light);
            pose.popPose();
        }
        var felt = buffers.getBuffer(texture("felt"));
        FurnitureShape.bevel(pose, felt, -.375f, .5f, -.375f, .375f, .54375f, .375f, .0125f, tint(color, .68f), light);
        FurnitureShape.bevel(pose, felt, -.359375f, .52f, -.359375f, .359375f, .625f, .359375f, .04f, tint(color, 1), light);
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1})
            FurnitureShape.box(pose, felt, x * .15625f - .03125f, .624f, z * .15625f - .03125f,
                x * .15625f + .03125f, .626f, z * .15625f + .03125f, tint(color, .8f), light);
    }

    public static void box(PoseStack pose, MultiBufferSource buffers, int light) {
        var wooden = buffers.getBuffer(texture("wood_dark_oak"));
        FurnitureShape.bevel(pose, wooden, -.359375f, 0, -.265625f, .359375f, .05f, .265625f, .0125f, SHADE, light);
        FurnitureShape.box(pose, wooden, -.34375f, .03125f, -.25f, .34375f, .2375f, .25f, WHITE, light);
        FurnitureShape.bevel(pose, wooden, -.359375f, .245f, -.265625f, .359375f, .328125f, .265625f, .0234375f, WHITE, light);
        FurnitureShape.box(pose, wooden, -.28f, .328125f, -.1875f, .28f, .33f, .1875f, SHADE, light);
        var edge = buffers.getBuffer(texture("edge"));
        FurnitureShape.box(pose, edge, -.345f, .234375f, -.251f, .345f, .25f, .251f, WHITE, light);
        FurnitureShape.box(pose, edge, -.1f, .126f, .273f, .1f, .151f, .302f, WHITE, light);
        var brass = buffers.getBuffer(texture("brass"));
        for (int side : new int[]{-1, 1}) {
            float x = side * .21875f;
            FurnitureShape.bevel(pose, brass, x - .032f, .175f, .249f, x + .032f, .282f, .275f, .006f, WHITE, light);
            FurnitureShape.box(pose, brass, x - .041f, .213f, -.273f, x + .041f, .265f, -.25f, WHITE, light);
            FurnitureShape.box(pose, brass, side * .105f - .014f, .126f, .258f,
                side * .105f + .014f, .198f, .299f, WHITE, light);
            for (int end : new int[]{-1, 1}) {
                FurnitureShape.box(pose, brass, side * .322f - .02f, .055f, end * .252f - .009f,
                    side * .322f + .02f, .219f, end * .252f + .009f, SHADE, light);
                FurnitureShape.box(pose, brass, side * .345f - .009f, .055f, end * .228f - .025f,
                    side * .345f + .009f, .219f, end * .228f + .025f, SHADE, light);
            }
        }
        // A restrained tile-shaped lid inlay identifies the case without a borrowed block icon.
        var inlay = buffers.getBuffer(TileRenderTypes.FACES);
        TileMesh.box(pose, inlay, -.048f, .33f, -.071f, .048f, .333f, .071f, 0xfff4f4ed, light);
        TileMesh.box(pose, inlay, -.009f, .333f, -.042f, .009f, .334f, .042f, 0xff3c715e, light);
        for (float z : new float[]{-.027f, 0, .027f})
            TileMesh.box(pose, inlay, -.018f, .333f, z - .004f, .018f, .334f, z + .004f, 0xff3c715e, light);
    }

    public static void foldedCloth(PoseStack pose, MultiBufferSource buffers, int light, DyeColor color) {
        var felt = buffers.getBuffer(texture("felt"));
        FurnitureShape.bevel(pose, felt, -.3125f, .1875f, -.28125f, .3125f, .24375f, .28125f, .015625f, tint(color, .78f), light);
        FurnitureShape.bevel(pose, felt, -.3f, .235f, -.28125f, .3f, .3f, .28125f, .015625f, tint(color, 1), light);
        FurnitureShape.box(pose, felt, -.27f, .3f, .22f, .27f, .301f, .23f, tint(color, .72f), light);
    }

    private static int tint(DyeColor dye, float brightness) {
        int rgb = dye.getTextureDiffuseColor();
        return 0xff000000 | (int) ((rgb >> 16 & 255) * brightness) << 16
            | (int) ((rgb >> 8 & 255) * brightness) << 8 | (int) ((rgb & 255) * brightness);
    }

    public static void stick(PoseStack pose, MultiBufferSource buffers, int light, int points) {
        var vertices = buffers.getBuffer(TileRenderTypes.FACES);
        int body = switch (points) {
            case 1000 -> 0xff80c5e8;
            case 5000 -> 0xfff3d76b;
            case 10000 -> 0xffee8d98;
            default -> 0xffeee6d4;
        };
        TileMesh.box(pose, vertices, -.35f, 0, -.03f, .35f, .025f, .03f, body, light);
        if (points == 0) return;
        int count = points == 100 ? 6 : points == 1000 ? 1 : points == 5000 ? 5 : 2;
        int color = points == 1000 ? 0xffa52b35 : points == 5000 ? 0xff315bb8 : 0xff252525;
        for (int i = 0; i < count; i++) {
            float x = (i - (count - 1) / 2f) * .07f;
            TileMesh.box(pose, vertices, x - .012f, .025f, -.012f, x + .012f, .026f, .012f, color, light);
        }
    }

    public static RenderType texture(String texture) {
        return RenderType.entityCutout(ResourceLocation.fromNamespaceAndPath("mchjong", "textures/furniture/" + texture + ".png"));
    }
}
