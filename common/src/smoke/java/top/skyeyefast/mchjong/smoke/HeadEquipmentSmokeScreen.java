package top.skyeyefast.mchjong.smoke;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/** Uses the equipped entity's real renderer and head layer, including optional maid models. */
final class HeadEquipmentSmokeScreen extends Screen {
    private final LivingEntity entity;
    HeadEquipmentSmokeScreen(LivingEntity entity) {
        super(Component.literal("Furniture head equipment"));
        this.entity = entity;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics) {}
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff18292e);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xffe3c082);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, width / 2 - 110, 28,
            width / 2 + 110, height - 12, Math.min(90, (height - 60) / 3), .1f,
            width / 2f - 35, height / 2f - 25, entity);
    }
}
