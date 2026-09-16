package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import top.skyeyefast.mchjong.item.MahjongBoxMenu;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.world.MahjongContent;

/** A supply case, not a chest skin: native inventory behavior with a read-only packing summary. */
public final class MahjongBoxScreen extends AbstractContainerScreen<MahjongBoxMenu> {
    public MahjongBoxScreen(MahjongBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 304;
        imageHeight = 232;
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MahjongUi.BACKDROP);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MahjongUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, MahjongUi.ACCENT);
        graphics.fill(leftPos + 184, topPos + 24, leftPos + 185, topPos + imageHeight - 10, MahjongUi.EDGE);
        for (Slot slot : menu.slots) MahjongUi.slot(graphics, leftPos + slot.x, topPos + slot.y, carrier(slot));
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MahjongUi.text(graphics, font, title, 14, 10, 276, MahjongUi.TEXT, false);
        MahjongUi.text(graphics, font, playerInventoryTitle, 14, 136, 160, MahjongUi.MUTED, false);
        var items = menu.items();
        int tiles = MahjongSupplies.tileCount(items);
        int occupied = (int) items.stream().filter(stack -> !stack.isEmpty()).count();
        int sticks = items.stream().filter(stack -> stack.is(MahjongContent.POINT_STICK)).mapToInt(stack -> stack.getCount()).sum();
        boolean ready = MahjongSupplies.deck(items) != null;
        int y = paragraph(graphics, Component.translatable("box.mchjong.contents"), 26, MahjongUi.TEXT);
        y = paragraph(graphics, Component.translatable("box.mchjong.tiles", tiles), y + 8, MahjongUi.TEXT);
        y = paragraph(graphics, Component.translatable("box.mchjong.sticks", sticks), y + 4, MahjongUi.MUTED);
        y = paragraph(graphics, Component.translatable("box.mchjong.slots", occupied, MahjongSupplies.BOX_SLOTS), y + 4, MahjongUi.MUTED);
        int barY = y + 6;
        graphics.fill(196, barY, 290, barY + 3, MahjongUi.INPUT);
        graphics.fill(196, barY, 196 + occupied * 94 / MahjongSupplies.BOX_SLOTS, barY + 3, MahjongUi.ACCENT);
        paragraph(graphics, Component.translatable(ready ? "box.mchjong.ready" : "box.mchjong.incomplete"), barY + 12,
            ready ? MahjongUi.POSITIVE : MahjongUi.ACCENT);
        paragraph(graphics, Component.translatable("box.mchjong.help"), 151, MahjongUi.MUTED);
    }

    private int paragraph(GuiGraphics graphics, Component text, int y, int color) {
        for (var line : font.split(text, 94)) {
            graphics.drawString(font, line, 196, y, color, false);
            y += 10;
        }
        return y;
    }

    private boolean carrier(Slot slot) {
        return slot.container == minecraft.player.getInventory() && slot.getContainerSlot() == menu.ownerSlot();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && carrier(hoveredSlot) && menu.getCarried().isEmpty()) {
            var lines = new java.util.ArrayList<>(getTooltipFromItem(minecraft, hoveredSlot.getItem()));
            lines.add(Component.translatable("box.mchjong.locked").withStyle(net.minecraft.ChatFormatting.GOLD));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else renderTooltip(graphics, mouseX, mouseY);
    }
}
