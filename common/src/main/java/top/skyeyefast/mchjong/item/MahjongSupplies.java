package top.skyeyefast.mchjong.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
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

    public static DyeColor color(ItemStack stack) { return stack.getOrDefault(DataComponents.BASE_COLOR, DyeColor.BLUE); }
    public static DyeColor back(ItemStack stack) { return stack.get(DataComponents.BASE_COLOR); }
    public static net.minecraft.resources.ResourceLocation backPreset(ItemStack stack) {
        return stack.getOrDefault(MahjongComponents.BACK_PRESET,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mchjong", "default"));
    }
    public static TileData tile(ItemStack stack) { return stack.getOrDefault(MahjongComponents.TILE, TileData.BLANK); }
    public static ItemStack tile(TileData data, int count) { return tile(data, null, count); }
    public static ItemStack tile(TileData data, DyeColor color, int count) {
        if (!data.valid()) throw new IllegalArgumentException("Invalid tile data");
        ItemStack stack = new ItemStack(MahjongContent.TILE_ITEM, count);
        stack.set(MahjongComponents.TILE, data);
        if (color != null) stack.set(DataComponents.BASE_COLOR, color);
        return stack;
    }

    public static NonNullList<ItemStack> contents(ItemStack box) {
        var preset = box.get(MahjongComponents.BOX_PRESET);
        var stored = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (preset != null && stored.nonEmptyStream().findAny().isEmpty()) return stockedContents(preset);
        NonNullList<ItemStack> result = NonNullList.withSize(BOX_SLOTS, ItemStack.EMPTY);
        stored.copyInto(result);
        return result;
    }

    public static void setContents(ItemStack box, List<ItemStack> items) {
        box.remove(MahjongComponents.BOX_PRESET);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    public static boolean storable(ItemStack stack) {
        if (stack.isEmpty() || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)) return false;
        return stack.is(MahjongContent.TILE_ITEM) ? tile(stack).valid()
            : stack.is(MahjongContent.POINT_STICK) ? validPoints(stack.getOrDefault(MahjongComponents.POINTS, 0))
            : stack.is(MahjongContent.DICE);
    }

    public static boolean mahjongDye(ItemStack stack) {
        return stack.is(MahjongContent.MAHJONG_DYE) || stack.is(MahjongContent.CREATIVE_MAHJONG_DYE);
    }

    public static boolean dyeSlotItem(ItemStack stack) {
        return mahjongDye(stack) || stack.getItem() instanceof DyeItem || stack.is(MahjongContent.UNDO_DYE);
    }

    public static boolean boxAccepts(int slot, ItemStack stack) {
        if (slot < 0 || slot >= BOX_SLOTS || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)) return false;
        return slot < TILE_SLOTS ? stack.is(MahjongContent.TILE_ITEM) && storable(stack)
            : slot < DYE_SLOT ? stack.is(MahjongContent.POINT_STICK) && storable(stack)
            : slot == DICE_SLOT ? stack.is(MahjongContent.DICE) && storable(stack) : dyeSlotItem(stack);
    }

    /** Do not truncate oversized command-created containers when opening or crafting them. */
    public static boolean validBox(ItemStack box) {
        if (!box.is(MahjongContent.BOX_ITEM) || box.getCount() != 1 || box.has(DataComponents.BUNDLE_CONTENTS)) return false;
        var stored = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (box.has(MahjongComponents.BOX_PRESET)) return stored.nonEmptyStream().findAny().isEmpty();
        if (stored.stream().limit(BOX_SLOTS + 1L).count() > BOX_SLOTS) return false;
        var items = contents(box);
        for (int i = 0; i < items.size(); i++)
            if (!items.get(i).isEmpty() && (!boxAccepts(i, items.get(i)) || items.get(i).getCount() > items.get(i).getMaxStackSize())) return false;
        return true;
    }

    public static int tileCount(List<ItemStack> items) {
        return items.stream().filter(stack -> stack.is(MahjongContent.TILE_ITEM)).mapToInt(ItemStack::getCount).sum();
    }

    public static Map<Item, Integer> markings() {
        return Map.of(Items.WHITE_DYE, 100, Items.BLUE_DYE, 1000,
            Items.YELLOW_DYE, 5000, Items.RED_DYE, 10000, Items.BLACK_DYE, -10000);
    }

    private static boolean validPoints(int points) {
        return points == 0 || points == 100 || points == 1000 || points == 5000 || points == 10000 || points == -10000;
    }

    /** One physical five per application, even when the source is a larger stack. */
    public static ItemStack redFive(ItemStack target, ItemStack reagent) {
        boolean red = reagent.is(MahjongContent.RED_DORA_DYE);
        if (!red && !reagent.is(MahjongContent.UNDO_DYE)) return ItemStack.EMPTY;
        var data = tile(target);
        if (!target.is(MahjongContent.TILE_ITEM) || !storable(target) || data.red() == red
            || (data.face() != 4 && data.face() != 13 && data.face() != 22)) return ItemStack.EMPTY;
        var result = target.copyWithCount(1);
        result.set(MahjongComponents.TILE, data.engraved(data.face(), red));
        return result;
    }

    /** Mark a homogeneous group without discarding its name or other components. */
    public static ItemStack markSticks(List<ItemStack> blanks, ItemStack reagent, int count) {
        int points = markings().getOrDefault(reagent.getItem(), 0);
        if (blanks.isEmpty() || points == 0 || count <= 0) return ItemStack.EMPTY;
        var template = blanks.getFirst();
        if (!template.is(MahjongContent.POINT_STICK) || !storable(template)
            || template.getOrDefault(MahjongComponents.POINTS, 0) != 0 || count > template.getMaxStackSize()) return ItemStack.EMPTY;
        int total = 0;
        for (var stack : blanks) {
            if (!ItemStack.isSameItemSameComponents(template, stack) || stack.getCount() > stack.getMaxStackSize()) return ItemStack.EMPTY;
            total += stack.getCount();
        }
        if (total != count) return ItemStack.EMPTY;
        var result = template.copyWithCount(count);
        result.set(MahjongComponents.POINTS, points);
        return result;
    }

    /** All-or-nothing insertion, using the same slot and component contract as the box menu. */
    public static ItemStack pack(ItemStack box, List<ItemStack> incoming) {
        if (!validBox(box) || incoming.isEmpty()) return ItemStack.EMPTY;
        var stored = contents(box);
        boolean changed = false;
        for (var source : incoming) {
            if (source.isEmpty()) continue;
            if (!storable(source) || source.getCount() > source.getMaxStackSize()) return ItemStack.EMPTY;
            var remainder = source.copy();
            // Merge first, then occupy empty slots; variants never overwrite one another.
            for (int pass = 0; pass < 2; pass++) for (int slot = 0; slot < BOX_SLOTS && !remainder.isEmpty(); slot++) {
                if (!boxAccepts(slot, remainder)) continue;
                var current = stored.get(slot);
                if (pass == 0 ? current.isEmpty() || !ItemStack.isSameItemSameComponents(current, remainder) : !current.isEmpty()) continue;
                int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize() - current.getCount());
                if (moved <= 0) continue;
                if (current.isEmpty()) stored.set(slot, remainder.copyWithCount(moved));
                else current.grow(moved);
                remainder.shrink(moved);
                changed = true;
            }
            if (!remainder.isEmpty()) return ItemStack.EMPTY;
        }
        if (!changed) return ItemStack.EMPTY;
        var result = box.copyWithCount(1);
        setContents(result, stored);
        return result;
    }

    /** The industrial full-deck transaction uses the ordinary engraving implementation. */
    public static ItemStack printBox(ItemStack box, List<ItemStack> blanks) {
        if (!validBox(box)
            || tileCount(contents(box)) + tileCount(blanks) != SET_SIZE + TileData.FLOWER_COUNT
            || blanks.stream().anyMatch(stack -> !stack.is(MahjongContent.TILE_ITEM) || !tile(stack).blank())) return ItemStack.EMPTY;
        if (contents(box).stream().anyMatch(stack -> stack.is(MahjongContent.TILE_ITEM) && !tile(stack).blank())) return ItemStack.EMPTY;
        var packed = blanks.isEmpty() ? box.copy() : pack(box, blanks);
        return packed.isEmpty() ? ItemStack.EMPTY : engrave(packed, TileFacePreset.KANSAI);
    }

    /** A target is a whole tile stack or one box. Every target must actually change. */
    public static List<ItemStack> dyeBatch(List<ItemStack> targets, DyeColor color) {
        if (targets.size() != 2) return List.of();
        var output = new ArrayList<ItemStack>();
        for (var target : targets) {
            if (!(target.is(MahjongContent.BOX_ITEM) && validBox(target))
                && !(target.is(MahjongContent.TILE_ITEM) && storable(target) && target.getCount() <= target.getMaxStackSize())) return List.of();
            // Expanding a compact box preset is not itself a change to any tile's back.
            boolean changesBack = target.is(MahjongContent.BOX_ITEM)
                ? contents(target).stream().anyMatch(stack -> stack.is(MahjongContent.TILE_ITEM) && back(stack) != color)
                : back(target) != color;
            if (!changesBack) return List.of();
            var result = dye(target, color);
            if (result.isEmpty() || ItemStack.isSameItemSameComponents(target, result)) return List.of();
            output.add(result.copyWithCount(target.getCount()));
        }
        return List.copyOf(output);
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
        ItemStack template = tiles.getFirst();
        boolean blanks = tile(template).blank();
        if (tiles.stream().anyMatch(stack -> !tile(stack).valid() || tile(stack).blank() != blanks)) return List.of();
        var output = new ArrayList<>(input.stream().map(ItemStack::copy).toList());
        if (blanks) {
            if (tiles.stream().anyMatch(stack -> !ItemStack.isSameItemSameComponents(template, stack))) return List.of();
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
                if (tile(stack).material() != tile(template).material() || back(stack) != back(template)
                    || !backPreset(stack).equals(backPreset(template))) return List.of();
                var data = tile(stack);
                if (data.flower()) flowers[data.face() - TileData.FIRST_FLOWER] += stack.getCount();
                else faces[data.face()] += stack.getCount();
            }
            for (int count : faces) if (count != 4) return List.of();
            for (int count : flowers) if (count != (total == SET_SIZE ? 0 : 1)) return List.of();
            if (tiles.stream().allMatch(stack -> facePreset(stack).equals(preset))) return List.of();
            for (int i = 0; i < TILE_SLOTS; i++)
                if (!output.get(i).isEmpty()) output.get(i).set(MahjongComponents.FACE_PRESET, preset);
        }
        return List.copyOf(output);
    }

    public static TileFacePreset facePreset(ItemStack stack) {
        return stack.getOrDefault(MahjongComponents.FACE_PRESET, TileFacePreset.KANSAI);
    }

    private static ItemStack printed(ItemStack template, int face, boolean red, int count, TileFacePreset preset) {
        var result = template.copyWithCount(count);
        result.set(MahjongComponents.TILE, tile(template).engraved(face, red));
        result.set(MahjongComponents.FACE_PRESET, preset);
        return result;
    }

    public static ItemStack dye(ItemStack input, DyeColor color) {
        ItemStack result = input.copyWithCount(1);
        if (input.is(MahjongContent.BOX_ITEM)) {
            if (!validBox(input)) return ItemStack.EMPTY;
            var items = dyedContents(contents(input), color);
            if (items.isEmpty()) return ItemStack.EMPTY;
            setContents(result, items);
        } else {
            if (color != null) result.set(DataComponents.BASE_COLOR, color);
            else result.remove(DataComponents.BASE_COLOR);
        }
        return result;
    }

    public static List<ItemStack> dyedContents(List<ItemStack> input, DyeColor color) {
        if (input.size() != BOX_SLOTS || tileCount(input) == 0) return List.of();
        var output = new ArrayList<>(input.stream().map(ItemStack::copy).toList());
        for (int i = 0; i < output.size(); i++) {
            var stack = output.get(i);
            if (!stack.isEmpty() && (!boxAccepts(i, stack) || stack.getCount() > stack.getMaxStackSize())) return List.of();
            if (stack.is(MahjongContent.TILE_ITEM)) {
                if (color != null) stack.set(DataComponents.BASE_COLOR, color);
                else stack.remove(DataComponents.BASE_COLOR);
            }
        }
        return List.copyOf(output);
    }

    /** Change only the decorative rear pattern; dye color and tile identity stay intact. */
    public static List<ItemStack> backedContents(List<ItemStack> input, net.minecraft.resources.ResourceLocation preset) {
        if (input.size() != BOX_SLOTS || tileCount(input) == 0 || preset.toString().length() > 128) return List.of();
        var output = new ArrayList<>(input.stream().map(ItemStack::copy).toList());
        boolean changed = false;
        for (int i = 0; i < TILE_SLOTS; i++) {
            var stack = output.get(i);
            if (stack.isEmpty()) continue;
            if (!boxAccepts(i, stack) || stack.getCount() > stack.getMaxStackSize()) return List.of();
            if (!backPreset(stack).equals(preset)) {
                if (preset.equals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mchjong", "default")))
                    stack.remove(MahjongComponents.BACK_PRESET);
                else stack.set(MahjongComponents.BACK_PRESET, preset);
                changed = true;
            }
        }
        return changed ? List.copyOf(output) : List.of();
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
            var appearance = new Deck(data.material(), back(stack), facePreset(stack), backPreset(stack), reds, sanma);
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

    public record Deck(TileMaterial material, DyeColor back, TileFacePreset preset,
                       net.minecraft.resources.ResourceLocation backPreset, RedFives redFives, boolean sanma) {
        public List<Integer> tiles() { return List.copyOf(Tile.set(sanma, redFives)); }
    }

    /** Compact creative/browser stock. Contents are expanded only when gameplay needs the box inventory. */
    public static ItemStack stockedBox(RedFives reds) {
        var box = new ItemStack(MahjongContent.BOX_ITEM);
        box.set(MahjongComponents.BOX_PRESET, reds);
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
            stick.set(MahjongComponents.POINTS, supply[0]);
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
