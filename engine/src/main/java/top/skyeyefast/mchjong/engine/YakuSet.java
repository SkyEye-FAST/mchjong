package top.skyeyefast.mchjong.engine;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Public, immutable house awards. Conditions read only facts about one winning interpretation. */
public record YakuSet(String id, MahjongVariant variant, Text name, List<Definition> awards) {
    public static final int MAX_AWARDS = 16;

    public YakuSet {
        requireId(id);
        if (variant != MahjongVariant.RIICHI && variant != MahjongVariant.TAIWAN)
            throw new IllegalArgumentException("Custom awards require Riichi or Taiwan");
        Objects.requireNonNull(name);
        awards = List.copyOf(awards);
        if (awards.size() > MAX_AWARDS) throw new IllegalArgumentException("Too many custom awards");
        if (awards.isEmpty() || id.startsWith("mchjong:"))
            throw new IllegalArgumentException("Reserved or empty award set ID");
        var byId = new HashMap<String, Definition>();
        for (var award : awards) {
            if (award.id().startsWith("mchjong:") || byId.put(award.id(), award) != null)
                throw new IllegalArgumentException("Reserved or duplicate award ID: " + award.id());
            if ((variant == MahjongVariant.TAIWAN) != (award.reward().type() == RewardType.TAI))
                throw new IllegalArgumentException("Award unit does not match variant");
            award.condition().validate(variant, 0, new int[1]);
        }
        for (var award : awards) for (var excluded : award.excludes()) {
            boolean nativeAllowed = nativeIds(variant).contains(excluded) || variant == MahjongVariant.RIICHI
                && award.reward().type() == RewardType.YAKUMAN && YakuCatalog.nativeIds().contains(excluded)
                && !Set.of(YakuCatalog.awardId("Nagashi"), YakuCatalog.awardId("Renhou")).contains(excluded);
            if (excluded.equals(award.id()) || !byId.containsKey(excluded) && !nativeAllowed)
                throw new IllegalArgumentException("Unknown or self-excluded award: " + excluded);
            if (award.reward().type() == RewardType.HAN && byId.containsKey(excluded)
                && byId.get(excluded).reward().type() == RewardType.YAKUMAN)
                throw new IllegalArgumentException("An ordinary award cannot suppress a yakuman");
        }
        for (var award : awards) visit(award.id(), byId, new HashSet<>(), new HashSet<>());
    }

    public Definition find(String awardId) { return awards.stream().filter(a -> a.id().equals(awardId)).findFirst().orElse(null); }

    /** Exclusions are simultaneous: suppressing a composite never resurrects its components. */
    public List<Definition> matching(YakuFacts facts) {
        return awards.stream().filter(a -> a.reward().value(facts.closed()) > 0 && a.condition().matches(facts)).toList();
    }

    public static Set<String> excluded(List<Definition> matched) {
        var result = new HashSet<String>();
        matched.forEach(a -> result.addAll(a.excludes()));
        return Set.copyOf(result);
    }

    private static void visit(String id, java.util.Map<String, Definition> awards, Set<String> path, Set<String> done) {
        if (done.contains(id) || !awards.containsKey(id)) return;
        if (!path.add(id)) throw new IllegalArgumentException("Cyclic custom award exclusions");
        for (var next : awards.get(id).excludes()) visit(next, awards, path, done);
        path.remove(id);
        done.add(id);
    }

    public static void requireId(String value) {
        if (value == null || value.length() > 128 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid award resource ID");
    }

    /** Ordinary custom awards can suppress these native awards; flower event switches belong to YakuSelection. */
    public static Set<String> nativeIds(MahjongVariant variant) {
        if (variant == MahjongVariant.RIICHI) return YakuCatalog.customExclusionIds();
        return java.util.Arrays.stream(TaiwanRules.Pattern.values())
            .filter(p -> p != TaiwanRules.Pattern.EIGHT_FLOWERS && p != TaiwanRules.Pattern.SEVEN_ROBS_ONE)
            .map(p -> "mchjong:taiwan/" + p.name().toLowerCase(java.util.Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public record Text(String en_us, String ja_jp, String zh_cn, String zh_tw) {
        public Text {
            for (var text : new String[]{en_us, ja_jp, zh_cn, zh_tw})
                if (text == null || text.isBlank() || text.length() > 512 || text.chars().anyMatch(c -> c < 32 && c != '\n'))
                    throw new IllegalArgumentException("Invalid localized award text");
        }
        public String get(String locale) {
            return switch (locale) { case "ja_jp" -> ja_jp; case "zh_cn" -> zh_cn; case "zh_tw" -> zh_tw; default -> en_us; };
        }
    }

    public enum RewardType { HAN, YAKUMAN, TAI }
    public record Reward(RewardType type, int closed, int open) {
        public Reward {
            Objects.requireNonNull(type);
            int maximum = switch (type) { case HAN -> 13; case YAKUMAN -> 2; case TAI -> 1000; };
            if (closed < 0 || open < 0 || closed > maximum || open > maximum || closed + open == 0
                || type == RewardType.TAI && closed != open)
                throw new IllegalArgumentException("Invalid custom award value");
        }
        public int value(boolean concealed) { return concealed ? closed : open; }
    }

    public record Definition(String id, Text name, Text description, YakuCondition condition, Reward reward, List<String> excludes) {
        public Definition {
            requireId(id);
            Objects.requireNonNull(name); Objects.requireNonNull(description);
            Objects.requireNonNull(condition); Objects.requireNonNull(reward);
            excludes = List.copyOf(excludes);
            if (excludes.size() > 64 || new HashSet<>(excludes).size() != excludes.size())
                throw new IllegalArgumentException("Invalid award exclusions");
            excludes.forEach(YakuSet::requireId);
        }
    }
}
