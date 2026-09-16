package top.skyeyefast.mchjong.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Pure stack transformations: callers commit the returned copy, never mutate recipe inputs. */
public final class MahjongSupplies {
    public static final int BOX_SLOTS = 54;
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

    /** Do not truncate oversized command-created containers when opening or crafting them. */
    public static boolean validBox(ItemStack box) {
        if (!box.is(MahjongContent.BOX_ITEM) || box.getCount() != 1) return false;
        var stored = box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        return stored.stream().limit(BOX_SLOTS + 1L).count() <= BOX_SLOTS
            && stored.stream().allMatch(stack -> stack.isEmpty() || storable(stack));
    }

    public static int tileCount(List<ItemStack> items) {
        return items.stream().filter(stack -> stack.is(MahjongContent.TILE_ITEM)).mapToInt(ItemStack::getCount).sum();
    }

    public static ItemStack engrave(ItemStack box) {
        if (!validBox(box)) return ItemStack.EMPTY;
        var input = contents(box);
        TileData blank = null;
        DyeColor color = null;
        List<ItemStack> output = new ArrayList<>();
        int total = 0;
        for (ItemStack stack : input) {
            if (stack.isEmpty()) continue;
            if (!storable(stack)) return ItemStack.EMPTY;
            if (stack.is(MahjongContent.POINT_STICK)) { output.add(stack.copy()); continue; }
            TileData data = tile(stack);
            if (!data.valid() || !data.blank()) return ItemStack.EMPTY;
            if (blank == null) { blank = data; color = color(stack); }
            if (!data.equals(blank) || color != color(stack)) return ItemStack.EMPTY;
            total += stack.getCount();
        }
        if (total < SET_SIZE) return ItemStack.EMPTY;
        int consumed = SET_SIZE;
        for (ItemStack stack : input) if (stack.is(MahjongContent.TILE_ITEM)) {
            int take = Math.min(consumed, stack.getCount());
            consumed -= take;
            if (take < stack.getCount()) output.add(stack.copyWithCount(stack.getCount() - take));
        }
        if (output.size() + 37 > BOX_SLOTS) return ItemStack.EMPTY;
        for (int face = 0; face < 34; face++) {
            boolean five = face == 4 || face == 13 || face == 22;
            output.add(tile(blank.engraved(face, false), color, five ? 3 : 4));
            if (five) output.add(tile(blank.engraved(face, true), color, 1));
        }
        ItemStack result = box.copyWithCount(1);
        result.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(output));
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
                if (!storable(stack)) return ItemStack.EMPTY;
                if (stack.is(MahjongContent.TILE_ITEM)) stack.set(DataComponents.BASE_COLOR, color);
            }
            result.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        } else result.set(DataComponents.BASE_COLOR, color);
        return result;
    }

    /** A set is one uniform, unmarked set with exactly three red fives. No tile order is exposed. */
    public static Deck deck(ItemStack box) {
        if (!validBox(box)) return null;
        return deck(contents(box));
    }

    public static Deck deck(List<ItemStack> items) {
        int[] normal = new int[34];
        int[] red = new int[34];
        TileMaterial material = null;
        DyeColor back = null;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            if (!storable(stack)) return null;
            if (stack.is(MahjongContent.POINT_STICK)) continue;
            TileData data = tile(stack);
            if (!data.valid()) return null;
            if (data.blank() || data.flower()) continue;
            if (material == null) { material = data.material(); back = color(stack); }
            if (data.material() != material || color(stack) != back) return null;
            (data.red() ? red : normal)[data.face()] += stack.getCount();
        }
        for (int face = 0; face < 34; face++) {
            boolean five = face == 4 || face == 13 || face == 22;
            if (normal[face] != (five ? 3 : 4) || red[face] != (five ? 1 : 0)) return null;
        }
        return new Deck(material, back);
    }

    public record Deck(TileMaterial material, DyeColor back) {
        public List<Integer> tiles(boolean sanma) { return List.copyOf(Tile.set(sanma)); }
    }

    /** Creative/test fixture assembled through the same physical blank engraving path. */
    public static ItemStack completeBox(TileMaterial material, DyeColor color) {
        ItemStack box = new ItemStack(MahjongContent.BOX_ITEM);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(
            tile(new TileData(-1, material, false), color, 64),
            tile(new TileData(-1, material, false), color, 64),
            tile(new TileData(-1, material, false), color, 8))));
        return engrave(box);
    }
}
