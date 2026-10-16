package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.TableGeometry;

/** The same public roll drives the physical dice, native pickup target and face tooltip. */
final class TableDice extends MahjongButton {
    private int first, second, third;

    TableDice(Runnable pickup) {
        super(0, 0, 40, 20, Component.translatable("action.mchjong.pick_up_dice"), ignored -> pickup.run());
        visible = active = false;
        setTooltip(null);
    }

    static boolean onTable(RiichiView view) {
        return view != null && onTable(view.handling(), view.phase().name(), view.rules().players());
    }

    private static boolean onTable(top.skyeyefast.mchjong.engine.ManualHandlingView handling, String phase, int players) {
        return handling != null && !handling.diceHeld() && !phase.equals("SHUFFLE") && !phase.equals("MATCH_END")
            && (!phase.equals("BUILD_WALL") || handling.builtWalls() == (1 << players) - 1);
    }

    void update(RiichiView view, int x, int y, int width, int height, boolean seated, boolean pending) {
        update(view.handling(), onTable(view), view.actions().stream().anyMatch(action -> action.type() == RiichiAction.Type.PICK_UP_DICE),
            x, y, width, height, seated, pending);
    }

    void update(top.skyeyefast.mchjong.engine.McrView view, int x, int y, int width, int height, boolean seated, boolean pending) {
        update(view.handling(), onTable(view.handling(), view.phase().name(), 4),
            view.actions().stream().anyMatch(action -> action.type() == top.skyeyefast.mchjong.engine.McrAction.Type.PICK_UP_DICE),
            x, y, width, height, seated, pending);
    }

    void update(top.skyeyefast.mchjong.engine.TaiwanView view, int x, int y, int width, int height, boolean seated, boolean pending) {
        update(view.handling(), onTable(view.handling(), view.phase().name(), 4),
            view.actions().stream().anyMatch(action -> action.type() == top.skyeyefast.mchjong.engine.TaiwanAction.Type.PICK_UP_DICE),
            x, y, width, height, seated, pending);
    }

    void update(top.skyeyefast.mchjong.engine.SichuanView view, int x, int y, int width, int height, boolean seated, boolean pending) {
        update(view.handling(), onTable(view.handling(), view.phase().name(), 4),
            view.actions().stream().anyMatch(action -> action.type() == top.skyeyefast.mchjong.engine.SichuanAction.Type.PICK_UP_DICE),
            x, y, width, height, seated, pending);
    }

    private void update(top.skyeyefast.mchjong.engine.ManualHandlingView handling, boolean onTable, boolean pickup,
                        int x, int y, int width, int height, boolean seated, boolean pending) {
        visible = seated && onTable;
        active = visible && !pending && pickup;
        first = handling == null ? 0 : handling.diceOne();
        second = handling == null ? 0 : handling.diceTwo();
        third = handling == null ? 0 : handling.diceThree();
        setRectangle(width, height, x, y);
        setMessage(first == 0 ? Component.translatable("action.mchjong.pick_up_dice") : third == 0
            ? Component.translatable("ui.mchjong.dice_result", first, second, first + second)
            : Component.translatable("ui.mchjong.dice_result_three", first, second, third, first + second + third));
        setTooltip(null);
        if (!visible) setFocused(false);
    }

    @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (active && isHoveredOrFocused()) graphics.outline(getX(), getY(), getWidth(), getHeight(), MahjongUi.ACCENT);
    }

    void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!visible || !isHoveredOrFocused()) return;
        var font = Minecraft.getInstance().font;
        if (first == 0) {
            graphics.setTooltipForNextFrame(font, getMessage(), mouseX, mouseY);
            return;
        }
        int[] faces = third == 0 ? new int[]{first, second} : new int[]{first, second, third};
        int total = java.util.Arrays.stream(faces).sum();
        int width = 28 * faces.length + 10 + font.width(Integer.toString(total));
        int x = Math.max(4, Math.min(mouseX + 12, graphics.guiWidth() - width - 4));
        int y = Math.max(4, Math.min(mouseY - 28, graphics.guiHeight() - 28));
        graphics.nextStratum();
        MahjongUi.panel(graphics, x, y, width, 24);
        for (int index = 0; index < faces.length; index++) {
            face(graphics, faces[index], x + 4 + 28 * index, y + 4);
            if (index > 0) graphics.text(font, "+", x + 28 * index - 5, y + 8, MahjongUi.TEXT, false);
        }
        graphics.text(font, "= " + total, x + 28 * faces.length - 5, y + 8, MahjongUi.TEXT, false);
    }

    private static void face(GuiGraphicsExtractor graphics, int face, int x, int y) {
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            MahjongContent.id("textures/item/dice_" + face + ".png"), x, y, 0, 0, 16, 16, 32, 32, 32, 32);
    }

    static void renderWorld(RiichiView view, PoseStack pose, SubmitNodeCollector collector, int light) {
        if (!onTable(view)) return;
        renderWorld(view.handling(), 2, pose, collector, light);
    }

    static void renderWorld(top.skyeyefast.mchjong.engine.McrView view, PoseStack pose, SubmitNodeCollector collector, int light) {
        if (!onTable(view.handling(), view.phase().name(), 4)) return;
        renderWorld(view.handling(), 2, pose, collector, light);
    }

    static void renderWorld(top.skyeyefast.mchjong.engine.TaiwanView view, PoseStack pose, SubmitNodeCollector collector, int light) {
        if (!onTable(view.handling(), view.phase().name(), 4)) return;
        renderWorld(view.handling(), 3, pose, collector, light);
    }

    static void renderWorld(top.skyeyefast.mchjong.engine.SichuanView view, PoseStack pose, SubmitNodeCollector collector, int light) {
        if (!onTable(view.handling(), view.phase().name(), 4)) return;
        renderWorld(view.handling(), 2, pose, collector, light);
    }

    private static void renderWorld(top.skyeyefast.mchjong.engine.ManualHandlingView handling, int count, PoseStack pose, SubmitNodeCollector collector, int light) {
        boolean three = count == 3;
        int[] faces = {handling.diceOne(), handling.diceTwo(), handling.diceThree()};
        var client = Minecraft.getInstance();
        for (int index = 0; index < count; index++) {
            int face = Math.max(1, faces[index]);
            pose.pushPose();
            // Keep every entire cube inside the central 0.3-block square; a third die sits ahead of the pair.
            pose.translate(index == 2 ? 0 : index == 0 ? -.065 : .065, TableGeometry.FELT_Y + .0375, three ? index == 2 ? .05 : -.075 : -.06);
            pose.scale(.12f, .12f, .12f);
            pose.mulPose(Axis.YP.rotationDegrees(face * 17 + index * 23));
            if (face == 2 || face == 5 || face == 6)
                pose.mulPose(Axis.XP.rotationDegrees(face == 2 ? 90 : face == 5 ? -90 : 180));
            if (face == 3 || face == 4) pose.mulPose(Axis.ZP.rotationDegrees(face == 3 ? 90 : -90));
            var renderState = new ItemStackRenderState();
            client.getItemModelResolver().updateForTopItem(renderState, new ItemStack(MahjongContent.DICE),
                ItemDisplayContext.NONE, client.level, client.player, index);
            renderState.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
