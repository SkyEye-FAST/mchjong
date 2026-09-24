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
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.TableView;
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

    static boolean onTable(TableView view) {
        return view != null && view.handling() != null && !view.handling().diceHeld()
            && view.phase() != Game.Phase.LOBBY && view.phase() != Game.Phase.SHUFFLE
            && view.phase() != Game.Phase.MATCH_END
            && (view.phase() != Game.Phase.BUILD_WALL
                || view.handling().builtWalls() == (1 << view.rules().players()) - 1);
    }

    void update(TableView view, int x, int y, int width, int height, boolean seated, boolean pending) {
        visible = seated && onTable(view);
        active = visible && !pending && view.actions().stream().anyMatch(action -> action.type() == Action.Type.PICK_UP_DICE);
        first = view.handling() == null ? 0 : view.handling().diceOne();
        second = view.handling() == null ? 0 : view.handling().diceTwo();
        setRectangle(width, height, x, y);
        setMessage(first == 0 ? Component.translatable("action.mchjong.pick_up_dice")
            : Component.translatable("ui.mchjong.dice_result", first, second, first + second));
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
        int width = 66 + font.width(Integer.toString(first + second));
        int x = Math.max(4, Math.min(mouseX + 12, graphics.guiWidth() - width - 4));
        int y = Math.max(4, Math.min(mouseY - 28, graphics.guiHeight() - 28));
        graphics.nextStratum();
        MahjongUi.panel(graphics, x, y, width, 24);
        face(graphics, first, x + 4, y + 4);
        graphics.text(font, "+", x + 23, y + 8, MahjongUi.TEXT, false);
        face(graphics, second, x + 32, y + 4);
        graphics.text(font, "= " + (first + second), x + 51, y + 8, MahjongUi.TEXT, false);
    }

    private static void face(GuiGraphicsExtractor graphics, int face, int x, int y) {
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
            MahjongContent.id("textures/item/dice_" + face + ".png"), x, y, 0, 0, 16, 16, 32, 32, 32, 32);
    }

    static void renderWorld(TableView view, PoseStack pose, SubmitNodeCollector collector, int light) {
        if (!onTable(view)) return;
        var client = Minecraft.getInstance();
        for (int index = 0; index < 2; index++) {
            int face = Math.max(1, index == 0 ? view.handling().diceOne() : view.handling().diceTwo());
            pose.pushPose();
            // Keep both entire cubes inside the central 0.3-block square.
            pose.translate(index == 0 ? -.065 : .065, TableGeometry.FELT_Y + .0375, -.06);
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
