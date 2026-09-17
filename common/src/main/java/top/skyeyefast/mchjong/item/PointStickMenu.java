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
    private final DataSlot openedSide = DataSlot.standalone();
    private final DataSlot[] scores = new DataSlot[8];

    public PointStickMenu(int id, Inventory inventory) { this(id, inventory, null, 0); }

    public PointStickMenu(int id, Inventory inventory, MahjongTableBlockEntity table, int side) {
        super(MahjongContent.STICK_MENU, id);
        this.inventory = inventory;
        this.table = table;
        this.side = side;
        addDataSlot(withdrawal);
        addDataSlot(openedSide);
        for (int i = 0; i < scores.length; i++) addDataSlot(scores[i] = DataSlot.standalone());
        for (int row = 0; row < 4; row++) {
            int owner = row;
            Container contents = table == null ? new SimpleContainer(TableEquipment.STICK_SLOTS) : table.equipment().drawer(row);
            for (int slot = 0; slot < TableEquipment.STICK_SLOTS; slot++)
                addSlot(new Slot(contents, slot, 113 + slot * 18, 22 + row * 24) {
                    @Override public boolean mayPlace(ItemStack stack) { return stillValid(inventory.player) && validStick(stack); }
                    @Override public boolean mayPickup(Player player) { return stillValid(player) && canWithdraw(owner); }
                });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 113 + col * 18, 128 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 113 + col * 18, 186));
    }

    public static boolean validStick(ItemStack stack) {
        return stack.is(MahjongContent.POINT_STICK) && MahjongSupplies.storable(stack)
            && stack.getOrDefault(MahjongComponents.POINTS, 0) > 0;
    }

    public int totalPoints(int row) {
        int total = 0;
        for (int slot = 0; slot < TableEquipment.STICK_SLOTS; slot++) {
            ItemStack stack = slots.get(row * TableEquipment.STICK_SLOTS + slot).getItem();
            total += stack.getCount() * stack.getOrDefault(MahjongComponents.POINTS, 0);
        }
        return total;
    }

    public int openedSide() { return openedSide.get(); }
    public int score(int row) { return scores[row * 2].get() & 0xffff | scores[row * 2 + 1].get() << 16; }
    public boolean canWithdraw(int row) {
        return table == null ? (withdrawal.get() & 1 << row) != 0 : table.canWithdrawSticks(inventory.player, row);
    }

    @Override public void broadcastChanges() {
        if (table != null) {
            int mask = 0;
            openedSide.set(side);
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
        super.clicked(slot, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack source = slot.getItem(), original = source.copy();
        if (index < DRAWER_SLOTS) {
            if (!moveItemStackTo(source, DRAWER_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!validStick(source) || !moveItemStackTo(source, side * TableEquipment.STICK_SLOTS,
            (side + 1) * TableEquipment.STICK_SLOTS, false)) return ItemStack.EMPTY;
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
