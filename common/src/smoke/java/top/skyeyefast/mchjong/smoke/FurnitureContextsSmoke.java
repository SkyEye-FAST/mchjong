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
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partialTick) {}
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff18292e);
        ItemDisplayContext[] contexts = {ItemDisplayContext.GUI, ItemDisplayContext.FIXED, ItemDisplayContext.GROUND,
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND};
        for (int i = 0; i < contexts.length; i++) {
            int cx = width / 6 * (i + 1), cy = height / 2;
            graphics.drawCenteredString(font, new String[] {"GUI", "FIXED", "GROUND", "FIRST PERSON", "THIRD PERSON"}[i], cx, cy + 65, 0xffe3c082);
            graphics.pose().pushPose();
            graphics.pose().translate(cx, cy, 100);
            graphics.pose().scale(80, -80, 80);
            minecraft.getItemRenderer().renderStatic(new ItemStack(MahjongContent.TABLE_ITEM), contexts[i],
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, graphics.pose(), graphics.bufferSource(), minecraft.level, 0);
            graphics.flush();
            graphics.pose().popPose();
        }
    }
}
