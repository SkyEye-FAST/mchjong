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
    public static final int STICK_SLOTS = 10;
    public static final int BUST_SLOT = 9;
    private final net.minecraft.world.SimpleContainer boxes = new net.minecraft.world.SimpleContainer(BOX_SLOTS);
    private final SimpleContainer[] drawers = new SimpleContainer[4];
    private ItemStack cloth = ItemStack.EMPTY;
    private MahjongSupplies.Deck deck;
    private top.skyeyefast.mchjong.engine.RuleConfig rules = top.skyeyefast.mchjong.engine.RuleSet.MAHJONG_SOUL_4.config()
        .with(top.skyeyefast.mchjong.engine.RuleOption.RED_FIVES, top.skyeyefast.mchjong.engine.RedFives.NONE.ordinal());
    private int activeBox = -1;
    private boolean loading;
    private java.util.List<ItemStack> matchSticks = java.util.List.of();
    private int clothColor = -1;
    private TileMaterial material = TileMaterial.BONE;
    private DyeColor back;
    private TileFacePreset preset = TileFacePreset.KANSAI;
    private net.minecraft.resources.ResourceLocation backPreset = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mchjong", "default");

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
    public net.minecraft.resources.ResourceLocation backPreset() { return backPreset; }
    public MahjongSupplies.Deck deck() { return deck; }
    public int activeBox() { return activeBox; }
    public boolean matchActive() { return !matchSticks.isEmpty(); }

    public void beginMatch() {
        matchSticks = java.util.stream.IntStream.range(0, 4 * STICK_SLOTS)
            .mapToObj(index -> drawers[index / STICK_SLOTS].getItem(index % STICK_SLOTS).copy()).toList();
    }

    public void endMatch() {
        if (!matchActive()) return;
        for (int index = 0; index < matchSticks.size(); index++)
            drawers[index / STICK_SLOTS].setItem(index % STICK_SLOTS, matchSticks.get(index).copy());
        matchSticks = java.util.List.of();
    }

    public void returnSticks(ItemStack carried, int side) {
        for (int pass = 0; pass < 2; pass++) for (int offset = 0; offset < 4; offset++) {
            var drawer = drawers[(side + offset) % 4];
            for (int slot = 0; slot < STICK_SLOTS && !carried.isEmpty(); slot++) {
                if (slot == BUST_SLOT && carried.getOrDefault(top.skyeyefast.mchjong.item.MahjongComponents.POINTS, 0) != -10000) continue;
                var target = drawer.getItem(slot);
                if (pass == 0 && ItemStack.isSameItemSameComponents(target, carried)) {
                    int count = Math.min(carried.getCount(), (slot == BUST_SLOT ? 1 : target.getMaxStackSize()) - target.getCount());
                    target.grow(count); carried.shrink(count); drawer.setChanged();
                } else if (pass == 1 && target.isEmpty()) drawer.setItem(slot, carried.split(slot == BUST_SLOT ? 1 : carried.getMaxStackSize()));
            }
        }
        if (!carried.isEmpty()) throw new IllegalStateException("Table payment no longer fits its drawers");
    }

    public boolean manualSuppliesReady() {
        return preparedSupplies() != null;
    }

    public boolean prepareMatch() {
        var prepared = preparedSupplies();
        if (prepared == null) return false;
        loading = true;
        try {
            for (int slot = 0; slot < BOX_SLOTS; slot++) boxes.setItem(slot, prepared.boxes().get(slot));
            for (int seat = 0; seat < 4; seat++) for (int slot = 0; slot < STICK_SLOTS; slot++)
                drawers[seat].setItem(slot, prepared.drawers().get(seat).get(slot));
        } finally { loading = false; }
        beginMatch();
        return true;
    }

    /** Fixed starting kit, retaining small payment denominations before larger sticks. */
    public static java.util.Map<Integer, Integer> startingKit(int points) {
        var kit = new java.util.LinkedHashMap<Integer, Integer>();
        for (int denomination : new int[]{100, 1000, 5000}) {
            int count = Math.min(denomination == 100 ? 10 : denomination == 1000 ? 4 : 2, points / denomination);
            kit.put(denomination, count);
            points -= count * denomination;
        }
        for (int denomination : new int[]{10000, 5000, 1000, 100}) {
            int count = points / denomination;
            kit.merge(denomination, count, Integer::sum);
            points -= count * denomination;
        }
        return java.util.Collections.unmodifiableMap(kit);
    }

    private record Supplies(java.util.List<ItemStack> boxes, java.util.List<java.util.List<ItemStack>> drawers) {}

    private Supplies preparedSupplies() {
        int dice = 0;
        var boxCopies = new java.util.ArrayList<ItemStack>();
        var contents = new java.util.ArrayList<java.util.List<ItemStack>>();
        for (int slot = 0; slot < BOX_SLOTS; slot++) {
            var box = boxes.getItem(slot).copy();
            boxCopies.add(box);
            var items = MahjongSupplies.validBox(box) ? MahjongSupplies.contents(box).stream().map(ItemStack::copy).toList() : java.util.List.<ItemStack>of();
            contents.add(items);
            if (!items.isEmpty()) dice += items.get(MahjongSupplies.DICE_SLOT).getCount();
        }
        if (dice < 2) return null;
        var copies = new java.util.ArrayList<java.util.List<ItemStack>>();
        for (int seat = 0; seat < 4; seat++) {
            var row = new java.util.ArrayList<ItemStack>();
            for (int slot = 0; slot < STICK_SLOTS; slot++) row.add(drawers[seat].getItem(slot).copy());
            copies.add(row);
        }
        var kit = new java.util.LinkedHashMap<>(startingKit(rules.startingPoints()));
        if (!rules.bankruptcy()) kit.put(-10000, 1);
        for (var entry : kit.entrySet()) {
            int available = 0;
            for (var items : contents) for (int slot = MahjongSupplies.TILE_SLOTS; !items.isEmpty() && slot < MahjongSupplies.DYE_SLOT; slot++)
                if (denomination(items.get(slot)) == entry.getKey()) available += items.get(slot).getCount();
            if (available < entry.getValue() * rules.players()) return null;
        }
        for (int seat = 0; seat < rules.players(); seat++) {
            var row = copies.get(seat);
            for (var entry : kit.entrySet()) {
                int denomination = entry.getKey(), missing = entry.getValue();
                int start = denomination < 0 ? BUST_SLOT : 0, end = denomination < 0 ? STICK_SLOTS : BUST_SLOT;
                for (int slot = start; slot < end; slot++) if (denomination(row.get(slot)) == denomination) missing -= row.get(slot).getCount();
                for (var items : contents) for (int slot = MahjongSupplies.TILE_SLOTS; !items.isEmpty() && slot < MahjongSupplies.DYE_SLOT && missing > 0; slot++) {
                    var source = items.get(slot);
                    if (denomination(source) != denomination) continue;
                    for (int pass = 0; pass < 2; pass++) for (int target = start; target < end && missing > 0 && !source.isEmpty(); target++) {
                        var stack = row.get(target);
                        if (pass == 0 && ItemStack.isSameItemSameComponents(source, stack)) {
                            int count = Math.min(missing, Math.min(source.getCount(), stack.getMaxStackSize() - stack.getCount()));
                            stack.grow(count); source.shrink(count); missing -= count;
                        } else if (pass == 1 && stack.isEmpty()) {
                            int count = Math.min(missing, Math.min(source.getCount(), source.getMaxStackSize()));
                            row.set(target, source.split(count)); missing -= count;
                        }
                    }
                }
                if (missing > 0) return null;
            }
        }
        for (int slot = 0; slot < BOX_SLOTS; slot++) if (!contents.get(slot).isEmpty())
            MahjongSupplies.setContents(boxCopies.get(slot), contents.get(slot));
        return new Supplies(boxCopies, copies);
    }

    private static int denomination(ItemStack stack) {
        return stack.is(MahjongContent.POINT_STICK) ? stack.getOrDefault(top.skyeyefast.mchjong.item.MahjongComponents.POINTS, 0) : 0;
    }

    public void abandonMatch() { matchSticks = java.util.List.of(); }

    public boolean canSupplyReds(boolean sanma, top.skyeyefast.mchjong.engine.RedFives reds) {
        for (int slot = 0; slot < BOX_SLOTS; slot++)
            if (MahjongSupplies.canSupplyReds(boxes.getItem(slot), sanma, reds)) return true;
        return false;
    }

    /** Six public capability bits, not an inventory dump: three compositions per player count. */
    public int redOptions() {
        int mask = 0;
        for (boolean sanma : new boolean[]{false, true}) for (var reds : top.skyeyefast.mchjong.engine.RedFives.values())
            if (canSupplyReds(sanma, reds)) mask |= 1 << ((sanma ? 3 : 0) + reds.ordinal());
        return mask;
    }

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
            var candidate = MahjongSupplies.deck(boxes.getItem(slot), rules.sanma(), rules.redFives());
            if (candidate != null) { deck = candidate; activeBox = slot; }
        }
        material = deck == null ? TileMaterial.BONE : deck.material();
        back = deck == null ? null : deck.back();
        preset = deck == null ? TileFacePreset.KANSAI : deck.preset();
        backPreset = deck == null ? net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mchjong", "default") : deck.backPreset();
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
        ListTag initialSticks = new ListTag();
        for (var stack : matchSticks) initialSticks.add(stack.saveOptional(registries));
        tag.put("match_sticks", initialSticks);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("match_sticks")) {
            var stored = tag.getList("match_sticks", 10);
            if (!stored.isEmpty() && stored.size() != 4 * STICK_SLOTS) throw new IllegalArgumentException("Invalid match drawers");
            matchSticks = java.util.stream.IntStream.range(0, stored.size())
                .mapToObj(index -> ItemStack.parseOptional(registries, stored.getCompound(index))).toList();
        }
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
        if (tag.contains("tile_back")) {
            int id = tag.getInt("tile_back");
            back = id < 0 ? null : DyeColor.byId(id);
        }
        if (tag.contains("tile_preset")) preset = new TileFacePreset(net.minecraft.resources.ResourceLocation.parse(tag.getString("tile_preset")));
        if (tag.contains("tile_back_preset")) backPreset = net.minecraft.resources.ResourceLocation.parse(tag.getString("tile_back_preset"));
        if (tag.contains("tile_material")) for (TileMaterial candidate : TileMaterial.values())
            if (candidate.getSerializedName().equals(tag.getString("tile_material"))) material = candidate;
    }

    public void writeAppearance(CompoundTag tag) {
        tag.putInt("cloth_color", clothColor);
        tag.putString("tile_material", material.getSerializedName());
        tag.putInt("tile_back", back == null ? -1 : back.getId());
        tag.putString("tile_preset", preset.getSerializedName());
        tag.putString("tile_back_preset", backPreset.toString());
    }
}
