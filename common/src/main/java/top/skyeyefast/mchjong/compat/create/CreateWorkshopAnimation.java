package top.skyeyefast.mchjong.compat.create;

import com.mojang.math.Axis;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllPartialModels;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;

/** Create's own block and partial models, shared by the recipe browsers. */
final class CreateWorkshopAnimation {
    private CreateWorkshopAnimation() {}

    static void render(GuiGraphics graphics, CreateWorkshopDisplays.Display display, int x, int y) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 88, y + 48, 200);
        pose.mulPose(Axis.XP.rotationDegrees(-15.5f));
        pose.mulPose(Axis.YP.rotationDegrees(22.5f));

        var time = AnimationTickHolder.getRenderTime();
        if (display.machine().is(AllBlocks.MECHANICAL_PRESS.asItem())) {
            GuiGameElement.of(AllBlocks.MECHANICAL_PRESS.getDefaultState()).scale(23).render(graphics);
            double cycle = time % 30;
            double offset = cycle < 10 ? -Math.pow(cycle / 10, 3)
                : cycle < 15 ? -1 : cycle < 20 ? -(20 - cycle) / 5 : 0;
            GuiGameElement.of(AllPartialModels.MECHANICAL_PRESS_HEAD).atLocal(0, -offset, 0).scale(23).render(graphics);
        } else if (display.machine().is(AllBlocks.MECHANICAL_MIXER.asItem())) {
            GuiGameElement.of(AllBlocks.MECHANICAL_MIXER.getDefaultState()).scale(23).render(graphics);
            double offset = (Math.sin(time / 8) + 1) / 5;
            GuiGameElement.of(AllPartialModels.MECHANICAL_MIXER_POLE).atLocal(0, offset, 0).scale(23).render(graphics);
            GuiGameElement.of(AllPartialModels.MECHANICAL_MIXER_HEAD).atLocal(0, offset, 0)
                .rotateBlock(0, time * 12, 0).scale(23).render(graphics);
        } else if (display.machine().is(AllBlocks.DEPLOYER.asItem())) {
            GuiGameElement.of(AllBlocks.DEPLOYER.getDefaultState()).scale(23).render(graphics);
            GuiGameElement.of(AllPartialModels.DEPLOYER_POLE).atLocal(0, (Math.sin(time / 6) + 1) / 5, 0)
                .scale(23).render(graphics);
            GuiGameElement.of(AllPartialModels.DEPLOYER_HAND_PUNCHING).atLocal(0, (Math.sin(time / 6) + 1) / 5, 0)
                .scale(23).render(graphics);
        } else {
            GuiGameElement.of(AllBlocks.MECHANICAL_SAW.getDefaultState()).scale(23).render(graphics);
            GuiGameElement.of(AllPartialModels.SAW_BLADE_HORIZONTAL_ACTIVE).scale(23).render(graphics);
        }
        if (display.requiresBasin())
            GuiGameElement.of(AllBlocks.BASIN.getDefaultState()).atLocal(0, 1.65, 0).scale(23).render(graphics);
        pose.popPose();
    }
}
