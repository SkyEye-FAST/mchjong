package top.skyeyefast.mchjong.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.engine.RedFives;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Pure stack transformations: callers commit the returned copy, never mutate recipe inputs. */
public final class MahjongSupplies {
    public static final int BOX_SLOTS = 56;
    public static final int TILE_SLOTS = 45;
    public static final int DYE_SLOT = 54;
    public static final int DICE_SLOT = 55;
    public static final int SET_SIZE = 136;
    private MahjongSupplies() {}

    public static DyeColor color(ItemStack stack) { var color = back(stack); return color == null ? DyeColor.BLUE : color; }
    public static DyeColor back(ItemStack stack) { return MahjongComponents.color(stack); }
    public static TileData tile(ItemStack stack) { return MahjongComponents.tile(stack); }
    public static ItemStack tile(TileData data, int count) { return tile(data, null, count); }
    public static ItemStack tile(TileData data, DyeColor color, int count) {
        if (!data.valid()) throw new IllegalArgumentException("Invalid tile data");
        ItemStack stack = new ItemStack(MahjongContent.TILE_ITEM, count);
        MahjongComponents.tile(stack, data);
        if (color != null) MahjongComponents.color(stack, color);
        return stack;
    }

    public static NonNullList<ItemStack> contents(ItemStack box) {
        var preset = MahjongComponents.boxPreset(box);
        var stored = box.getTagElement("mchjong");
        if (preset != null && (stored == null || stored.getList("Items", 10).isEmpty())) return stockedContents(preset);
        NonNullList<ItemStack> result = NonNullList.withSize(BOX_SLOTS, ItemStack.EMPTY);
        if (stored != null) ContainerHelper.loadAllItems(stored, result);
        return result;
    }

    public static void setContents(ItemStack box, List<ItemStack> items) {
        var data = box.getOrCreateTagElement("mchjong");
        data.remove("box_preset");
        var stored = NonNullList.withSize(items.size(), ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) stored.set(i, items.get(i).copy());
        ContainerHelper.saveAllItems(data, stored);
        MahjongComponents.normalize(box.getItem(), box.getTag());
        if (box.getTag().isEmpty()) box.setTag(null);
    }

    public static boolean storable(ItemStack stack) {
        return (stack.is(MahjongContent.TILE_ITEM) || stack.is(MahjongContent.POINT_STICK) || stack.is(MahjongContent.DICE))
            && !hasStorage(stack) && MahjongComponents.valid(stack);
    }

    public static boolean mahjongDye(ItemStack stack) {
        return stack.is(MahjongContent.MAHJONG_DYE) || stack.is(MahjongContent.CREATIVE_MAHJONG_DYE);
    }

    public static boolean dyeSlotItem(ItemStack stack) {
        return mahjongDye(stack) || stack.getItem() instanceof DyeItem;
    }

    public static boolean boxAccepts(int slot, ItemStack stack) {
        if (slot < 0 || slot >= BOX_SLOTS || hasStorage(stack) || !MahjongComponents.valid(stack)) return false;
        return slot < TILE_SLOTS ? stack.is(MahjongContent.TILE_ITEM)
            : slot < DYE_SLOT ? stack.is(MahjongContent.POINT_STICK)
            : slot == DICE_SLOT ? stack.is(MahjongContent.DICE) : dyeSlotItem(stack);
    }

    /** Do not truncate oversized command-created containers when opening or crafting them. */
    public static boolean validBox(ItemStack box) {
        if (!box.is(MahjongContent.BOX_ITEM) || box.getCount() != 1 || !MahjongComponents.valid(box)) return false;
        var data = box.getTagElement("mchjong");
        if (data != null && data.contains("Items") && (!data.contains("Items", 9)
            || data.get("Items") instanceof net.minecraft.nbt.ListTag list && !list.isEmpty() && list.getElementType() != 10)) return false;
        var stored = data == null ? new net.minecraft.nbt.ListTag() : data.getList("Items", 10);
        if (MahjongComponents.boxPreset(box) != null) return stored.isEmpty();
        if (stored.size() > BOX_SLOTS) return false;
        var occupied = new java.util.HashSet<Integer>();
        for (var entry : stored) {
            var item = (CompoundTag) entry;
            if (!item.contains("Slot", 1) || !item.contains("id", 8) || !item.contains("Count", 1)
                || item.getByte("Count") <= 0 || ItemStack.of(item).isEmpty()) return false;
            int slot = item.getByte("Slot") & 255;
            if (slot >= BOX_SLOTS || !occupied.add(slot)) return false;
        }
        var items = contents(box);
        for (int i = 0; i < items.size(); i++)
            if (!items.get(i).isEmpty() && (!boxAccepts(i, items.get(i)) || items.get(i).getCount() > items.get(i).getMaxStackSize())) return false;
        return true;
    }

    public static boolean hasStorage(ItemStack stack) {
        var data = stack.getTagElement("mchjong");
        return data != null && data.contains("Items") || stack.hasTag()
            && (stack.getTag().contains("Items") || stack.getTag().contains("BlockEntityTag"));
    }

    public static int tileCount(List<ItemStack> items) {
        return items.stream().filter(stack -> stack.is(MahjongContent.TILE_ITEM)).mapToInt(ItemStack::getCount).sum();
    }

    public static ItemStack engrave(ItemStack box, TileFacePreset preset) {
        if (!validBox(box)) return ItemStack.EMPTY;
        var output = engravedContents(contents(box), preset);
        if (output.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = box.copyWithCount(1);
        setContents(result, output);
        return result;
    }

    /** Preview a complete transaction; callers own committing it and consuming the reagent. */
    public static List<ItemStack> engravedContents(List<ItemStack> input, TileFacePreset preset) {
        if (input.size() != BOX_SLOTS) return List.of();
        int total = tileCount(input);
        if (total != SET_SIZE && total != SET_SIZE + TileData.FLOWER_COUNT) return List.of();
        var tiles = input.subList(0, TILE_SLOTS).stream().filter(stack -> !stack.isEmpty()).toList();
        if (tiles.isEmpty()) return List.of();
        for (int i = 0; i < input.size(); i++)
            if (!input.get(i).isEmpty() && (!boxAccepts(i, input.get(i)) || input.get(i).getCount() > input.get(i).getMaxStackSize())) return List.of();
        ItemStack template = tiles.get(0);
        boolean blanks = tile(template).blank();
        if (tiles.stream().anyMatch(stack -> !tile(stack).valid() || tile(stack).blank() != blanks)) return List.of();
        var output = new ArrayList<>(input.stream().map(ItemStack::copy).toList());
        if (blanks) {
            if (tiles.stream().anyMatch(stack -> !ItemStack.isSameItemSameTags(template, stack))) return List.of();
            for (int i = 0; i < TILE_SLOTS; i++) output.set(i, ItemStack.EMPTY);
            int slot = 0;
            for (int face = 0; face < 34; face++) {
                output.set(slot++, printed(template, face, false, 4, preset));
            }
            if (total == SET_SIZE + TileData.FLOWER_COUNT)
                for (int flower = 0; flower < TileData.FLOWER_COUNT; flower++)
                    output.set(slot++, printed(template, TileData.FIRST_FLOWER + flower, false, 1, preset));
        } else {
            int[] faces = new int[34];
            int[] flowers = new int[TileData.FLOWER_COUNT];
            for (var stack : tiles) {
                if (tile(stack).material() != tile(template).material() || back(stack) != back(template)) return List.of();
                var data = tile(stack);
                if (data.flower()) flowers[data.face() - TileData.FIRST_FLOWER] += stack.getCount();
                else faces[data.face()] += stack.getCount();
            }
            for (int count : faces) if (count != 4) return List.of();
            for (int count : flowers) if (count != (total == SET_SIZE ? 0 : 1)) return List.of();
            if (tiles.stream().allMatch(stack -> facePreset(stack).equals(preset))) return List.of();
            for (int i = 0; i < TILE_SLOTS; i++)
                if (!output.get(i).isEmpty()) MahjongComponents.facePreset(output.get(i), preset);
        }
        return List.copyOf(output);
    }

    public static TileFacePreset facePreset(ItemStack stack) {
        return MahjongComponents.facePreset(stack);
    }

    private static ItemStack printed(ItemStack template, int face, boolean red, int count, TileFacePreset preset) {
        var result = template.copyWithCount(count);
        MahjongComponents.tile(result, tile(template).engraved(face, red));
        MahjongComponents.facePreset(result, preset);
        return result;
    }

    public static ItemStack dye(ItemStack input, DyeColor color) {
        ItemStack result = input.copyWithCount(1);
        if (input.is(MahjongContent.BOX_ITEM)) {
            if (!validBox(input)) return ItemStack.EMPTY;
            var items = dyedContents(contents(input), color);
            if (items.isEmpty()) return ItemStack.EMPTY;
            setContents(result, items);
        } else MahjongComponents.color(result, color);
        return result;
    }

    public static List<ItemStack> dyedContents(List<ItemStack> input, DyeColor color) {
        if (input.size() != BOX_SLOTS || tileCount(input) == 0) return List.of();
        var output = new ArrayList<>(input.stream().map(ItemStack::copy).toList());
        for (int i = 0; i < output.size(); i++) {
            var stack = output.get(i);
            if (!stack.isEmpty() && (!boxAccepts(i, stack) || stack.getCount() > stack.getMaxStackSize())) return List.of();
            if (stack.is(MahjongContent.TILE_ITEM)) MahjongComponents.color(stack, color);
        }
        return List.copyOf(output);
    }

    /** Inventory summaries prefer a four-player set, then a usable three-player subset. */
    public static Deck deck(ItemStack box) {
        if (!validBox(box)) return null;
        return deck(contents(box));
    }

    public static Deck deck(List<ItemStack> items) {
        for (boolean sanma : new boolean[]{false, true}) for (var reds : RedFives.values()) {
            var deck = selectDeck(items, sanma, reds);
            if (deck != null) return deck;
        }
        return null;
    }

    public static Deck deck(ItemStack box, boolean sanma, RedFives reds) {
        return validBox(box) ? selectDeck(contents(box), sanma, reds) : null;
    }

    public static boolean canSupplyReds(ItemStack box, boolean sanma, RedFives reds) {
        return deck(box, sanma, reds) != null;
    }

    /** Select a uniform subset without consuming, recoloring, or combining physical boxes. */
    private static Deck selectDeck(List<ItemStack> items, boolean sanma, RedFives reds) {
        var stocks = new java.util.LinkedHashMap<Deck, int[]>();
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            if (dyeSlotItem(stack)) continue;
            if (!storable(stack)) return null;
            if (stack.is(MahjongContent.POINT_STICK)) continue;
            TileData data = tile(stack);
            if (!data.valid()) return null;
            if (data.blank() || data.flower()) continue;
            var appearance = new Deck(data.material(), back(stack), facePreset(stack), reds, sanma);
            stocks.computeIfAbsent(appearance, ignored -> new int[68])[data.face() * 2 + (data.red() ? 1 : 0)] += stack.getCount();
        }
        for (var stock : stocks.entrySet()) {
            boolean enough = true;
            for (int face = 0; face < 34 && enough; face++) {
                if (sanma && face > 0 && face < 8) continue;
                int red = face < 27 && face % 9 == 4 ? reds.count(face / 9) : 0;
                enough = stock.getValue()[face * 2 + 1] >= red
                    && stock.getValue()[face * 2] >= 4 - red;
            }
            if (enough) return stock.getKey();
        }
        return null;
    }

    public record Deck(TileMaterial material, DyeColor back, TileFacePreset preset, RedFives redFives, boolean sanma) {
        public List<Integer> tiles() { return List.copyOf(Tile.set(sanma, redFives)); }
    }

    /** Compact creative/browser stock. Contents are expanded only when gameplay needs the box inventory. */
    public static ItemStack stockedBox(RedFives reds) {
        var box = new ItemStack(MahjongContent.BOX_ITEM);
        MahjongComponents.boxPreset(box, reds);
        return box;
    }

    private static NonNullList<ItemStack> stockedContents(RedFives reds) {
        var items = NonNullList.withSize(BOX_SLOTS, ItemStack.EMPTY);
        int slot = 0;
        for (int face = 0; face < 34; face++) {
            int red = face < 27 && face % 9 == 4 ? reds.count(face / 9) : 0;
            if (red < 4) items.set(slot++, tile(new TileData(face, TileMaterial.BONE, false), 4 - red));
            if (red > 0) items.set(slot++, tile(new TileData(face, TileMaterial.BONE, true), red));
        }
        for (int flower = 0; flower < TileData.FLOWER_COUNT; flower++)
            items.set(slot++, tile(new TileData(TileData.FIRST_FLOWER + flower, TileMaterial.BONE, false), 1));
        if (slot > TILE_SLOTS) throw new IllegalStateException("Stocked tile set exceeds mahjong box capacity");
        int stickSlot = TILE_SLOTS;
        for (int[] supply : new int[][]{{100, 40}, {1000, 16}, {5000, 8}, {10000, 4}, {-10000, 4}}) {
            var stick = new ItemStack(MahjongContent.POINT_STICK, supply[1]);
            MahjongComponents.points(stick, supply[0]);
            items.set(stickSlot++, stick);
        }
        items.set(DICE_SLOT, new ItemStack(MahjongContent.DICE, 2));
        return items;
    }

    /** Creative/test fixture assembled through the same physical blank engraving path. */
    public static ItemStack completeBox(TileMaterial material) { return completeBox(material, null); }

    public static ItemStack completeBox(TileMaterial material, DyeColor color) {
        ItemStack box = new ItemStack(MahjongContent.BOX_ITEM);
        setContents(box, List.of(
            tile(new TileData(-1, material, false), color, 64),
            tile(new TileData(-1, material, false), color, 64),
            tile(new TileData(-1, material, false), color, 8)));
        var result = engrave(box, TileFacePreset.KANSAI);
        var items = contents(result);
        items.set(DICE_SLOT, new ItemStack(MahjongContent.DICE, 2));
        setContents(result, items);
        return result;
    }
}
