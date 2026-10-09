package top.skyeyefast.mchjong.smoke;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.skyeyefast.mchjong.item.FurnitureWood;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Exercises both actual maid head-item entry points, including simultaneous skull equipment. */
final class MaidFurnitureSmokeScreen extends Screen {
    private final EntityMaid maid;
    MaidFurnitureSmokeScreen(EntityMaid maid) {
        super(Component.literal("Maid furniture: HEAD equipment / display slot"));
        this.maid = maid;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics) {}
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        graphics.fill(0, 0, width, height, 0xff18292e);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xffe3c082);
        var head = maid.getItemBySlot(EquipmentSlot.HEAD);
        var display = maid.getBackpackShowItem();
        var items = new net.minecraft.world.item.Item[]{MahjongContent.TABLE_ITEM, MahjongContent.AUTO_TABLE_ITEM, MahjongContent.STOOL_ITEM};
        try {
            for (int row = 0; row < 2; row++) for (int col = 0; col < 3; col++) {
                var stack = new ItemStack(items[col]);
                top.skyeyefast.mchjong.item.MahjongComponents.wood(stack, FurnitureWood.CHERRY);
                top.skyeyefast.mchjong.item.MahjongComponents.color(stack, net.minecraft.world.item.DyeColor.BLUE);
                maid.setItemSlot(EquipmentSlot.HEAD, row == 0 ? stack : new ItemStack(Items.SKELETON_SKULL));
                maid.setBackpackShowItem(row == 0 ? ItemStack.EMPTY : stack);
                int left = col * width / 3, right = (col + 1) * width / 3;
                int top = 24 + row * (height - 24) / 2, bottom = 24 + (row + 1) * (height - 24) / 2;
                graphics.drawCenteredString(font, stack.getHoverName(), (left + right) / 2, top, 0xfff1eee3);
                InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, (left + right) / 2, bottom - 8,
                    (bottom - top - 20) / 3, 30, 15, maid);
            }
        } finally {
            maid.setItemSlot(EquipmentSlot.HEAD, head);
            maid.setBackpackShowItem(display);
        }
    }
}
