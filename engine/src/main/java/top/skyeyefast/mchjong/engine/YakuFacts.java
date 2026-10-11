package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Set;

/** One candidate's owned ordinary kinds, groups and host-certified circumstances; no opponent or wall identities. */
public record YakuFacts(String shape, boolean closed, List<Integer> kinds, List<Group> groups,
                        int pair, String waitShape, Set<String> flags, int redCount, int flowerCount) {
    public YakuFacts {
        kinds = List.copyOf(kinds); groups = List.copyOf(groups); flags = Set.copyOf(flags);
        if (!Set.of("regular", "seven_pairs", "thirteen_orphans").contains(shape)
            || !Set.of("pair", "two_sided", "closed", "edge", "triplet", "special").contains(waitShape)
            || pair < -1 || pair > 33 || kinds.size() > 22 || groups.size() > 5 || kinds.stream().anyMatch(k -> k < 0 || k > 33)
            || redCount < 0 || redCount > 4 || flowerCount < 0 || flowerCount > 8)
            throw new IllegalArgumentException("Invalid custom award facts");
    }
    public record Group(String type, int kind, boolean concealed) {
        public Group {
            if (!Set.of("sequence", "triplet", "quad").contains(type) || kind < 0 || kind > 33
                || type.equals("sequence") && (kind >= 27 || kind % 9 > 6))
                throw new IllegalArgumentException("Invalid custom award group");
        }
    }
}
