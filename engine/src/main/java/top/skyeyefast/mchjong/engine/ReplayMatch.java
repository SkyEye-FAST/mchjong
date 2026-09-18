package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Contains completed hands only. No RNG seed, future wall, or active hand is exported. */
public record ReplayMatch(UUID id, UUID tableId, long startedAt, long updatedAt, RuleConfig rules,
                          int initialDealer, List<Participant> participants, List<ReplayHand> hands, boolean complete, RedFives redFives) {
    public record Participant(UUID id, String name, boolean bot) {
        public Participant {
            Objects.requireNonNull(id); Objects.requireNonNull(name);
            if (name.isBlank() || name.length() > 128) throw new IllegalArgumentException("Invalid replay player name");
        }
    }
    public record Header(UUID id, long updatedAt, RuleConfig rules, int hands, boolean complete, List<String> names) {
        public Header {
            Objects.requireNonNull(id); Objects.requireNonNull(rules); names = List.copyOf(names);
            if (hands < 1 || hands > 1024 || names.size() != rules.players()
                || names.stream().anyMatch(name -> name.isBlank() || name.length() > 128))
                throw new IllegalArgumentException("Invalid replay header");
        }
    }
    public record Index(int page, String search, boolean oldestFirst, List<Header> matches, boolean more) {
        public Index {
            Objects.requireNonNull(search);
            matches = List.copyOf(matches);
            if (page < 0 || page > 100_000 || search.length() > 80 || matches.size() > 12)
                throw new IllegalArgumentException("Invalid replay page");
        }
    }

    public ReplayMatch {
        Objects.requireNonNull(id); Objects.requireNonNull(tableId); Objects.requireNonNull(rules);
        if (!rules.allows(redFives)) throw new IllegalArgumentException("Invalid replay red-five composition");
        participants = List.copyOf(participants); hands = List.copyOf(hands);
        if (participants.size() != rules.players() || initialDealer < 0 || initialDealer >= participants.size()
            || hands.size() > 1024 || complete && hands.isEmpty()) throw new IllegalArgumentException("Invalid replay match");
        for (var hand : hands) if (hand.initialHands().size() != rules.players()) throw new IllegalArgumentException("Replay rules mismatch");
        if (complete && (hands.getLast().finalScores().size() != rules.players() || hands.getLast().finalRanks().size() != rules.players()))
            throw new IllegalArgumentException("Missing final standings");
    }

    public Header header() {
        return new Header(id, updatedAt, rules, hands.size(), complete, participants.stream().map(Participant::name).toList());
    }
    public boolean permits(UUID player) {
        return participants.stream().anyMatch(participant -> !participant.bot() && participant.id().equals(player));
    }
    ReplayMatch append(ReplayHand hand, boolean ended) {
        var completed = new ArrayList<>(hands);
        completed.add(hand);
        return new ReplayMatch(id, tableId, startedAt, System.currentTimeMillis(), rules, initialDealer, participants, completed, ended, redFives);
    }
}
