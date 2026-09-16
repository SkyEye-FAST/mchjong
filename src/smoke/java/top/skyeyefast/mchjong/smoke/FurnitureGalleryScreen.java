package top.skyeyefast.mchjong.smoke;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.item.MahjongComponents;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileData;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Development-only contact sheets using the real item renderer, not mock artwork. */
final class FurnitureGalleryScreen extends Screen {
    private final boolean materials;

    FurnitureGalleryScreen(boolean materials) {
        super(Component.literal(materials ? "Wood finishes / white tile faces" : "Furniture detail gallery"));
        this.materials = materials;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        minecraft.getToasts().clear();
        gui.fill(0, 0, width, height, 0xff202831);
        gui.drawCenteredString(font, title, width / 2, 12, 0xffe4e9ee);
        if (materials) materials(gui);
        else details(gui);
    }

    private void details(GuiGraphics gui) {
        ItemStack table = furniture(MahjongContent.TABLE_ITEM, FurnitureWood.OAK);
        ItemStack automatic = furniture(MahjongContent.AUTO_TABLE_ITEM, FurnitureWood.CHERRY);
        ItemStack stool = furniture(MahjongContent.STOOL_ITEM, FurnitureWood.SPRUCE);
        stool.set(DataComponents.BASE_COLOR, DyeColor.GREEN);
        ItemStack cloth = new ItemStack(MahjongContent.CLOTH_ITEM);
        cloth.set(DataComponents.BASE_COLOR, DyeColor.GREEN);
        ItemStack[] items = {table, automatic, stool, new ItemStack(MahjongContent.BOX_ITEM), cloth,
            MahjongSupplies.tile(new TileData(22, TileMaterial.AMETHYST, false), DyeColor.BLUE, 1)};
        String[] labels = {"Ordinary table / oak", "Automatic table / cherry", "Cushioned stool / spruce",
            "Mahjong case", "Bound folded cloth", "White face / amethyst body"};
        int cellWidth = (width - 32) / 3;
        int cellHeight = (height - 48) / 2;
        for (int i = 0; i < items.length; i++) {
            int x = 16 + i % 3 * cellWidth;
            int y = 32 + i / 3 * cellHeight;
            gui.fill(x + 4, y + 4, x + cellWidth - 4, y + cellHeight - 4, 0xff2b3540);
            item(gui, items[i], x + cellWidth / 2, y + cellHeight / 2 - 7, Math.min(cellWidth, cellHeight) / 22f);
            gui.drawCenteredString(font, labels[i], x + cellWidth / 2, y + cellHeight - 18, 0xffd0d9de);
        }
    }

    private void materials(GuiGraphics gui) {
        int cellWidth = (width - 24) / 6;
        int woodHeight = (height - 58) / 4;
        var woods = FurnitureWood.values();
        for (int i = 0; i < woods.length; i++) {
            int x = 12 + i % 6 * cellWidth + cellWidth / 2;
            int y = 42 + i / 6 * woodHeight;
            item(gui, furniture(MahjongContent.TABLE_ITEM, woods[i]), x, y + woodHeight / 3, woodHeight / 18f);
            gui.drawCenteredString(font, woods[i].getSerializedName(), x, y + woodHeight - 10, 0xffd0d9de);
        }
        int top = 48 + woodHeight * 2;
        gui.drawCenteredString(font, "Every material uses an opaque white face", width / 2, top, 0xffe4e9ee);
        for (var material : TileMaterial.values()) {
            int x = 12 + material.ordinal() * cellWidth + cellWidth / 2;
            item(gui, MahjongSupplies.tile(new TileData(22, material, false), DyeColor.BLUE, 1),
                x, top + (height - top) / 2, Math.min(cellWidth, height - top - 30) / 20f);
            gui.drawCenteredString(font, material.getSerializedName(), x, height - 20, 0xffd0d9de);
        }
    }

    private static ItemStack furniture(net.minecraft.world.item.Item item, FurnitureWood wood) {
        var stack = new ItemStack(item);
        stack.set(MahjongComponents.WOOD, wood);
        return stack;
    }

    private static void item(GuiGraphics gui, ItemStack stack, int x, int y, float scale) {
        gui.pose().pushPose();
        gui.pose().translate(x - 8 * scale, y - 8 * scale, 0);
        gui.pose().scale(scale, scale, scale);
        gui.renderItem(stack, 0, 0);
        gui.pose().popPose();
    }
}
