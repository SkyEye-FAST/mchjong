package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import top.skyeyefast.mchjong.item.PointStickMenu;

public final class PointStickScreen extends AbstractContainerScreen<PointStickMenu> {
    public net.minecraft.client.gui.navigation.ScreenRectangle browserBounds() {
        return new net.minecraft.client.gui.navigation.ScreenRectangle(leftPos, topPos, imageWidth, imageHeight);
    }
    private final TableScreen parent;
    public PointStickScreen(PointStickMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 304, 216);
        parent = TableScreen.active(net.minecraft.client.Minecraft.getInstance().screen);
    }

    @Override protected void init() {
        super.init();
        topPos = Math.min(topPos, height - imageHeight - 24);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, MahjongUi.BACKDROP);
        extractPanel(graphics, partialTick, mouseX, mouseY);
    }

    private void extractPanel(GuiGraphicsExtractor graphics, float partialTick, int mouseX, int mouseY) {
        MahjongUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, MahjongUi.ACCENT);
        for (var slot : menu.slots) MahjongUi.slot(graphics, leftPos + slot.x, topPos + slot.y,
            slot.index < PointStickMenu.DRAWER_SLOTS && slot.index / top.skyeyefast.mchjong.world.TableEquipment.STICK_SLOTS == menu.recipientSide());
    }

    @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        MahjongUi.text(graphics, font, title, 10, 8, 266, MahjongUi.TEXT, true);
        top.skyeyefast.mchjong.engine.TableView view = parent != null && minecraft.level != null
            && minecraft.level.getBlockEntity(parent.tablePos()) instanceof top.skyeyefast.mchjong.world.MahjongTableBlockEntity table
            ? table.clientView() : null;
        for (int row = 0; row < 4; row++) {
            int y = 22 + row * 24;
            Component name = view != null && row < view.seats().size() ? TableScreen.playerName(view, row)
                : Component.translatable("sticks.mchjong.seat", row + 1);
            MahjongUi.text(graphics, font, name, 10, y, 96, menu.canWithdraw(row) ? MahjongUi.POSITIVE : MahjongUi.TEXT, false);
            int total = menu.totalPoints(row);
            MahjongUi.text(graphics, font, Component.literal(total + " / " + menu.score(row)), 10, y + 10, 96,
                total == menu.score(row) ? MahjongUi.POSITIVE : MahjongUi.ACCENT, false);
        }
        MahjongUi.text(graphics, font, playerInventoryTitle, 113, 116, 162, MahjongUi.MUTED, false);
        int y = 128;
        for (var line : font.split(Component.translatable("sticks.mchjong.deliver"), 94)) {
            graphics.text(font, line, 10, y, MahjongUi.MUTED, false);
            y += 10;
        }
        MahjongUi.text(graphics, font, Component.translatable("sticks.mchjong.balance"), 10, 204, 266, MahjongUi.MUTED, true);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && hoveredSlot.index < PointStickMenu.DRAWER_SLOTS
            && hoveredSlot.index % top.skyeyefast.mchjong.world.TableEquipment.STICK_SLOTS == top.skyeyefast.mchjong.world.TableEquipment.BUST_SLOT) {
            var lines = new java.util.ArrayList<Component>();
            if (hoveredSlot.hasItem()) lines.addAll(getTooltipFromItem(minecraft, hoveredSlot.getItem()));
            lines.add(Component.translatable("sticks.mchjong.bust_reserve"));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical == 0 || mouseX < leftPos || mouseX >= leftPos + imageWidth
            || mouseY < topPos || mouseY >= topPos + 118) return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        int players = 4;
        if (parent != null && minecraft.level != null
            && minecraft.level.getBlockEntity(parent.tablePos()) instanceof top.skyeyefast.mchjong.world.MahjongTableBlockEntity table
            && table.clientView() != null) players = table.clientView().seats().size();
        int target = Math.floorMod(menu.recipientSide() + (vertical > 0 ? -1 : 1), players);
        menu.clickMenuButton(minecraft.player, target);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, target);
        return true;
    }

    @Override public void onClose() {
        super.onClose();
        if (parent != null && minecraft.player != null && minecraft.player.isAlive()
            && minecraft.player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat
            && seat.tablePos().equals(parent.tablePos())) minecraft.setScreen(parent);
    }
}
