package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

/** Strict rule-executed playback. Recorded events are checked, never applied as rules. */
public final class TaiwanReplayPlayback {
    public record Frame(TaiwanGameState state, List<Long> scores, TaiwanReplayHand.Event event) {
        public Frame { scores = List.copyOf(scores); }
        public int drawable() {
            return state.wall().slots().size()-state.wall().front()-state.wall().tail()-16
                -(state.rules().reserve() == TaiwanRules.Reserve.SIXTEEN_PLUS_KONGS ? state.wall().kongs() : 0);
        }
    }
    public record Timeline(List<Frame> frames) { public Timeline { frames = List.copyOf(frames); } }
    private TaiwanReplayPlayback() {}
    public static TaiwanGame reconstruct(TaiwanReplayRecorder.State recording) { return execute(recording,null); }
    public static Timeline timeline(ReplayMatch match, int handIndex) {
        if (match.variant() != MahjongVariant.TAIWAN) throw new IllegalArgumentException("Not a Taiwan replay");
        var hand = match.taiwan().hands().get(handIndex);
        var frames = new ArrayList<Frame>();
        var game = execute(hand.recording(),frames);
        if (!samePosition(game.save(),hand.finalState()) || !hand.settlement().equals(game.save().settlement())
            || !hand.finalScores().equals(TaiwanReplayRecorder.scores(hand.recording().initialScores(),game.save().settlement())))
            throw new IllegalArgumentException("Taiwan replay settlement disagrees with game");
        return new Timeline(frames);
    }
    private static TaiwanGame execute(TaiwanReplayRecorder.State recording, List<Frame> frames) {
        var game = new TaiwanGame(recording.rules().restore(),recording.opening().restore(),recording.wall(),recording.roundWind(),recording.continuation());
        if (!game.replaySteps().get(0).players().equals(recording.initialPlayers()))
            throw new IllegalArgumentException("Taiwan replay deal disagrees with physical wall");
        int cursor = check(TaiwanReplayRecorder.openingEffects(game.replaySteps()),recording,0,frames);
        for (int i = 0; i < recording.decisions().size(); i++) {
            var choice = recording.decisions().get(i);
            var issued = game.decisions().stream().filter(d -> d.getSeat() == choice.seat()).findFirst().orElseThrow(
                () -> new IllegalArgumentException("Taiwan replay seat has no decision"));
            if (!issued.getActions().stream().map(TaiwanGameState.Action::of).toList().equals(choice.options()))
                throw new IllegalArgumentException("Taiwan replay options disagree with engine");
            var before = game.save();
            game.submit(choice.seat(),issued.getToken(),choice.selected());
            game.checkConservation();
            cursor = check(TaiwanReplayRecorder.effects(before,choice,game.replaySteps(),i+1),recording,cursor,frames);
        }
        if (cursor != recording.events().size()) throw new IllegalArgumentException("Unmatched Taiwan replay events");
        return game;
    }
    private static int check(List<TaiwanReplayRecorder.Effect> effects, TaiwanReplayRecorder.State recording, int cursor, List<Frame> frames) {
        for (var effect : effects) {
            if (cursor >= recording.events().size() || !effect.event().equals(recording.events().get(cursor++)))
                throw new IllegalArgumentException("Taiwan replay event disagrees with engine");
            if (frames != null) frames.add(new Frame(effect.state(),TaiwanReplayRecorder.scores(recording.initialScores(),effect.state().settlement()),effect.event()));
        }
        return cursor;
    }
    /** Restoration refreshes request authority only; every gameplay field must agree. */
    public static boolean samePosition(TaiwanGameState actual, TaiwanGameState expected) {
        return new TaiwanGameState(actual.format(),actual.rules(),actual.opening(),actual.roundWind(),actual.continuation(),expected.revision(),expected.decision(),
            actual.phase(),actual.turn(),actual.wall(),actual.players(),actual.drawn(),actual.turnDrawn(),actual.origin(),actual.turnWaits(),actual.calls(),actual.draws(),
            actual.offer(),actual.replies(),actual.outcome(),actual.settlement()).equals(expected);
    }
}
