package top.skyeyefast.mchjong.smoke;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;

/** All physical flower designs, rendered through the same item path as player inventory. */
final class FlowerGalleryScreen extends Screen {
    FlowerGalleryScreen() { super(Component.literal("Flowers and seasons / eight physical tile designs")); }
    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        minecraft.getToasts().clear();
        gui.fill(0, 0, width, height, 0xff202831);
        gui.drawCenteredString(font, title, width / 2, 12, 0xffe4e9ee);
        int cellWidth = (width - 32) / 4, cellHeight = (height - 48) / 2;
        float scale = Math.min(cellWidth, cellHeight - 20) / 18f;
        for (int flower = 0; flower < TileData.FLOWER_COUNT; flower++) {
            int x = 16 + flower % 4 * cellWidth, y = 32 + flower / 4 * cellHeight;
            gui.fill(x + 4, y + 4, x + cellWidth - 4, y + cellHeight - 4, 0xff2b3540);
            var data = new TileData(TileData.FIRST_FLOWER + flower, TileMaterial.BONE, false);
            gui.pose().pushPose();
            gui.pose().translate(x + cellWidth / 2f - 8 * scale, y + cellHeight / 2f - 8 * scale - 7, 0);
            gui.pose().scale(scale, scale, scale);
            gui.renderItem(MahjongSupplies.tile(data, DyeColor.BLUE, 1), 0, 0);
            gui.pose().popPose();
            gui.drawCenteredString(font, Component.translatable(data.flowerKey()), x + cellWidth / 2, y + cellHeight - 18, 0xffd0d9de);
        }
    }
}
