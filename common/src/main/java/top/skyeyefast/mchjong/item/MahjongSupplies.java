package top.skyeyefast.mchjong.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.engine.RedFives;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Pure stack transformations: callers commit the returned copy, never mutate recipe inputs. */
public final class MahjongSupplies {
    public static final int BOX_SLOTS = 55;
    public static final int TILE_SLOTS = 45;
    public static final int DYE_SLOT = BOX_SLOTS - 1;
    public static final int SET_SIZE = 136;
    private MahjongSupplies() {}

    public static DyeColor color(ItemStack stack) { return stack.getOrDefault(DataComponents.BASE_COLOR, DyeColor.BLUE); }
    public static TileData tile(ItemStack stack) { return stack.getOrDefault(MahjongComponents.TILE, TileData.BLANK); }
    public static ItemStack tile(TileData data, DyeColor color, int count) {
        if (!data.valid()) throw new IllegalArgumentException("Invalid tile data");
        ItemStack stack = new ItemStack(MahjongContent.TILE_ITEM, count);
        stack.set(MahjongComponents.TILE, data);
        stack.set(DataComponents.BASE_COLOR, color);
        return stack;
    }

    public static NonNullList<ItemStack> contents(ItemStack box) {
        NonNullList<ItemStack> result = NonNullList.withSize(BOX_SLOTS, ItemStack.EMPTY);
        box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(result);
        return result;
    }

    public static boolean storable(ItemStack stack) {
        return (stack.is(MahjongContent.TILE_ITEM) || stack.is(MahjongContent.POINT_STICK))
            && !stack.has(DataComponents.CONTAINER) && !stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    public static boolean mahjongDye(ItemStack stack) {
        return stack.is(MahjongContent.MAHJONG_DYE) || stack.is(MahjongContent.CREATIVE_MAHJONG_DYE);
    }

    public static boolean boxAccepts(int slot, ItemStack stack) {
        if (slot < 0 || slot >= BOX_SLOTS || stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)) return false;
        return slot < TILE_SLOTS ? stack.is(MahjongContent.TILE_ITEM)
            : slot < DYE_SLOT ? stack.is(MahjongContent.POINT_STICK) : mahjongDye(stack);
    }

    /** Do not truncate oversized command-created containers when opening or crafting them. */
    public static boolean validBox(ItemStack box) {
        if (!box.is(MahjongContent.BOX_ITEM) || box.getCount() != 1) return false;
        var stored = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (stored.stream().limit(BOX_SLOTS + 1L).count() > BOX_SLOTS) return false;
        var items = contents(box);
        for (int i = 0; i < items.size(); i++)
            if (!items.get(i).isEmpty() && (!boxAccepts(i, items.get(i)) || items.get(i).getCount() > items.get(i).getMaxStackSize())) return false;
        return true;
    }

    public static int tileCount(List<ItemStack> items) {
        return items.stream().filter(stack -> stack.is(MahjongContent.TILE_ITEM)).mapToInt(ItemStack::getCount).sum();
    }

    public static ItemStack engrave(ItemStack box, TileFacePreset preset) {
        if (!validBox(box)) return ItemStack.EMPTY;
        var output = engravedContents(contents(box), preset);
        if (output.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = box.copyWithCount(1);
        result.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(output));
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
            if (tiles.stream().allMatch(stack -> facePreset(stack) == preset)) return List.of();
            for (int i = 0; i < TILE_SLOTS; i++)
                if (!output.get(i).isEmpty()) output.get(i).set(MahjongComponents.FACE_PRESET, preset);
            if (deck(output) == null) return List.of();
            int[] flowers = new int[TileData.FLOWER_COUNT];
            for (var stack : tiles) {
                if (tile(stack).material() != tile(template).material() || color(stack) != color(template)) return List.of();
                if (tile(stack).flower()) flowers[tile(stack).face() - TileData.FIRST_FLOWER] += stack.getCount();
            }
            for (int count : flowers) if (count != (total == SET_SIZE ? 0 : 1)) return List.of();
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
            var items = contents(input);
            if (tileCount(items) == 0) return ItemStack.EMPTY;
            for (ItemStack stack : items) {
                if (stack.isEmpty()) continue;
                if (stack.is(MahjongContent.TILE_ITEM)) stack.set(DataComponents.BASE_COLOR, color);
            }
            result.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        } else result.set(DataComponents.BASE_COLOR, color);
        return result;
    }

    /** A set is uniform and has no reds, one of each red five, or an additional red five of circles. */
    public static Deck deck(ItemStack box) {
        if (!validBox(box)) return null;
        return deck(contents(box));
    }

    public static Deck deck(List<ItemStack> items) {
        int[] normal = new int[34];
        int[] red = new int[34];
        TileMaterial material = null;
        DyeColor back = null;
        TileFacePreset preset = null;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            if (mahjongDye(stack)) continue;
            if (!storable(stack)) return null;
            if (stack.is(MahjongContent.POINT_STICK)) continue;
            TileData data = tile(stack);
            if (!data.valid()) return null;
            if (data.blank() || data.flower()) continue;
            if (material == null) { material = data.material(); back = color(stack); preset = facePreset(stack); }
            if (data.material() != material || color(stack) != back || facePreset(stack) != preset) return null;
            (data.red() ? red : normal)[data.face()] += stack.getCount();
        }
        for (int face = 0; face < 34; face++) {
            if (normal[face] + red[face] != 4) return null;
        }
        RedFives redFives = RedFives.of(red[4], red[13], red[22]);
        return preset != null && redFives != null ? new Deck(material, back, preset, redFives) : null;
    }

    public record Deck(TileMaterial material, DyeColor back, TileFacePreset preset, RedFives redFives) {
        public List<Integer> tiles(boolean sanma) { return List.copyOf(Tile.set(sanma, redFives)); }
    }

    /** Creative/test fixture assembled through the same physical blank engraving path. */
    public static ItemStack completeBox(TileMaterial material, DyeColor color) {
        ItemStack box = new ItemStack(MahjongContent.BOX_ITEM);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
            tile(new TileData(-1, material, false), color, 64),
            tile(new TileData(-1, material, false), color, 64),
            tile(new TileData(-1, material, false), color, 8))));
        return engrave(box, TileFacePreset.KANSAI);
    }
}
