package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Read-only reconstruction from a sealed opening and accepted server choices. */
public final class McrReplayPlayback {
    public record Frame(McrView view, McrReplayHand.Event event) {}
    public record Timeline(List<Frame> frames) {
        public Timeline {
            frames = List.copyOf(frames);
            if (frames.size() < 2 || frames.get(frames.size() - 1).event().kind() != McrReplayHand.Kind.SETTLEMENT)
                throw new IllegalArgumentException("Invalid MCR replay timeline");
        }
    }

    private McrReplayPlayback() {}

    public static Timeline timeline(ReplayMatch match, int handIndex) {
        if (match.variant() != MahjongVariant.MCR) throw new IllegalArgumentException("Not an MCR replay");
        var hand = match.mcr().hands().get(handIndex);
        var game = McrGame.replayHand(hand.number(), hand.wall(), hand.opening(), hand.initialPoints());
        if (!game.initialHands().equals(hand.initialHands())) throw new IllegalArgumentException("MCR opening deal disagrees with wall");
        var frames = new ArrayList<Frame>();
        frames.add(new Frame(game.replayView(), null));
        int eventCursor = 0;
        for (int index = 0; index < hand.decisions().size(); index++) {
            var decision = hand.decisions().get(index);
            if (!game.actions(decision.seat()).equals(decision.options()))
                throw new IllegalArgumentException("MCR replay decision was not issued by the engine");
            var before = game.save();
            if (!game.act(decision.seat(), game.decision(), decision.selected()))
                throw new IllegalArgumentException("MCR replay action was rejected");
            var actual = McrReplayRecorder.effects(before, decision.options().get(decision.selected()), decision.seat(), game, index + 1);
            for (var event : actual) {
                if (eventCursor >= hand.events().size() || !Objects.equals(event, hand.events().get(eventCursor++)))
                    throw new IllegalArgumentException("MCR replay event disagrees with the game");
                frames.add(new Frame(game.replayView(), event));
            }
        }
        if (eventCursor != hand.events().size() || game.result() == null
            || !Objects.equals(game.result() instanceof McrSettlement.Win win ? win : null, hand.win())
            || (game.result() instanceof McrSettlement.Draw) != hand.draw()
            || !game.penalties().stream().filter(penalty -> penalty.handNumber() == hand.number()).toList().equals(hand.penalties())
            || !java.util.stream.IntStream.range(0, 4).map(game::points).boxed().toList().equals(hand.finalPoints()))
            throw new IllegalArgumentException("MCR replay settlement disagrees with the game");
        return new Timeline(frames);
    }
}
