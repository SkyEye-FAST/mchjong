package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

public final class SichuanReplayPlayback {
    public record Frame(SichuanGame.State state, List<Integer> scores, SichuanReplayHand.Event event) {
        public Frame { scores = List.copyOf(scores); }
        public List<SichuanView.Seat> seats() {
            return state.players().stream().map(player -> new SichuanView.Seat(player.hand(), player.melds(), player.river(),
                player.voidSuit(), player.won(), player.drawn())).toList();
        }
    }
    public record Timeline(List<Frame> frames) {
        public Timeline { frames = List.copyOf(frames); }
    }
    private SichuanReplayPlayback() {}

    static SichuanGame reconstruct(SichuanRules rules, List<SichuanGame.Hand> completed, SichuanReplayRecorder.State recording) {
        var game = SichuanGame.replayHand(rules, completed, recording.opening());
        if (game.handNumber() != recording.number() || !game.scores().equals(recording.initialPoints())
            || !game.initialHands().equals(recording.initialHands())) throw new IllegalArgumentException("Invalid Sichuan replay opening");
        execute(game, recording.decisions(), recording.events(), null);
        return game;
    }
    public static Timeline timeline(ReplayMatch match, int handIndex) {
        if (match.variant() != MahjongVariant.SICHUAN) throw new IllegalArgumentException("Not a Sichuan replay");
        var replay = match.sichuan();
        var hand = replay.hands().get(handIndex);
        var previous = replay.hands().subList(0, handIndex).stream()
            .map(item -> new SichuanGame.Hand(item.number(), item.opening().dealer(), item.result())).toList();
        var game = SichuanGame.replayHand(replay.rules(), previous, hand.opening());
        if (!game.initialHands().equals(hand.initialHands()) || !game.scores().equals(hand.initialPoints()))
            throw new IllegalArgumentException("Sichuan replay opening disagrees with wall");
        var frames = new ArrayList<Frame>();
        frames.add(new Frame(game.save(), game.scores(), null));
        execute(game, hand.decisions(), hand.events(), frames);
        if (!game.ended() || !game.result().equals(hand.result()) || !game.scores().equals(hand.finalPoints())
            || !game.save().players().stream().map(SichuanPlayerState::voidSuit).toList().equals(hand.voidSuits()))
            throw new IllegalArgumentException("Sichuan replay settlement disagrees with game");
        return new Timeline(frames);
    }
    private static void execute(SichuanGame game, List<SichuanReplayHand.Decision> decisions,
                                List<SichuanReplayHand.Event> events, List<Frame> frames) {
        int eventCursor = 0;
        for (int index = 0; index < decisions.size(); index++) {
            var choice = decisions.get(index);
            var before = game.save();
            if (choice.adjudication()) {
                if (!game.adjudicateActiveFlowerPig(choice.seat())) throw new IllegalArgumentException("Invalid Sichuan replay adjudication");
            } else if (!game.actions(choice.seat()).equals(choice.options())
                || !game.act(choice.seat(), game.decision(), choice.selected()))
                throw new IllegalArgumentException("Sichuan replay choice was not issued");
            var after = game.save();
            for (var event : SichuanReplayRecorder.effects(before, choice, after, index + 1)) {
                if (eventCursor >= events.size() || !event.equals(events.get(eventCursor++)))
                    throw new IllegalArgumentException("Sichuan replay event disagrees with game");
                if (frames != null) frames.add(new Frame(after, game.scores(), event));
            }
        }
        if (eventCursor != events.size()) throw new IllegalArgumentException("Unmatched Sichuan replay events");
    }
}
