package top.skyeyefast.mchjong.smoke;

import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {}
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff18292e);
        graphics.centeredText(font, title, width / 2, 12, 0xffe3c082);
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, width / 2 - 110, 28,
            width / 2 + 110, height - 12, Math.min(90, (height - 60) / 3), .1f,
            width / 2f - 35, height / 2f - 25, entity);
    }
}
