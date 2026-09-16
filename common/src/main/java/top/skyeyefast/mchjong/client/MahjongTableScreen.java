package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import top.skyeyefast.mchjong.item.MahjongTableMenu;

/** Read-only equipment summary around the two native case slots. */
public final class MahjongTableScreen extends AbstractContainerScreen<MahjongTableMenu> {
    public MahjongTableScreen(MahjongTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 230;
        imageHeight = 192;
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MahjongUi.BACKDROP);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MahjongUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, MahjongUi.ACCENT);
        for (var slot : menu.slots) MahjongUi.slot(graphics, leftPos + slot.x, topPos + slot.y, false);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MahjongUi.text(graphics, font, title, 12, 9, 206, MahjongUi.TEXT, true);
        int active = menu.activeBox();
        for (int slot = 0; slot < 2; slot++) {
            MahjongUi.text(graphics, font, Component.translatable("storage.mchjong.box", slot + 1),
                58 + slot * 58, 23, 56, MahjongUi.MUTED, true);
            String key = active == slot ? "storage.mchjong.active"
                : menu.slots.get(slot).hasItem() ? "storage.mchjong.stored" : "storage.mchjong.empty";
            MahjongUi.text(graphics, font, Component.translatable(key), 58 + slot * 58, 57, 56,
                active == slot ? MahjongUi.POSITIVE : MahjongUi.MUTED, true);
        }
        String status = !menu.hasCloth() ? "storage.mchjong.no_cloth" : active < 0 ? "storage.mchjong.no_set" : "storage.mchjong.ready";
        MahjongUi.text(graphics, font, Component.translatable(status), 12, 79, 206,
            menu.hasCloth() && active >= 0 ? MahjongUi.POSITIVE : MahjongUi.ACCENT, true);
        MahjongUi.text(graphics, font, playerInventoryTitle, 34, 94, 162, MahjongUi.MUTED, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
