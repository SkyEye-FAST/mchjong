package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

/** One sealed native Taiwan hand, including automatic opening and replacement events. */
public record TaiwanReplayHand(TaiwanReplayRecorder.State recording, TaiwanGameState finalState,
                               TaiwanGameState.Result settlement, List<Long> finalScores) {
    public enum Kind { INITIAL, RESPONSE, PASS, DRAW, REPLACEMENT, FLOWER, DISCARD, READY,
        CHOW, PONG, OPEN_KONG, CONCEALED_KONG, ADDED_KONG, KONG_OFFER, WIN, ROBBING_KONG, FLOWER_WIN, SETTLEMENT }
    public record Decision(int seat, List<TaiwanGameState.Action> options, int selected) {
        public Decision {
            options = List.copyOf(options);
            if (seat < 0 || seat > 3 || options.isEmpty() || options.size() > 128 || selected < 0 || selected >= options.size())
                throw new IllegalArgumentException("Invalid Taiwan replay decision");
        }
    }
    public record Event(Kind kind, int actionCursor, int seat, int tile, int slot, int front, int tail, int kongs) {
        public Event {
            Objects.requireNonNull(kind);
            if (actionCursor < 0 || actionCursor > 4096 || seat < 0 || seat > 3 || tile < Tile.ABSENT || tile >= 144
                || slot < -1 || slot >= 144 || front < 65 || front > 144 || tail < 0 || tail > 144 || kongs < 0 || kongs > 20)
                throw new IllegalArgumentException("Invalid Taiwan replay event");
        }
    }
    public TaiwanReplayHand {
        Objects.requireNonNull(recording); Objects.requireNonNull(finalState); Objects.requireNonNull(settlement);
        finalScores = List.copyOf(finalScores);
        if (finalState.phase() != TaiwanGame.Phase.FINISHED || !settlement.equals(finalState.settlement())
            || !finalScores.equals(TaiwanReplayRecorder.scores(recording.initialScores(), settlement))
            || recording.events().isEmpty() || recording.events().get(recording.events().size()-1).kind() != Kind.SETTLEMENT)
            throw new IllegalArgumentException("Invalid sealed Taiwan hand");
    }
    public int number() { return recording.number(); }
    public List<Decision> decisions() { return recording.decisions(); }
    public List<Event> events() { return recording.events(); }
}
