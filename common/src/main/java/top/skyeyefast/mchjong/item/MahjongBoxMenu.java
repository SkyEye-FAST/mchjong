package top.skyeyefast.mchjong.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** Exactly the vanilla six-row chest slot protocol. No extra screen, payload or workstation. */
public final class MahjongBoxMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final int ownerSlot;
    private final ItemStack box;
    private final SimpleContainer contents = new SimpleContainer(MahjongSupplies.BOX_SLOTS);
    private boolean active = true;

    public MahjongBoxMenu(int id, Inventory inventory, int ownerSlot) {
        super(MenuType.GENERIC_9x6, id);
        this.inventory = inventory;
        this.ownerSlot = ownerSlot;
        this.box = inventory.getItem(ownerSlot);
        if (!MahjongSupplies.validBox(box)) throw new IllegalArgumentException("Invalid mahjong box");
        var stored = MahjongSupplies.contents(box);
        for (int i = 0; i < stored.size(); i++) contents.setItem(i, stored.get(i));
        contents.addListener(container -> {
            if (!stillValid(inventory.player)) return;
            var items = java.util.stream.IntStream.range(0, contents.getContainerSize()).mapToObj(contents::getItem).toList();
            box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
            inventory.setChanged();
        });
        for (int row = 0; row < 6; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(contents, col + row * 9, 8 + col * 18, 18 + row * 18) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return stillValid(inventory.player) && MahjongSupplies.storable(stack);
                }
                @Override public boolean mayPickup(Player player) { return stillValid(player); }
            });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            playerSlot(col + row * 9 + 9, 8 + col * 18, 139 + row * 18);
        for (int col = 0; col < 9; col++) playerSlot(col, 8 + col * 18, 197);
    }

    private void playerSlot(int inventorySlot, int x, int y) {
        addSlot(new Slot(inventory, inventorySlot, x, y) {
            @Override public boolean mayPickup(Player player) { return inventorySlot != ownerSlot && stillValid(player); }
            @Override public boolean mayPlace(ItemStack stack) { return inventorySlot != ownerSlot && stillValid(inventory.player); }
        });
    }

    @Override public boolean stillValid(Player player) {
        if (player != inventory.player) return false;
        // Once detached, dead or closed, this menu must never become live again.
        active &= player.containerMenu == this && player.isAlive() && !player.isRemoved() && !player.isSpectator()
            && inventory.getItem(ownerSlot) == box && box.getCount() == 1;
        return active;
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || slot >= slots.size() || type == ClickType.SWAP && button == ownerSlot) return;
        if (slot >= 0 && slots.get(slot).container == inventory && slots.get(slot).getContainerSlot() == ownerSlot) return;
        super.clicked(slot, button, type, player);
    }

    @Override public void removed(Player player) {
        active = false;
        resetQuickCraft();
        // Vanilla returns/drops the cursor stack. Stored contents stay in the carrier.
        super.removed(player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.mayPickup(player) || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        if (index >= MahjongSupplies.BOX_SLOTS && !MahjongSupplies.storable(source)) return ItemStack.EMPTY;
        ItemStack original = source.copy();
        if (index < MahjongSupplies.BOX_SLOTS) {
            if (!moveItemStackTo(source, MahjongSupplies.BOX_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 0, MahjongSupplies.BOX_SLOTS, false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }
}
