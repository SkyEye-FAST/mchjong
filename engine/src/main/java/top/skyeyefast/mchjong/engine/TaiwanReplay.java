package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

public record TaiwanReplay(TaiwanGameState.Rules rules, List<TaiwanReplayHand> hands) {
    public TaiwanReplay {
        Objects.requireNonNull(rules); hands = List.copyOf(hands);
        if (hands.size() > 1024) throw new IllegalArgumentException("Too many Taiwan hands");
        int dealer = 0, continuation = 0, rotations = 0;
        List<Long> scores = List.of(0L,0L,0L,0L);
        for (int i = 0; i < hands.size(); i++) {
            var hand = hands.get(i); var start = hand.recording();
            if (rotations >= 16 || start.number() != i+1 || !start.rules().equals(rules)
                || start.opening().dealer() != dealer || start.roundWind() != Tile.EAST+rotations/4
                || start.continuation() != continuation || !start.initialScores().equals(scores))
                throw new IllegalArgumentException("Invalid Taiwan replay match chain");
            var end = hand.settlement();
            boolean retain = end.winner() == null || end.winner() == dealer;
            if (end.nextDealer() != (retain ? dealer : (dealer+1)%4) || end.nextContinuation() != (retain ? continuation+1 : 0))
                throw new IllegalArgumentException("Invalid Taiwan replay dealer succession");
            if (!retain) rotations++;
            dealer = end.nextDealer(); continuation = end.nextContinuation(); scores = hand.finalScores();
        }
    }
    public boolean complete() {
        return hands.stream().filter(h -> h.settlement().nextDealer() != h.recording().opening().dealer()).count() == 16;
    }
}
