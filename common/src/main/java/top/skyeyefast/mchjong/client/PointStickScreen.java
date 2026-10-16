package top.skyeyefast.mchjong.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import top.skyeyefast.mchjong.item.PointStickMenu;

public final class PointStickScreen extends AbstractContainerScreen<PointStickMenu> {
    public net.minecraft.client.gui.navigation.ScreenRectangle browserBounds() {
        return new net.minecraft.client.gui.navigation.ScreenRectangle(leftPos, topPos, imageWidth, imageHeight);
    }
    private final net.minecraft.client.gui.screens.Screen parent;
    private final net.minecraft.core.BlockPos tablePos;
    public PointStickScreen(PointStickMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        var screen = net.minecraft.client.Minecraft.getInstance().screen;
        var riichi = RiichiTableScreen.active(screen);
        var mcr = McrTableScreen.active(screen);
        var results = TableChildScreen.root(screen) instanceof TableResultsScreen result ? result : null;
        parent = riichi != null ? riichi : mcr != null ? mcr : results;
        tablePos = riichi != null ? riichi.tablePos() : mcr != null ? mcr.tablePos() : results != null ? results.tablePos() : null;
        imageWidth = 304;
        imageHeight = 216;
    }

    @Override protected void init() {
        super.init();
        topPos = Math.min(topPos, height - imageHeight - 24);
    }

    @Override public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, MahjongUi.BACKDROP);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        MahjongUi.panel(graphics, leftPos, topPos, imageWidth, imageHeight);
        graphics.fill(leftPos + 1, topPos + 1, leftPos + imageWidth - 1, topPos + 3, MahjongUi.ACCENT);
        for (var slot : menu.slots) MahjongUi.slot(graphics, leftPos + slot.x, topPos + slot.y,
            slot.index < PointStickMenu.DRAWER_SLOTS && slot.index / top.skyeyefast.mchjong.world.TableEquipment.STICK_SLOTS == menu.recipientSide());
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        MahjongUi.text(graphics, font, title, 10, 8, 266, MahjongUi.TEXT, true);
        var room = tablePos != null && minecraft.level != null
            && minecraft.level.getBlockEntity(tablePos) instanceof top.skyeyefast.mchjong.world.MahjongTableBlockEntity table
            ? table.clientRoom() : null;
        for (int row = 0; row < 4; row++) {
            int y = 22 + row * 24;
            Component name = room != null && row < room.seats().size() ? RoomMembers.name(room, row)
                : Component.translatable(menu.cards() ? "cards.mchjong.seat" : "sticks.mchjong.seat", row + 1);
            MahjongUi.text(graphics, font, name, 10, y, 96, menu.canWithdraw(row) ? MahjongUi.POSITIVE : MahjongUi.TEXT, false);
            int total = menu.totalPoints(row);
            MahjongUi.text(graphics, font, Component.literal(total + " / " + menu.score(row)), 10, y + 10, 96,
                total == menu.score(row) ? MahjongUi.POSITIVE : MahjongUi.ACCENT, false);
        }
        MahjongUi.text(graphics, font, playerInventoryTitle, 113, 116, 162, MahjongUi.MUTED, false);
        int y = 128;
        for (var line : font.split(Component.translatable(menu.cards() ? "cards.mchjong.deliver" : "sticks.mchjong.deliver"), 94)) {
            graphics.drawString(font, line, 10, y, MahjongUi.MUTED, false);
            y += 10;
        }
        MahjongUi.text(graphics, font, Component.translatable(menu.cards() ? "cards.mchjong.balance" : "sticks.mchjong.balance"), 10, 204, 266, MahjongUi.MUTED, true);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && hoveredSlot.index < PointStickMenu.DRAWER_SLOTS
            && hoveredSlot.index % top.skyeyefast.mchjong.world.TableEquipment.STICK_SLOTS == top.skyeyefast.mchjong.world.TableEquipment.BUST_SLOT) {
            var lines = new java.util.ArrayList<Component>();
            if (hoveredSlot.hasItem()) lines.addAll(getTooltipFromItem(minecraft, hoveredSlot.getItem()));
            lines.add(Component.translatable(menu.cards() ? "cards.mchjong.debt_reserve" : "sticks.mchjong.bust_reserve"));
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        } else renderTooltip(graphics, mouseX, mouseY);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double vertical) {
        if (vertical == 0 || mouseX < leftPos || mouseX >= leftPos + imageWidth
            || mouseY < topPos || mouseY >= topPos + 118) return super.mouseScrolled(mouseX, mouseY, vertical);
        int players = 4;
        if (tablePos != null && minecraft.level != null
            && minecraft.level.getBlockEntity(tablePos) instanceof top.skyeyefast.mchjong.world.MahjongTableBlockEntity table
            && table.clientRoom() != null) players = table.clientRoom().seats().size();
        int target = Math.floorMod(menu.recipientSide() + (vertical > 0 ? -1 : 1), players);
        menu.clickMenuButton(minecraft.player, target);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, target);
        return true;
    }

    @Override public void onClose() {
        super.onClose();
        if (parent != null && minecraft.player != null && minecraft.player.isAlive()
            && minecraft.player.getVehicle() instanceof top.skyeyefast.mchjong.world.SeatEntity seat
            && seat.tablePos().equals(tablePos)) {
            if (parent instanceof McrResultsScreen results && minecraft.level != null
                && minecraft.level.getBlockEntity(tablePos) instanceof top.skyeyefast.mchjong.world.MahjongTableBlockEntity table
                && table.clientMcrView() != null && table.clientMcrView().game().result() == null)
                minecraft.setScreen(new McrTableScreen(tablePos, results.immersive()));
            else minecraft.setScreen(parent);
        }
    }
}
