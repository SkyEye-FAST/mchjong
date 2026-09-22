package top.skyeyefast.mchjong.item;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-owned carrier inventory with native slot synchronization and a dedicated client screen. */
public final class MahjongBoxMenu extends AbstractContainerMenu {
    public static final int DYE_BACK_BUTTON = 0;
    private final Inventory inventory;
    private final DataSlot ownerSlot = DataSlot.standalone();
    private ItemStack box = ItemStack.EMPTY;
    private final SimpleContainer contents = new SimpleContainer(MahjongSupplies.BOX_SLOTS);
    private boolean active = true;
    private boolean updating;

    public MahjongBoxMenu(int id, Inventory inventory) {
        super(top.skyeyefast.mchjong.world.MahjongContent.BOX_MENU, id);
        this.inventory = inventory;
        ownerSlot.set(-1);
        addDataSlot(ownerSlot);
        for (int index = 0; index < MahjongSupplies.BOX_SLOTS; index++) {
            int slot = index;
            int x = slot == MahjongSupplies.DYE_SLOT ? 196 : slot == MahjongSupplies.DICE_SLOT ? 176 : 14 + (slot % 9) * 18;
            int y = slot < MahjongSupplies.TILE_SLOTS ? 16 + slot / 9 * 18 : slot == MahjongSupplies.DYE_SLOT ? 83 : 114;
            addSlot(new Slot(contents, slot, x, y) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return stillValid(inventory.player) && MahjongSupplies.boxAccepts(slot, stack);
                }
                @Override public boolean mayPickup(Player player) { return stillValid(player); }
            });
        }
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            playerSlot(col + row * 9 + 9, 14 + col * 18, 142 + row * 18);
        for (int col = 0; col < 9; col++) playerSlot(col, 14 + col * 18, 198);
    }

    public MahjongBoxMenu(int id, Inventory inventory, int ownerSlot) {
        this(id, inventory);
        this.ownerSlot.set(ownerSlot);
        box = inventory.getItem(ownerSlot);
        if (!MahjongSupplies.validBox(box)) throw new IllegalArgumentException("Invalid mahjong box");
        var stored = MahjongSupplies.contents(box);
        for (int i = 0; i < stored.size(); i++) contents.setItem(i, stored.get(i));
        contents.addListener(container -> save());
    }

    private void save() {
        if (updating || !stillValid(inventory.player)) return;
        MahjongSupplies.setContents(box, items());
        inventory.setChanged();
    }

    public boolean canEngrave(TileFacePreset preset) {
        return MahjongSupplies.mahjongDye(contents.getItem(MahjongSupplies.DYE_SLOT))
            && !MahjongSupplies.engravedContents(items(), preset).isEmpty();
    }

    public boolean canDyeBack() {
        var reagent = contents.getItem(MahjongSupplies.DYE_SLOT);
        if (!(reagent.getItem() instanceof net.minecraft.world.item.DyeItem dye)) return false;
        var color = dye.getDyeColor();
        return items().subList(0, MahjongSupplies.TILE_SLOTS).stream()
            .anyMatch(stack -> stack.is(top.skyeyefast.mchjong.world.MahjongContent.TILE_ITEM)
                && MahjongSupplies.back(stack) != color);
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        return !player.level().isClientSide && stillValid(player) && id == DYE_BACK_BUTTON && dyeBack();
    }

    public boolean print(Player player, TileFacePreset preset) {
        if (player.level().isClientSide || !stillValid(player)) return false;
        var dye = contents.getItem(MahjongSupplies.DYE_SLOT);
        if (!MahjongSupplies.mahjongDye(dye)) return false;
        var output = MahjongSupplies.engravedContents(items(), preset);
        if (output.isEmpty()) return false;
        if (dye.is(top.skyeyefast.mchjong.world.MahjongContent.MAHJONG_DYE)) output.get(MahjongSupplies.DYE_SLOT).shrink(1);
        updating = true;
        try {
            for (int i = 0; i < output.size(); i++) contents.setItem(i, output.get(i));
        } finally { updating = false; }
        save();
        broadcastChanges();
        return true;
    }

    private boolean dyeBack() {
        var reagent = contents.getItem(MahjongSupplies.DYE_SLOT);
        if (!(reagent.getItem() instanceof net.minecraft.world.item.DyeItem dye) || !canDyeBack()) return false;
        var output = MahjongSupplies.dyedContents(items(), dye.getDyeColor());
        if (output.isEmpty()) return false;
        output.get(MahjongSupplies.DYE_SLOT).shrink(1);
        updating = true;
        try {
            for (int i = 0; i < output.size(); i++) contents.setItem(i, output.get(i));
        } finally { updating = false; }
        save();
        broadcastChanges();
        return true;
    }

    public int ownerSlot() { return ownerSlot.get(); }
    public java.util.List<ItemStack> items() {
        return java.util.stream.IntStream.range(0, contents.getContainerSize()).mapToObj(contents::getItem).toList();
    }

    private void playerSlot(int inventorySlot, int x, int y) {
        addSlot(new Slot(inventory, inventorySlot, x, y) {
            @Override public boolean mayPickup(Player player) { return inventorySlot != ownerSlot() && stillValid(player); }
            @Override public boolean mayPlace(ItemStack stack) { return inventorySlot != ownerSlot() && stillValid(inventory.player); }
        });
    }

    @Override public boolean stillValid(Player player) {
        if (player != inventory.player) return false;
        if (player.level().isClientSide) return active && ownerSlot() >= 0;
        // Once detached, dead or closed, this menu must never become live again.
        active &= player.containerMenu == this && player.isAlive() && !player.isRemoved() && !player.isSpectator()
            && inventory.getItem(ownerSlot()) == box && box.getCount() == 1;
        return active;
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!stillValid(player) || slot >= slots.size() || type == ClickType.SWAP && button == ownerSlot()) return;
        if (slot >= 0 && slots.get(slot).container == inventory && slots.get(slot).getContainerSlot() == ownerSlot()) return;
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
        if (index >= MahjongSupplies.BOX_SLOTS && !MahjongSupplies.storable(source) && !MahjongSupplies.dyeSlotItem(source)) return ItemStack.EMPTY;
        ItemStack original = source.copy();
        if (index < MahjongSupplies.BOX_SLOTS) {
            if (!moveItemStackTo(source, MahjongSupplies.BOX_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int start = source.is(top.skyeyefast.mchjong.world.MahjongContent.TILE_ITEM) ? 0
                : source.is(top.skyeyefast.mchjong.world.MahjongContent.POINT_STICK) ? MahjongSupplies.TILE_SLOTS
                : source.is(top.skyeyefast.mchjong.world.MahjongContent.DICE) ? MahjongSupplies.DICE_SLOT : MahjongSupplies.DYE_SLOT;
            int end = start == 0 ? MahjongSupplies.TILE_SLOTS : start == MahjongSupplies.TILE_SLOTS
                ? MahjongSupplies.DYE_SLOT : start + 1;
            if (!moveItemStackTo(source, start, end, false)) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }
}
