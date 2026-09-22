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

/** A physical drawer, accessible to nearby players between games and participants during play. */
public final class PointStickMenu extends AbstractContainerMenu {
    public static final int DRAWER_SLOTS = 4 * TableEquipment.STICK_SLOTS;
    private final Inventory inventory;
    private final MahjongTableBlockEntity table;
    private final int side;
    private boolean active = true;
    private final DataSlot withdrawal = DataSlot.standalone();
    private final DataSlot recipientSide = DataSlot.standalone();
    private final DataSlot locked = DataSlot.standalone();
    private boolean internalCursor;
    public boolean belongsTo(MahjongTableBlockEntity candidate) { return table == candidate; }
    private final DataSlot[] scores = new DataSlot[8];

    public PointStickMenu(int id, Inventory inventory) { this(id, inventory, null, 0); }

    public PointStickMenu(int id, Inventory inventory, MahjongTableBlockEntity table, int side) {
        super(MahjongContent.STICK_MENU, id);
        this.inventory = inventory;
        this.table = table;
        this.side = side;
        recipientSide.set(side);
        addDataSlot(withdrawal);
        addDataSlot(recipientSide);
        addDataSlot(locked);
        for (int i = 0; i < scores.length; i++) addDataSlot(scores[i] = DataSlot.standalone());
        for (int row = 0; row < 4; row++) {
            int owner = row;
            Container contents = table == null ? new SimpleContainer(TableEquipment.STICK_SLOTS) : table.equipment().drawer(row);
            for (int slot = 0; slot < TableEquipment.STICK_SLOTS; slot++) {
                boolean reserve = slot == TableEquipment.BUST_SLOT;
                addSlot(new Slot(contents, slot, 113 + slot * 18 + (reserve ? 4 : 0), 22 + row * 24) {
                    @Override public int getMaxStackSize() { return reserve ? 1 : super.getMaxStackSize(); }
                    @Override public boolean mayPlace(ItemStack stack) {
                        return stillValid(inventory.player) && validStick(stack) && (!locked() || internalCursor)
                            && (!reserve || MahjongComponents.points(stack) == -10000);
                    }
                    @Override public boolean mayPickup(Player player) { return stillValid(player) && canWithdraw(owner); }
                });
            }
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            playerSlot(col + row * 9 + 9, 113 + col * 18, 128 + row * 18);
        for (int col = 0; col < 9; col++) playerSlot(col, 113 + col * 18, 186);
    }

    private boolean locked() { return table == null ? locked.get() != 0 : !table.equipmentEditable(); }

    private void playerSlot(int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override public boolean mayPlace(ItemStack stack) { return !locked(); }
            @Override public boolean mayPickup(Player player) { return !locked(); }
        });
    }

    public static boolean validStick(ItemStack stack) {
        return stack.is(MahjongContent.POINT_STICK) && MahjongSupplies.storable(stack)
            && MahjongComponents.points(stack) != 0;
    }

    public int totalPoints(int row) {
        int total = 0;
        for (int slot = 0; slot < TableEquipment.BUST_SLOT; slot++) {
            ItemStack stack = slots.get(row * TableEquipment.STICK_SLOTS + slot).getItem();
            total += stack.getCount() * MahjongComponents.points(stack);
        }
        return total;
    }

    public int recipientSide() { return recipientSide.get(); }
    public int score(int row) { return scores[row * 2].get() & 0xffff | scores[row * 2 + 1].get() << 16; }
    public boolean canWithdraw(int row) {
        return table == null ? (withdrawal.get() & 1 << row) != 0 : table.canWithdrawSticks(inventory.player, row);
    }

    @Override public void broadcastChanges() {
        if (table != null) {
            int mask = 0;
            locked.set(locked() ? 1 : 0);
            for (int row = 0; row < 4; row++) {
                if (canWithdraw(row)) mask |= 1 << row;
                int score = table.pointScore(row);
                scores[row * 2].set(score & 0xffff);
                scores[row * 2 + 1].set(score >>> 16);
            }
            withdrawal.set(mask);
        }
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        if (player != inventory.player) return false;
        if (player.level().isClientSide) return active;
        active &= table != null && player.containerMenu == this && table.canUseSticks(player, side);
        return active;
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || slot >= slots.size()) return;
        if (locked() && (slot >= DRAWER_SLOTS || type == ClickType.SWAP || type == ClickType.CLONE
            || type == ClickType.QUICK_MOVE || type == ClickType.THROW || slot < 0 && type == ClickType.PICKUP
            || !getCarried().isEmpty() && !internalCursor)) return;
        if (slot >= 0 && slot < DRAWER_SLOTS && type == ClickType.PICKUP && internalCursor
            && mergeDelivery(slots.get(slot), button)) return;
        boolean empty = getCarried().isEmpty();
        super.clicked(slot, button, type, player);
        if (getCarried().isEmpty()) internalCursor = false;
        else if (empty) internalCursor = slot >= 0 && slot < DRAWER_SLOTS;
    }

    private boolean mergeDelivery(Slot target, int button) {
        ItemStack carried = getCarried(), stored = target.getItem();
        if (carried.isEmpty() || stored.isEmpty() || !validStick(carried) || !validStick(stored)
            || MahjongComponents.points(carried) != MahjongComponents.points(stored)
            || ItemStack.isSameItemSameTags(carried, stored) || !target.mayPlace(carried)) return false;
        int moved = Math.min(button == 1 ? 1 : carried.getCount(), target.getMaxStackSize() - stored.getCount());
        if (moved <= 0) return false;
        stored.grow(moved);
        carried.shrink(moved);
        target.setChanged();
        if (carried.isEmpty()) {
            setCarried(ItemStack.EMPTY);
            internalCursor = false;
        }
        return true;
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player) || id < 0 || id >= 4 || table != null && !table.canReceiveSticks(id)) return false;
        recipientSide.set(id);
        return true;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || locked() || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack source = slot.getItem(), original = source.copy();
        if (index < DRAWER_SLOTS) {
            if (!moveItemStackTo(source, DRAWER_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean reserve = MahjongComponents.points(source) == -10000;
            int start = recipientSide() * TableEquipment.STICK_SLOTS + (reserve ? TableEquipment.BUST_SLOT : 0);
            int end = recipientSide() * TableEquipment.STICK_SLOTS + (reserve ? TableEquipment.STICK_SLOTS : TableEquipment.BUST_SLOT);
            if (!validStick(source) || !moveItemStackTo(source, start, end, false)) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }

    @Override public void removed(Player player) {
        if (table != null && internalCursor && (locked() || table.equipment().matchActive())) {
            table.equipment().returnSticks(getCarried(), side);
            setCarried(ItemStack.EMPTY);
            internalCursor = false;
        }
        active = false;
        resetQuickCraft();
        super.removed(player);
    }
}
