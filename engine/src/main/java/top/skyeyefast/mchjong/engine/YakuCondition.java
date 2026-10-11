package top.skyeyefast.mchjong.engine;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Bounded condition tree. Group predicates always apply to one interpretation, never merged decompositions. */
public record YakuCondition(Type type, List<YakuCondition> children, List<String> values, int min, int max) {
    public enum Type { ALL, ANY, NOT, SHAPE, CLOSED, FLAGS, CATEGORIES, TILES, GROUPS, PAIR, WAIT, REDS, FLOWERS, SAME_SEQUENCES, THREE_SUIT_SEQUENCES }
    private static final Set<String> COMMON_FLAGS = Set.of("self_draw", "discard", "robbing_kong", "last_draw", "last_discard", "kong_replacement", "heavenly_win", "earthly_win", "human_win");
    private static final Set<String> RIICHI_FLAGS = Set.of("riichi", "double_riichi", "ippatsu");
    private static final Set<String> TAIWAN_FLAGS = Set.of("ready", "heavenly_ready", "earthly_ready", "flower_replacement");

    public YakuCondition {
        Objects.requireNonNull(type);
        children = List.copyOf(children); values = List.copyOf(values);
        if (children.size() > 16 || values.size() > 34 || new HashSet<>(values).size() != values.size() || min < 0 || max < min || max > 34)
            throw new IllegalArgumentException("Invalid condition bounds");
    }

    public void validate(MahjongVariant variant, int depth, int[] nodes) {
        if (depth > 3 || ++nodes[0] > 64) throw new IllegalArgumentException("Award condition is too complex");
        boolean logical = type == Type.ALL || type == Type.ANY || type == Type.NOT;
        if (logical) {
            if (children.isEmpty() || type == Type.NOT && children.size() != 1 || !values.isEmpty() || min != 0 || max != 0)
                throw new IllegalArgumentException("Invalid logical condition");
            children.forEach(c -> c.validate(variant, depth + 1, nodes));
            return;
        }
        if (!children.isEmpty()) throw new IllegalArgumentException("Leaf condition has children");
        switch (type) {
            case SHAPE -> allowed(values, variant == MahjongVariant.RIICHI ? Set.of("regular", "seven_pairs", "thirteen_orphans") : Set.of("regular"), true);
            case CLOSED -> allowed(values, Set.of("true", "false"), true);
            case FLAGS -> {
                var allowed = new HashSet<>(COMMON_FLAGS);
                allowed.addAll(variant == MahjongVariant.RIICHI ? RIICHI_FLAGS : TAIWAN_FLAGS);
                allowed(values, allowed, false);
            }
            case CATEGORIES -> allowed(values, Set.of("man", "pin", "sou", "wind", "dragon"), false);
            case TILES, PAIR -> { if (values.isEmpty()) throw new IllegalArgumentException("Missing tile kinds"); values.forEach(Tile::parseKind); }
            case GROUPS -> {
                if (values.size() != 3) throw new IllegalArgumentException("Group filters require type, concealment and kind");
                allowed(List.of(values.get(0)), Set.of("any", "sequence", "triplet", "quad"), true);
                allowed(List.of(values.get(1)), Set.of("all", "concealed", "exposed"), true);
                if (!values.get(2).equals("*")) Tile.parseKind(values.get(2));
            }
            case WAIT -> allowed(values, Set.of("pair", "two_sided", "closed", "edge", "triplet", "special"), false);
            case REDS, FLOWERS, SAME_SEQUENCES, THREE_SUIT_SEQUENCES -> {
                if (!values.isEmpty() || type == Type.REDS && variant != MahjongVariant.RIICHI
                    || type == Type.FLOWERS && variant != MahjongVariant.TAIWAN)
                    throw new IllegalArgumentException("Unavailable condition for variant");
            }
            default -> throw new IllegalArgumentException("Unsupported condition");
        }
        boolean count = switch (type) { case TILES, GROUPS, REDS, FLOWERS, SAME_SEQUENCES -> true; default -> false; };
        if (!count && (min != 0 || max != 0)) throw new IllegalArgumentException("Non-count condition has bounds");
    }

    private static void allowed(List<String> values, Set<String> allowed, boolean single) {
        if (values.isEmpty() || single && values.size() != 1 || !allowed.containsAll(values))
            throw new IllegalArgumentException("Invalid condition values");
    }

    private boolean range(long count) { return count >= min && count <= max; }

    public boolean matches(YakuFacts f) {
        return switch (type) {
            case ALL -> children.stream().allMatch(c -> c.matches(f));
            case ANY -> children.stream().anyMatch(c -> c.matches(f));
            case NOT -> !children.get(0).matches(f);
            case SHAPE -> values.contains(f.shape());
            case CLOSED -> f.closed() == values.get(0).equals("true");
            case FLAGS -> f.flags().containsAll(values);
            case CATEGORIES -> values.stream().allMatch(v -> f.kinds().stream().anyMatch(k -> switch (v) {
                case "man" -> k < 9; case "pin" -> k >= 9 && k < 18; case "sou" -> k >= 18 && k < 27;
                case "wind" -> k >= 27 && k < 31; case "dragon" -> k >= 31; default -> false;
            }));
            case TILES -> range(f.kinds().stream().filter(k -> values.stream().anyMatch(v -> Tile.parseKind(v) == k)).count());
            case PAIR -> f.pair() >= 0 && values.stream().anyMatch(v -> Tile.parseKind(v) == f.pair());
            case WAIT -> values.contains(f.waitShape());
            case GROUPS -> range(f.groups().stream().filter(g -> (values.get(0).equals("any") || g.type().equals(values.get(0)))
                && (values.get(1).equals("all") || g.concealed() == values.get(1).equals("concealed"))
                && (values.get(2).equals("*") || g.kind() == Tile.parseKind(values.get(2)))).count());
            case REDS -> range(f.redCount());
            case FLOWERS -> range(f.flowerCount());
            case SAME_SEQUENCES -> range(f.groups().stream().filter(g -> g.type().equals("sequence"))
                .mapToLong(g -> f.groups().stream().filter(other -> other.type().equals("sequence") && other.kind() == g.kind()).count()).max().orElse(0));
            case THREE_SUIT_SEQUENCES -> java.util.stream.IntStream.range(0, 7).anyMatch(rank ->
                java.util.stream.IntStream.range(0, 3).allMatch(suit -> f.groups().stream().anyMatch(g -> g.type().equals("sequence") && g.kind() == rank + suit * 9)));
        };
    }
}
