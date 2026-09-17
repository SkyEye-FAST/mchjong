package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import top.skyeyefast.mchjong.item.PointStickMenu;

public final class PointStickScreen extends AbstractContainerScreen<PointStickMenu> {
    private final TableScreen parent;
    public PointStickScreen(PointStickMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        parent = TableScreen.active(net.minecraft.client.Minecraft.getInstance().screen);
        imageWidth = 286;
        imageHeight = 232;
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MahjongUi.BACKDROP);
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MahjongUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, MahjongUi.ACCENT);
        for (var slot : menu.slots) MahjongUi.slot(graphics, leftPos + slot.x, topPos + slot.y,
            slot.index < PointStickMenu.DRAWER_SLOTS && slot.index / top.skyeyefast.mchjong.world.TableEquipment.STICK_SLOTS == menu.openedSide());
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MahjongUi.text(graphics, font, title, 10, 8, 266, MahjongUi.TEXT, true);
        top.skyeyefast.mchjong.engine.TableView view = parent != null && minecraft.level != null
            && minecraft.level.getBlockEntity(parent.tablePos()) instanceof top.skyeyefast.mchjong.world.MahjongTableBlockEntity table
            ? table.clientView() : null;
        for (int row = 0; row < 4; row++) {
            int y = 30 + row * 24;
            Component name = view != null && row < view.seats().size() ? TableScreen.playerName(view, row)
                : Component.translatable("sticks.mchjong.seat", row + 1);
            MahjongUi.text(graphics, font, name, 10, y, 96, menu.canWithdraw(row) ? MahjongUi.POSITIVE : MahjongUi.TEXT, false);
            int total = menu.totalPoints(row);
            MahjongUi.text(graphics, font, Component.literal(total + " / " + menu.score(row)), 10, y + 10, 96,
                total == menu.score(row) ? MahjongUi.POSITIVE : MahjongUi.ACCENT, false);
        }
        MahjongUi.text(graphics, font, playerInventoryTitle, 113, 130, 162, MahjongUi.MUTED, false);
        int y = 142;
        for (var line : font.split(Component.translatable("sticks.mchjong.deliver"), 94)) {
            graphics.drawString(font, line, 10, y, MahjongUi.MUTED, false);
            y += 10;
        }
        MahjongUi.text(graphics, font, Component.translatable("sticks.mchjong.balance"), 10, 220, 266, MahjongUi.MUTED, true);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override public void onClose() {
        super.onClose();
        if (parent != null && minecraft.player != null && minecraft.player.isAlive()
            && minecraft.player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat
            && seat.tablePos().equals(parent.tablePos())) minecraft.setScreen(parent);
    }
}
