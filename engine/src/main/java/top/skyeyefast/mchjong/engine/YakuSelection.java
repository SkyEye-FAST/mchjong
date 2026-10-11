package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Frozen definitions and individual switches for one table. Native awards default to enabled. */
public record YakuSelection(MahjongVariant variant, List<YakuSet> sets,
                            Set<String> disabledNative, Set<String> enabledCustom) {
    public static final int MAX_SETS = 32;
    public static final int MAX_AWARDS = MAX_SETS * YakuSet.MAX_AWARDS + 64;
    public static final int MAX_DEFINITION_CHARS = 131_072;
    public static final int MAX_DEFINITION_BYTES = 196_608;
    private static final Set<String> TAIWAN_NATIVE_IDS = java.util.Arrays.stream(TaiwanRules.Pattern.values())
        .map(p -> "mchjong:taiwan/" + p.name().toLowerCase(java.util.Locale.ROOT))
        .collect(java.util.stream.Collectors.toUnmodifiableSet());

    public YakuSelection {
        if (variant != MahjongVariant.RIICHI && variant != MahjongVariant.TAIWAN)
            throw new IllegalArgumentException("Unsupported award selection");
        sets = sets.stream().sorted(java.util.Comparator.comparing(YakuSet::id)).toList();
        disabledNative = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(disabledNative));
        enabledCustom = java.util.Collections.unmodifiableSet(new java.util.TreeSet<>(enabledCustom));
        if (sets.size() > MAX_SETS || !nativeIds(variant).containsAll(disabledNative))
            throw new IllegalArgumentException("Invalid native award selection");
        var setIds = new HashSet<String>();
        var awardIds = new HashSet<String>();
        int chars = 0, bytes = 0;
        var enabled = enabledCustom;
        for (var set : sets) {
            if (set.variant() != variant || !setIds.add(set.id()))
                throw new IllegalArgumentException("Invalid selected award set");
            for (var award : set.awards()) if (!awardIds.add(award.id()))
                throw new IllegalArgumentException("Duplicate custom award ID");
            if (set.awards().stream().noneMatch(a -> enabled.contains(a.id())))
                throw new IllegalArgumentException("Selected set has no enabled awards");
            String json = YakuSetCodec.encode(set);
            chars += json.length(); bytes += json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
        if (chars > MAX_DEFINITION_CHARS || bytes > MAX_DEFINITION_BYTES || !awardIds.containsAll(enabledCustom))
            throw new IllegalArgumentException("Invalid custom award selection");
    }

    public static YakuSelection defaults(MahjongVariant variant) {
        return new YakuSelection(variant, List.of(), Set.of(), Set.of());
    }
    public static YakuSelection of(YakuSet set) {
        return new YakuSelection(set.variant(), List.of(set), Set.of(),
            set.awards().stream().map(YakuSet.Definition::id).collect(java.util.stream.Collectors.toSet()));
    }
    public boolean isDefault() { return sets.isEmpty() && disabledNative.isEmpty(); }
    public boolean enabled(String id) { return nativeIds(variant).contains(id) ? !disabledNative.contains(id) : enabledCustom.contains(id); }
    public YakuSet.Definition find(String id) {
        if (!enabledCustom.contains(id)) return null;
        return sets.stream().map(s -> s.find(id)).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
    public List<YakuSet.Definition> matching(YakuFacts facts) {
        return sets.stream().flatMap(s -> s.matching(facts).stream()).filter(a -> enabledCustom.contains(a.id())).toList();
    }
    public YakuSelection withNative(String id, boolean enabled) {
        if (!nativeIds(variant).contains(id)) throw new IllegalArgumentException("Unknown native award");
        var disabled = new HashSet<>(disabledNative);
        if (enabled) disabled.remove(id); else disabled.add(id);
        return new YakuSelection(variant, sets, disabled, enabledCustom);
    }
    public YakuSelection withCustom(YakuSet set, String id, boolean enabled) {
        if (set.find(id) == null) throw new IllegalArgumentException("Unknown custom award");
        var selected = new HashSet<>(enabledCustom);
        if (enabled) selected.add(id); else selected.remove(id);
        var definitions = new ArrayList<>(sets);
        definitions.removeIf(s -> s.id().equals(set.id()));
        if (set.awards().stream().anyMatch(a -> selected.contains(a.id()))) definitions.add(set);
        return new YakuSelection(variant, definitions, disabledNative, selected);
    }
    public YakuSelection withSet(YakuSet set, boolean enabled) {
        var selected = new HashSet<>(enabledCustom);
        for (var award : set.awards()) { if (enabled) selected.add(award.id()); else selected.remove(award.id()); }
        var definitions = new ArrayList<>(sets);
        definitions.removeIf(s -> s.id().equals(set.id()));
        if (enabled) definitions.add(set);
        return new YakuSelection(variant, definitions, disabledNative, selected);
    }
    public static Set<String> nativeIds(MahjongVariant variant) {
        if (variant == MahjongVariant.RIICHI) return YakuCatalog.nativeIds();
        if (variant != MahjongVariant.TAIWAN) throw new IllegalArgumentException("Unsupported award selection");
        return TAIWAN_NATIVE_IDS;
    }
}
