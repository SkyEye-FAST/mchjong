package top.skyeyefast.mchjong.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.NonNullList;
import top.skyeyefast.mchjong.item.MahjongComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileMaterial;

/** Removable physical equipment and a strictly public appearance projection. Never holds game state. */
public final class TableEquipment {
    private ItemStack box = ItemStack.EMPTY;
    private ItemStack cloth = ItemStack.EMPTY;
    private MahjongSupplies.Deck deck;
    private boolean visibleBox;
    private int clothColor = -1;
    private TileMaterial material = TileMaterial.BONE;
    private DyeColor back = DyeColor.BLUE;
    private final NonNullList<ItemStack> sticks = NonNullList.withSize(4, ItemStack.EMPTY);
    private int[] stickValues = new int[4];
    private int[] stickCounts = new int[4];

    public boolean hasBox() { return visibleBox; }
    public boolean hasCloth() { return clothColor >= 0; }
    public DyeColor clothColor() { return DyeColor.byId(clothColor); }
    public TileMaterial material() { return material; }
    public DyeColor back() { return back; }
    public MahjongSupplies.Deck deck() { return deck; }
    public ItemStack boxCopy() { return box.copy(); }
    public int stickValue(int seat) { return stickValues[seat]; }
    public int stickCount(int seat) { return stickCounts[seat]; }

    public boolean placeStick(int seat, ItemStack source) {
        if (!source.is(MahjongContent.POINT_STICK) || !MahjongSupplies.storable(source)
            || source.getOrDefault(MahjongComponents.POINTS, 0) == 0) return false;
        ItemStack previous = sticks.get(seat);
        if (previous.isEmpty()) sticks.set(seat, source.copyWithCount(1));
        else if (ItemStack.isSameItemSameComponents(previous, source) && previous.getCount() < previous.getMaxStackSize()) previous.grow(1);
        else return false;
        refreshSticks();
        return true;
    }

    public ItemStack removeSticks(int seat) {
        ItemStack previous = sticks.set(seat, ItemStack.EMPTY);
        refreshSticks();
        return previous;
    }

    private void refreshSticks() {
        for (int seat = 0; seat < 4; seat++) {
            stickValues[seat] = sticks.get(seat).getOrDefault(MahjongComponents.POINTS, 0);
            stickCounts[seat] = sticks.get(seat).getCount();
        }
    }

    public ItemStack installBox(ItemStack source) {
        MahjongSupplies.Deck checked = MahjongSupplies.deck(source);
        if (checked == null) throw new IllegalArgumentException("Incomplete physical set");
        ItemStack previous = box;
        box = source.copyWithCount(1);
        deck = checked;
        visibleBox = true;
        material = checked.material();
        back = checked.back();
        return previous;
    }

    public ItemStack installCloth(ItemStack source) {
        if (!source.is(MahjongContent.CLOTH_ITEM)) throw new IllegalArgumentException("Not a table cloth");
        ItemStack previous = cloth;
        cloth = source.copyWithCount(1);
        clothColor = MahjongSupplies.color(cloth).getId();
        return previous;
    }

    public ItemStack removeBox() {
        ItemStack previous = box;
        box = ItemStack.EMPTY;
        deck = null;
        visibleBox = false;
        return previous;
    }

    public ItemStack removeCloth() {
        ItemStack previous = cloth;
        cloth = ItemStack.EMPTY;
        clothColor = -1;
        return previous;
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        // Empty slots are explicit in a private save, but absent from public appearance packets.
        tag.put("box", box.saveOptional(registries));
        tag.put("cloth", cloth.saveOptional(registries));
        ListTag placed = new ListTag();
        for (int seat = 0; seat < 4; seat++) if (!sticks.get(seat).isEmpty()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("seat", seat);
            entry.put("stack", sticks.get(seat).save(registries));
            placed.add(entry);
        }
        tag.put("placed_sticks", placed);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        // Update packets contain only the appearance fields, not either item stack.
        if (tag.contains("box")) {
            ItemStack stored = ItemStack.parseOptional(registries, tag.getCompound("box"));
            // Retain invalid equipment for removal rather than inventing a free replacement set.
            box = stored;
            deck = MahjongSupplies.deck(stored);
            visibleBox = !box.isEmpty();
            if (deck != null) { material = deck.material(); back = deck.back(); }
        }
        if (tag.contains("placed_sticks")) {
            sticks.clear();
            for (var value : tag.getList("placed_sticks", 10)) {
                CompoundTag entry = (CompoundTag) value;
                int seat = entry.getInt("seat");
                if (seat >= 0 && seat < 4) sticks.set(seat, ItemStack.parseOptional(registries, entry.getCompound("stack")));
            }
            refreshSticks();
        }
        if (tag.contains("cloth")) {
            cloth = ItemStack.parseOptional(registries, tag.getCompound("cloth"));
            clothColor = cloth.isEmpty() ? -1 : MahjongSupplies.color(cloth).getId();
        }
        if (tag.contains("has_box")) visibleBox = tag.getBoolean("has_box");
        if (tag.contains("cloth_color")) clothColor = tag.getInt("cloth_color");
        if (tag.contains("tile_back")) back = DyeColor.byId(tag.getInt("tile_back"));
        if (tag.contains("tile_material")) for (TileMaterial candidate : TileMaterial.values())
            if (candidate.getSerializedName().equals(tag.getString("tile_material"))) material = candidate;
        if (tag.getIntArray("stick_values").length == 4) stickValues = tag.getIntArray("stick_values");
        if (tag.getIntArray("stick_counts").length == 4) stickCounts = tag.getIntArray("stick_counts");
    }

    public void writeAppearance(CompoundTag tag) {
        tag.putBoolean("has_box", visibleBox);
        tag.putInt("cloth_color", clothColor);
        tag.putString("tile_material", material.getSerializedName());
        tag.putInt("tile_back", back.getId());
        tag.putIntArray("stick_values", stickValues);
        tag.putIntArray("stick_counts", stickCounts);
    }
}
