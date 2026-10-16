package top.skyeyefast.mchjong.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
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
    private int first, second;

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

    private void update(top.skyeyefast.mchjong.engine.ManualHandlingView handling, boolean onTable, boolean pickup,
                        int x, int y, int width, int height, boolean seated, boolean pending) {
        visible = seated && onTable;
        active = visible && !pending && pickup;
        first = handling == null ? 0 : handling.diceOne();
        second = handling == null ? 0 : handling.diceTwo();
        setRectangle(width, height, x, y);
        setMessage(first == 0 ? Component.translatable("action.mchjong.pick_up_dice")
            : Component.translatable("ui.mchjong.dice_result", first, second, first + second));
        setTooltip(null);
        if (!visible) setFocused(false);
    }

    @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (active && isHoveredOrFocused()) graphics.renderOutline(getX(), getY(), getWidth(), getHeight(), MahjongUi.ACCENT);
    }

    void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!visible || !isHoveredOrFocused()) return;
        var font = Minecraft.getInstance().font;
        if (first == 0) {
            graphics.renderTooltip(font, getMessage(), mouseX, mouseY);
            return;
        }
        int width = 66 + font.width(Integer.toString(first + second));
        int x = Math.max(4, Math.min(mouseX + 12, graphics.guiWidth() - width - 4));
        int y = Math.max(4, Math.min(mouseY - 28, graphics.guiHeight() - 28));
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        MahjongUi.panel(graphics, x, y, width, 24);
        face(graphics, first, x + 4, y + 4);
        graphics.drawString(font, "+", x + 23, y + 8, MahjongUi.TEXT, false);
        face(graphics, second, x + 32, y + 4);
        graphics.drawString(font, "= " + (first + second), x + 51, y + 8, MahjongUi.TEXT, false);
        graphics.pose().popPose();
    }

    private static void face(GuiGraphics graphics, int face, int x, int y) {
        graphics.blit(MahjongContent.id("textures/item/dice_" + face + ".png"), x, y, 16, 16, 0, 0, 32, 32, 32, 32);
    }

    static void renderWorld(RiichiView view, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!onTable(view)) return;
        renderWorld(view.handling(), pose, buffers, light);
    }

    static void renderWorld(top.skyeyefast.mchjong.engine.McrView view, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!onTable(view.handling(), view.phase().name(), 4)) return;
        renderWorld(view.handling(), pose, buffers, light);
    }

    private static void renderWorld(top.skyeyefast.mchjong.engine.ManualHandlingView handling, PoseStack pose, MultiBufferSource buffers, int light) {
        for (int index = 0; index < 2; index++) {
            int face = Math.max(1, index == 0 ? handling.diceOne() : handling.diceTwo());
            pose.pushPose();
            // Keep both entire cubes inside the central 0.3-block square.
            pose.translate(index == 0 ? -.065 : .065, TableGeometry.FELT_Y + .0375, -.06);
            pose.scale(.12f, .12f, .12f);
            pose.mulPose(Axis.YP.rotationDegrees(face * 17 + index * 23));
            if (face == 2 || face == 5 || face == 6)
                pose.mulPose(Axis.XP.rotationDegrees(face == 2 ? 90 : face == 5 ? -90 : 180));
            if (face == 3 || face == 4) pose.mulPose(Axis.ZP.rotationDegrees(face == 3 ? 90 : -90));
            Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(MahjongContent.DICE), ItemDisplayContext.NONE,
                light, OverlayTexture.NO_OVERLAY, pose, buffers, Minecraft.getInstance().level, 0);
            pose.popPose();
        }
    }
}
