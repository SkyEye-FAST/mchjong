package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.world.TableGeometry;

/** A machine display with large seven-segment scores, localized wind letters and seat lamps. */
public final class TableIndicator {
    private static final float SURFACE = (float) TableGeometry.FELT_Y + .039f;
    private static final int LAMP = 0xffffd58a;
    private static final int DIGIT = 0xffb9efcf;
    private static final int[] DIGITS = {0x3f, 0x06, 0x5b, 0x4f, 0x66, 0x6d, 0x7d, 0x07, 0x7f, 0x6f};
    private static final String[] WINDS = {"east", "south", "west", "north"};
    private TableIndicator() {}

    public static int segments(char character) {
        if (character == '-') return 0x40;
        if (character < '0' || character > '9') throw new IllegalArgumentException("Not a display digit");
        return DIGITS[character - '0'];
    }

    public static void render(TableView view, PoseStack pose, MultiBufferSource buffers, int light) {
        var vertices = buffers.getBuffer(TileRenderTypes.FACES);
        TileMesh.box(pose, vertices, -.265f, (float) TableGeometry.FELT_Y, -.265f,
            .265f, SURFACE - .004f, .265f, 0xff243c40, light);
        TileMesh.box(pose, vertices, -.25f, SURFACE - .004f, -.25f, .25f, SURFACE, .25f, 0xff101e23, light);
        boolean playing = view.phase() == Game.Phase.TURN || view.phase() == Game.Phase.REACTION;
        for (int seat = 0; seat < view.seats().size(); seat++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(seat * 90));
            TileMesh.box(pose, vertices, -.15f, SURFACE, .166f, .15f, SURFACE + .001f, .244f, 0xff22383d, light);
            panel(pose, vertices, -.14f, .237f, .14f, .242f,
                playing && seat == view.turn() ? LAMP : 0xff405052, light);
            number(pose, vertices, view.seats().get(seat).points(), .032f, .176f, .048f, .214f, DIGIT, light);
            pose.popPose();
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Math.max(0, view.viewerSeat()) * 90));
        // Round number is a row of pips; the wall and stick pictograms identify their counters.
        for (int pip = 0; pip <= view.round() % view.rules().players(); pip++)
            panel(pose, vertices, .002f + pip * .022f, -.066f, .012f + pip * .022f, -.056f, LAMP, light);
        for (int tile = 0; tile < 3; tile++)
            panel(pose, vertices, -.083f + tile * .015f, -.012f, -.071f + tile * .015f, .016f, DIGIT, light);
        number(pose, vertices, view.remaining(), .03f, -.022f, .046f, .08f, DIGIT, light);
        panel(pose, vertices, -.104f, .065f, -.059f, .071f, LAMP, light);
        number(pose, vertices, view.honba(), -.026f, .047f, .036f, .068f, LAMP, light);
        panel(pose, vertices, .023f, .065f, .068f, .071f, DIGIT, light);
        number(pose, vertices, view.riichiSticks(), .102f, .047f, .036f, .068f, DIGIT, light);
        pose.popPose();

        // Font rendering switches buffers; finish the machine mesh before drawing any text.
        for (int seat = 0; seat < view.seats().size(); seat++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(seat * 90));
            wind(pose, buffers, Math.floorMod(seat - view.dealer(), view.rules().players()), -.119f, .202f, light);
            pose.popPose();
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Math.max(0, view.viewerSeat()) * 90));
        wind(pose, buffers, Math.min(3, view.round() / view.rules().players()), -.04f, -.061f, light);
        pose.popPose();
    }

    private static void wind(PoseStack pose, MultiBufferSource buffers, int wind, float x, float z, int light) {
        var font = Minecraft.getInstance().font;
        var text = Component.translatable("wind.mchjong." + WINDS[wind] + ".short");
        float scale = .05f / Math.max(font.lineHeight, font.width(text));
        pose.pushPose();
        pose.translate(x, SURFACE + .004f, z);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(scale, scale, scale);
        font.drawInBatch(text, -font.width(text) / 2f, -font.lineHeight / 2f, LAMP, false,
            pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }

    private static void number(PoseStack pose, VertexConsumer out, int value, float center, float z,
                               float height, float maxWidth, int color, int light) {
        String digits = Integer.toString(value);
        height = Math.min(height, maxWidth / (.5f + (digits.length() - 1) * .675f));
        float width = height * .5f, pitch = width * 1.35f;
        float x = center - (digits.length() * pitch - (pitch - width)) / 2;
        for (char digit : digits.toCharArray()) {
            int mask = segments(digit);
            float t = height * .12f, middle = height / 2;
            float[][] bars = {
                {t, 0, width - t, t}, {width - t, t, width, middle - t / 2},
                {width - t, middle + t / 2, width, height - t}, {t, height - t, width - t, height},
                {0, middle + t / 2, t, height - t}, {0, t, t, middle - t / 2},
                {t, middle - t / 2, width - t, middle + t / 2}
            };
            for (int bar = 0; bar < bars.length; bar++) if ((mask & 1 << bar) != 0) {
                var b = bars[bar];
                panel(pose, out, x + b[0], z + b[1], x + b[2], z + b[3], color, light);
            }
            x += pitch;
        }
    }

    /** One upward-facing surface, rather than coplanar top/bottom faces of a thin cuboid. */
    private static void panel(PoseStack pose, VertexConsumer out, float x0, float z0, float x1, float z1, int color, int light) {
        for (float[] p : new float[][]{{x0,z1}, {x1,z1}, {x1,z0}, {x0,z0}})
            out.addVertex(pose.last(), p[0], SURFACE + .003f, p[1]).setColor(color).setUv(TileMesh.SWATCH_U, TileMesh.SWATCH_V)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(), 0, 1, 0);
    }
}
