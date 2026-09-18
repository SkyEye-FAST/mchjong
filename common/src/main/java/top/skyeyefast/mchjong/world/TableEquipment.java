package top.skyeyefast.mchjong.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.item.MahjongSupplies;
import top.skyeyefast.mchjong.item.TileMaterial;
import top.skyeyefast.mchjong.item.TileFacePreset;

/** Removable physical equipment and a strictly public appearance projection. Never holds game state. */
public final class TableEquipment {
    public static final int BOX_SLOTS = 2;
    public static final int STICK_SLOTS = 9;
    private final net.minecraft.world.SimpleContainer boxes = new net.minecraft.world.SimpleContainer(BOX_SLOTS);
    private final SimpleContainer[] drawers = new SimpleContainer[4];
    private ItemStack cloth = ItemStack.EMPTY;
    private MahjongSupplies.Deck deck;
    private top.skyeyefast.mchjong.engine.RuleConfig rules = top.skyeyefast.mchjong.engine.RuleSet.MAHJONG_SOUL_4.config();
    private int activeBox = -1;
    private boolean loading;
    private int clothColor = -1;
    private TileMaterial material = TileMaterial.BONE;
    private DyeColor back = DyeColor.BLUE;
    private TileFacePreset preset = TileFacePreset.KANSAI;

    public TableEquipment(Runnable changed) {
        boxes.addListener(container -> {
            refreshDeck();
            if (!loading) changed.run();
        });
        for (int side = 0; side < drawers.length; side++) {
            drawers[side] = new SimpleContainer(STICK_SLOTS);
            drawers[side].addListener(container -> { if (!loading) changed.run(); });
        }
    }

    public net.minecraft.world.Container boxes() { return boxes; }
    public net.minecraft.world.Container drawer(int side) { return drawers[side]; }
    public boolean hasCloth() { return clothColor >= 0; }
    public DyeColor clothColor() { return DyeColor.byId(clothColor); }
    public TileMaterial material() { return material; }
    public DyeColor back() { return back; }
    public TileFacePreset preset() { return preset; }
    public MahjongSupplies.Deck deck() { return deck; }
    public int activeBox() { return activeBox; }

    public boolean selectRules(top.skyeyefast.mchjong.engine.RuleConfig rules) {
        if (this.rules.equals(rules)) return false;
        this.rules = rules;
        var previous = deck;
        refreshDeck();
        return !java.util.Objects.equals(previous, deck);
    }

    private void refreshDeck() {
        deck = null;
        activeBox = -1;
        for (int slot = 0; slot < BOX_SLOTS && deck == null; slot++) {
            var candidate = MahjongSupplies.deck(boxes.getItem(slot));
            if (candidate != null && rules.allows(candidate.redFives())) { deck = candidate; activeBox = slot; }
        }
        material = deck == null ? TileMaterial.BONE : deck.material();
        back = deck == null ? DyeColor.BLUE : deck.back();
        preset = deck == null ? TileFacePreset.KANSAI : deck.preset();
    }

    public ItemStack installCloth(ItemStack source) {
        if (!source.is(MahjongContent.CLOTH_ITEM)) throw new IllegalArgumentException("Not a table cloth");
        ItemStack previous = cloth;
        cloth = source.copyWithCount(1);
        clothColor = MahjongSupplies.color(cloth).getId();
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
        ListTag storedBoxes = new ListTag();
        for (int slot = 0; slot < BOX_SLOTS; slot++) storedBoxes.add(boxes.getItem(slot).saveOptional(registries));
        tag.put("boxes", storedBoxes);
        tag.put("cloth", cloth.saveOptional(registries));
        ListTag storedSticks = new ListTag();
        for (var drawer : drawers) for (int slot = 0; slot < STICK_SLOTS; slot++)
            storedSticks.add(drawer.getItem(slot).saveOptional(registries));
        tag.put("stick_drawers", storedSticks);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        // Update packets contain only the appearance fields, not either item stack.
        if (tag.contains("boxes")) {
            loading = true;
            try {
                ListTag stored = tag.getList("boxes", 10);
                for (int slot = 0; slot < BOX_SLOTS; slot++)
                    boxes.setItem(slot, slot < stored.size() ? ItemStack.parseOptional(registries, stored.getCompound(slot)) : ItemStack.EMPTY);
            } finally { loading = false; }
        }
        if (tag.contains("stick_drawers")) {
            loading = true;
            try {
                ListTag stored = tag.getList("stick_drawers", 10);
                for (int side = 0; side < drawers.length; side++) for (int slot = 0; slot < STICK_SLOTS; slot++) {
                    int index = side * STICK_SLOTS + slot;
                    drawers[side].setItem(slot, index < stored.size()
                        ? ItemStack.parseOptional(registries, stored.getCompound(index)) : ItemStack.EMPTY);
                }
            } finally { loading = false; }
        }
        if (tag.contains("cloth")) {
            cloth = ItemStack.parseOptional(registries, tag.getCompound("cloth"));
            clothColor = cloth.isEmpty() ? -1 : MahjongSupplies.color(cloth).getId();
        }
        if (tag.contains("cloth_color")) clothColor = tag.getInt("cloth_color");
        if (tag.contains("tile_back")) back = DyeColor.byId(tag.getInt("tile_back"));
        if (tag.contains("tile_preset")) for (TileFacePreset candidate : TileFacePreset.values())
            if (candidate.getSerializedName().equals(tag.getString("tile_preset"))) preset = candidate;
        if (tag.contains("tile_material")) for (TileMaterial candidate : TileMaterial.values())
            if (candidate.getSerializedName().equals(tag.getString("tile_material"))) material = candidate;
    }

    public void writeAppearance(CompoundTag tag) {
        tag.putInt("cloth_color", clothColor);
        tag.putString("tile_material", material.getSerializedName());
        tag.putInt("tile_back", back.getId());
        tag.putString("tile_preset", preset.getSerializedName());
    }
}
