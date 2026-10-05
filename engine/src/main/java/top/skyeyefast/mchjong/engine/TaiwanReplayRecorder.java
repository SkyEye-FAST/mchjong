package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import static top.skyeyefast.mchjong.engine.TaiwanReplayHand.Kind.*;

/** Observes accepted engine choices and physical checkpoints; owns no game transitions. */
public final class TaiwanReplayRecorder {
    public record State(int number, TaiwanGameState.Rules rules, TaiwanGameState.Opening opening,
                        int roundWind, int continuation, List<Integer> wall,
                        List<TaiwanGameState.Player> initialPlayers, List<Long> initialScores,
                        List<TaiwanReplayHand.Decision> decisions, List<TaiwanReplayHand.Event> events) {
        public State {
            Objects.requireNonNull(rules); Objects.requireNonNull(opening);
            wall = List.copyOf(wall); initialPlayers = List.copyOf(initialPlayers); initialScores = List.copyOf(initialScores);
            decisions = List.copyOf(decisions); events = List.copyOf(events);
            var expected = rules.flowers() == TaiwanRules.Flowers.NONE ? Tile.set(false,RedFives.NONE) : Tile.standard144Set();
            if (number < 1 || number > 1024 || roundWind < Tile.EAST || roundWind > Tile.NORTH || continuation < 0 || continuation > 1_000_000
                || wall.size() != expected.size() || !new HashSet<>(wall).equals(new HashSet<>(expected))
                || initialPlayers.size() != 4 || initialScores.size() != 4 || decisions.size() > 4096 || events.size() > 16384)
                throw new IllegalArgumentException("Invalid Taiwan replay opening");
            long total = 0; for (long score : initialScores) total = Math.addExact(total,score);
            if (total != 0) throw new IllegalArgumentException("Unbalanced Taiwan replay scores");
            int cursor = 0;
            for (var event : events) {
                if (event.actionCursor() < cursor || event.actionCursor() > decisions.size())
                    throw new IllegalArgumentException("Invalid Taiwan event order");
                cursor = event.actionCursor();
            }
        }
    }
    record Effect(TaiwanReplayHand.Event event, TaiwanGameState state) {}
    private final State initial;
    private final List<TaiwanReplayHand.Decision> decisions = new ArrayList<>();
    private final List<TaiwanReplayHand.Event> events = new ArrayList<>();

    public TaiwanReplayRecorder(int number, List<Long> scores, TaiwanGame game) {
        var steps = game.replaySteps();
        if (steps.isEmpty() || steps.get(0).wall().front() != 65 || steps.get(0).wall().tail() != 0)
            throw new IllegalArgumentException("Recorder requires a real opening");
        initial = new State(number,TaiwanGameState.Rules.of(game.getRules()),
            new TaiwanGameState.Opening(game.getOpening().getDealer(),game.getOpening().getDice()),game.getRoundWind(),game.getContinuation(),
            game.replayOpeningWall(),steps.get(0).players(),scores,List.of(),List.of());
        for (var effect : openingEffects(steps)) events.add(effect.event());
    }
    public TaiwanReplayRecorder(State state) { initial = state; decisions.addAll(state.decisions()); events.addAll(state.events()); }
    public State save() { return new State(initial.number(),initial.rules(),initial.opening(),initial.roundWind(),initial.continuation(),
        initial.wall(),initial.initialPlayers(),initial.initialScores(),decisions,events); }
    public void accepted(TaiwanGameState before, int seat, List<TaiwanAction> options, int selected, TaiwanGame game) {
        var choice = new TaiwanReplayHand.Decision(seat,options.stream().map(TaiwanGameState.Action::of).toList(),selected);
        decisions.add(choice);
        for (var effect : effects(before,choice,game.replaySteps(),decisions.size())) events.add(effect.event());
    }
    static List<Long> scores(List<Long> initial, TaiwanGameState.Result settlement) {
        if (settlement == null) return initial;
        return java.util.stream.IntStream.range(0,4).mapToObj(s -> Math.addExact(initial.get(s),settlement.deltas().get(s))).toList();
    }
    static TaiwanReplayHand.Event event(TaiwanReplayHand.Kind kind, int cursor, int seat, int tile, int slot, TaiwanGameState state) {
        return new TaiwanReplayHand.Event(kind,cursor,seat,tile,slot,state.wall().front(),state.wall().tail(),state.wall().kongs());
    }
    static List<Effect> openingEffects(List<TaiwanGameState> steps) {
        var effects = new ArrayList<Effect>(); var first = steps.get(0);
        effects.add(new Effect(event(INITIAL,0,first.opening().dealer(),Tile.ABSENT,-1,first),first));
        differences(first,steps.subList(1,steps.size()),0,effects);
        return List.copyOf(effects);
    }
    static List<Effect> effects(TaiwanGameState before, TaiwanReplayHand.Decision choice, List<TaiwanGameState> steps, int cursor) {
        var effects = new ArrayList<Effect>();
        var action = choice.options().get(choice.selected());
        effects.add(new Effect(event(action.type() == TaiwanAction.Type.PASS ? PASS : RESPONSE,cursor,choice.seat(),
            action.tiles().isEmpty() ? Tile.ABSENT : action.tiles().get(0),-1,before),before));
        differences(before,steps,cursor,effects);
        return List.copyOf(effects);
    }
    private static void differences(TaiwanGameState before, List<TaiwanGameState> steps, int cursor, List<Effect> effects) {
        for (var after : steps) {
            for (int slot = 0; slot < before.wall().slots().size(); slot++) {
                int tile = before.wall().slots().get(slot);
                if (tile >= 0 && after.wall().slots().get(slot) == Tile.ABSENT) {
                    int owner = after.turn();
                    for (int s = 0; s < 4; s++) if (after.players().get(s).hand().contains(tile)
                        || after.players().get(s).flowers().stream().anyMatch(f -> f.id() == tile)) owner = s;
                    effects.add(new Effect(event(after.wall().tail() > before.wall().tail() ? REPLACEMENT : DRAW,cursor,owner,tile,slot,after),after));
                }
            }
            for (int s = 0; s < 4; s++) {
                var old = before.players().get(s); var player = after.players().get(s);
                for (var flower : player.flowers()) if (!old.flowers().contains(flower))
                    effects.add(new Effect(event(FLOWER,cursor,s,flower.id(),-1,after),after));
                for (int tile : player.river()) if (!old.river().contains(tile))
                    effects.add(new Effect(event(DISCARD,cursor,s,tile,-1,after),after));
                if (old.ready() != player.ready()) effects.add(new Effect(event(READY,cursor,s,
                    player.river().get(player.river().size()-1),-1,after),after));
                for (var meld : player.melds()) if (!old.melds().contains(meld)) {
                    var kind = switch (meld.type()) {
                        case SEQUENCE -> CHOW; case TRIPLET -> PONG; case OPEN_QUAD -> OPEN_KONG;
                        case CONCEALED_QUAD -> CONCEALED_KONG; case ADDED_QUAD -> ADDED_KONG;
                    };
                    effects.add(new Effect(event(kind,cursor,s,meld.calledTile() < 0 ? meld.tiles().get(0) : meld.calledTile(),-1,after),after));
                }
            }
            if (after.offer() != null && after.offer().addedMeld() != null && !after.offer().equals(before.offer()))
                effects.add(new Effect(event(KONG_OFFER,cursor,after.offer().seat(),after.offer().tile(),-1,after),after));
            if (before.settlement() == null && after.settlement() != null) {
                var end = after.outcome();
                var kind = end.flowerEvent() != null ? FLOWER_WIN : end.method() == TaiwanWinContext.Method.ROBBING_KONG ? ROBBING_KONG : WIN;
                if (end.winner() != null) effects.add(new Effect(event(kind,
                    cursor,end.winner(),end.tile(),-1,after),after));
                effects.add(new Effect(event(SETTLEMENT,cursor,end.winner() == null ? after.turn() : end.winner(),end.tile(),-1,after),after));
            }
            before = after;
        }
    }
    public TaiwanReplayHand finish(TaiwanGame game) {
        if (game.getPhase() != TaiwanGame.Phase.FINISHED) throw new IllegalStateException("Taiwan hand is not complete");
        var state = game.save();
        return new TaiwanReplayHand(save(),state,state.settlement(),scores(initial.initialScores(),state.settlement()));
    }
}
