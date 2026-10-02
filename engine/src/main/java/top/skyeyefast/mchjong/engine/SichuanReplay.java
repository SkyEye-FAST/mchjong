package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

public record SichuanReplay(SichuanRules rules, List<SichuanReplayHand> hands) {
    public SichuanReplay {
        Objects.requireNonNull(rules);
        hands = List.copyOf(hands);
        if (hands.size() > rules.matchHands()) throw new IllegalArgumentException("Too many Sichuan replay hands");
        int dealer = 0;
        List<Integer> points = List.of(0, 0, 0, 0);
        for (int index = 0; index < hands.size(); index++) {
            var hand = hands.get(index);
            if (hand.number() != index + 1 || hand.opening().dealer() != dealer || !hand.initialPoints().equals(points))
                throw new IllegalArgumentException("Invalid Sichuan replay hand chain");
            hand.result().validate(rules);
            dealer = hand.result().nextDealer(dealer);
            points = hand.finalPoints();
        }
    }
}
