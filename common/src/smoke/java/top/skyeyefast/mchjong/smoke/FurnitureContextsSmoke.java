package top.skyeyefast.mchjong.smoke;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.world.MahjongContent;

final class FurnitureContextsSmoke extends Screen {
    FurnitureContextsSmoke() { super(Component.literal("Furniture item contexts")); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics) {}
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff18292e);
        ItemDisplayContext[] contexts = {ItemDisplayContext.GUI, ItemDisplayContext.FIXED, ItemDisplayContext.GROUND,
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND};
        for (int row = 0; row < 2; row++) for (int i = 0; i < contexts.length; i++) {
            int cx = width / 6 * (i + 1), cy = height * (row + 1) / 3;
            graphics.drawCenteredString(font, new String[] {"GUI", "FIXED", "GROUND", "FIRST PERSON", "THIRD PERSON"}[i], cx, cy + 50, 0xffe3c082);
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 100);
            graphics.pose().scale(60, -60, 60);
            minecraft.getItemRenderer().renderStatic(new ItemStack(row == 0 ? MahjongContent.TABLE_ITEM : MahjongContent.STOOL_ITEM), contexts[i],
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, graphics.pose(), graphics.bufferSource(), minecraft.level, 0);
            graphics.flush();
            graphics.pose().popPose();
        }
    }
}
