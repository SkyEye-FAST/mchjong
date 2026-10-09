package top.skyeyefast.mchjong.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import top.skyeyefast.mchjong.engine.PreparationProblem;
import top.skyeyefast.mchjong.engine.RedFives;
import top.skyeyefast.mchjong.world.MahjongContent;

/** Selection and diagnosis share one copy-count check, always within a single case. */
public final class DeckAdmission {
    private DeckAdmission() {}

    public record Appearance(TileMaterial material, DyeColor back, TileFacePreset face, ResourceLocation backPreset) {}
    public record Check(Appearance appearance, List<PreparationProblem> problems, int missing) {
        public Check { problems = List.copyOf(problems); }
    }

    public static int[] requirements(boolean sanma, RedFives reds, boolean suitedOnly, boolean flowers) {
        int[] required = new int[84];
        for (int face = 0; face < (suitedOnly ? 27 : 34); face++) {
            if (sanma && face > 0 && face < 8) continue;
            int red = face < 27 && face % 9 == 4 ? reds.count(face / 9) : 0;
            required[face * 2] = 4 - red;
            required[face * 2 + 1] = red;
        }
        if (flowers) for (int face = 34; face < 42; face++) required[face * 2] = 1;
        return required;
    }

    public static Check inspect(ItemStack box, int[] required) {
        if (box.isEmpty()) return new Check(null, List.of(PreparationProblem.CASE), total(required));
        if (!MahjongSupplies.validBox(box)) return new Check(null, List.of(PreparationProblem.INVALID_CASE), total(required));
        return inspect(MahjongSupplies.contents(box), required);
    }

    public static Check inspect(List<ItemStack> items, int[] required) {
        var stocks = new LinkedHashMap<Appearance, int[]>();
        for (var stack : items) {
            if (stack.isEmpty() || MahjongSupplies.dyeSlotItem(stack)) continue;
            if (!MahjongSupplies.storable(stack)) return new Check(null, List.of(PreparationProblem.INVALID_CASE), total(required));
            if (!stack.is(MahjongContent.TILE_ITEM)) continue;
            var tile = MahjongSupplies.tile(stack);
            if (tile.blank()) continue;
            int index = tile.face() * 2 + (tile.red() ? 1 : 0);
            if (required[index] == 0) continue;
            var appearance = new Appearance(tile.material(), MahjongSupplies.back(stack), MahjongSupplies.facePreset(stack), MahjongSupplies.backPreset(stack));
            stocks.computeIfAbsent(appearance, ignored -> new int[required.length])[index] += stack.getCount();
        }
        for (var stock : stocks.entrySet()) if (missing(stock.getValue(), required) == 0)
            return new Check(stock.getKey(), List.of(), 0);
        int[] combined = new int[required.length];
        for (var stock : stocks.values()) add(combined, stock);
        var problems = new ArrayList<PreparationProblem>();
        for (int index = 0; index < required.length; index++) if (combined[index] < required[index]) {
            var problem = index >= 68 ? PreparationProblem.FLOWERS : index % 2 == 1 ? PreparationProblem.RED_FIVES : PreparationProblem.TILES;
            if (!problems.contains(problem)) problems.add(problem);
        }
        if (!stocks.isEmpty()) {
            if (!completeGroup(stocks, Appearance::material, required)) problems.add(PreparationProblem.MATERIAL);
            if (!completeGroup(stocks, a -> java.util.Arrays.asList(a.back(), a.backPreset()), required)) problems.add(PreparationProblem.BACK);
            if (!completeGroup(stocks, Appearance::face, required)) problems.add(PreparationProblem.FACE);
        }
        // An incomplete single-style set is missing faces, not inconsistent in all three styles.
        if (missing(combined, required) > 0) problems.removeIf(p -> p == PreparationProblem.MATERIAL || p == PreparationProblem.BACK || p == PreparationProblem.FACE);
        if (problems.isEmpty()) problems.add(PreparationProblem.UNIFORM_SET);
        return new Check(null, problems, stocks.values().stream().mapToInt(stock -> missing(stock, required)).min().orElse(total(required)));
    }

    private static boolean completeGroup(LinkedHashMap<Appearance, int[]> stocks, Function<Appearance, ?> key, int[] required) {
        var groups = new LinkedHashMap<Object, int[]>();
        stocks.forEach((appearance, stock) -> add(groups.computeIfAbsent(key.apply(appearance), ignored -> new int[required.length]), stock));
        return groups.values().stream().anyMatch(stock -> missing(stock, required) == 0);
    }
    private static void add(int[] target, int[] source) { for (int i = 0; i < target.length; i++) target[i] += source[i]; }
    private static int total(int[] required) { return java.util.Arrays.stream(required).sum(); }
    private static int missing(int[] stock, int[] required) {
        int count = 0;
        for (int i = 0; i < required.length; i++) count += Math.max(0, required[i] - stock[i]);
        return count;
    }
}
