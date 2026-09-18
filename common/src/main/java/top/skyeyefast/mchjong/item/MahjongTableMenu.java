package top.skyeyefast.mchjong.item;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.world.MahjongContent;
import top.skyeyefast.mchjong.world.MahjongTableBlockEntity;
import top.skyeyefast.mchjong.world.TableEquipment;

/** Two server-owned case slots. All transfers use native container transactions. */
public final class MahjongTableMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final MahjongTableBlockEntity table;
    private final DataSlot cloth = DataSlot.standalone();
    private final DataSlot selectedBox = DataSlot.standalone();
    private boolean active = true;

    public MahjongTableMenu(int id, Inventory inventory) { this(id, inventory, null); }

    public MahjongTableMenu(int id, Inventory inventory, MahjongTableBlockEntity table) {
        super(MahjongContent.TABLE_MENU, id);
        this.inventory = inventory;
        this.table = table;
        Container contents = table == null ? new SimpleContainer(TableEquipment.BOX_SLOTS) : table.equipment().boxes();
        addDataSlot(cloth);
        selectedBox.set(-1);
        addDataSlot(selectedBox);
        for (int slot = 0; slot < TableEquipment.BOX_SLOTS; slot++)
            addSlot(new Slot(contents, slot, 78 + slot * 58, 36) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPlace(ItemStack stack) { return stillValid(inventory.player) && MahjongSupplies.validBox(stack); }
                @Override public boolean mayPickup(Player player) { return stillValid(player); }
            });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 34 + col * 18, 106 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 34 + col * 18, 164));
    }

    public boolean hasCloth() { return cloth.get() != 0; }
    public int activeBox() { return selectedBox.get(); }

    @Override public void broadcastChanges() {
        if (table != null) {
            cloth.set(table.equipment().hasCloth() ? 1 : 0);
            selectedBox.set(table.equipment().activeBox());
        }
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        if (player != inventory.player) return false;
        if (player.level().isClientSide) return active;
        active &= table != null && player.containerMenu == this && player.isAlive() && !player.isRemoved() && !player.isSpectator()
            && player.level() == table.getLevel() && !table.isRemoved() && table.equipmentEditable()
            && player.level().getBlockEntity(table.getBlockPos()) == table && player.distanceToSqr(table.getBlockPos().getCenter()) <= 64;
        return active;
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || slot >= slots.size()) return;
        super.clicked(slot, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack source = slot.getItem(), original = source.copy();
        if (index < TableEquipment.BOX_SLOTS) {
            if (!moveItemStackTo(source, TableEquipment.BOX_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 0, TableEquipment.BOX_SLOTS, false)) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }

    @Override public void removed(Player player) {
        active = false;
        resetQuickCraft();
        super.removed(player);
    }
}
