package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

/** Records accepted MCR choices and their physical effects until the hand settles. */
final class McrReplayRecorder {
    record State(int number, List<Integer> initialPoints, List<Integer> wall, McrOpening opening,
                 List<List<Integer>> initialHands, List<McrReplayHand.Event> events,
                 List<McrReplayHand.Decision> decisions) {
        State {
            initialPoints = List.copyOf(initialPoints);
            wall = List.copyOf(wall);
            initialHands = initialHands.stream().map(List::copyOf).toList();
            events = List.copyOf(events);
            decisions = List.copyOf(decisions);
        }
    }

    private final int number;
    private final List<Integer> initialPoints;
    private final List<Integer> wall;
    private final McrOpening opening;
    private final List<List<Integer>> initialHands;
    private final List<McrReplayHand.Event> events = new ArrayList<>();
    private final List<McrReplayHand.Decision> decisions = new ArrayList<>();

    McrReplayRecorder(McrGame game) {
        number = game.handNumber();
        initialPoints = java.util.stream.IntStream.range(0, 4).map(game::points).boxed().toList();
        wall = game.initialWall();
        opening = game.opening();
        initialHands = game.initialHands();
    }

    McrReplayRecorder(State state) {
        number = state.number(); initialPoints = state.initialPoints(); wall = state.wall();
        opening = state.opening(); initialHands = state.initialHands();
        events.addAll(state.events()); decisions.addAll(state.decisions());
    }

    State save() { return new State(number, initialPoints, wall, opening, initialHands, events, decisions); }

    void accepted(McrGameState before, int seat, List<McrAction> options, int selected, McrGame after) {
        decisions.add(new McrReplayHand.Decision(seat, options, selected));
        events.addAll(effects(before, options.get(selected), seat, after, decisions.size()));
    }

    static List<McrReplayHand.Event> effects(McrGameState before, McrAction action, int seat, McrGame after, int cursor) {
        var result = new ArrayList<McrReplayHand.Event>();
        int taken = Tile.ABSENT;
        var oldWall = before.wall().tiles();
        var newWall = after.save().wall().tiles();
        for (int slot = 0; slot < 144; slot++) if (oldWall.get(slot) != Tile.ABSENT && newWall.get(slot) == Tile.ABSENT) {
            if (taken != Tile.ABSENT) throw new IllegalStateException("MCR action took more than one wall tile");
            taken = oldWall.get(slot);
        }
        var kind = switch (action.type()) {
            case DRAW -> McrReplayHand.Kind.DRAW;
            case REPLACE_FLOWER -> McrReplayHand.Kind.FLOWER_REPLACEMENT;
            case DISCARD -> McrReplayHand.Kind.DISCARD;
            case CHOW, PUNG, MELDED_KONG -> McrReplayHand.Kind.RESPONSE;
            case CONCEALED_KONG -> McrReplayHand.Kind.KONG;
            case WIN -> McrReplayHand.Kind.WIN;
            case PASS -> McrReplayHand.Kind.PASS;
        };
        int tile = action.type() == McrAction.Type.DRAW || action.type() == McrAction.Type.REPLACE_FLOWER
            ? taken : action.tiles().isEmpty() ? Tile.ABSENT : action.tiles().get(0);
        result.add(new McrReplayHand.Event(kind, cursor, seat, tile));
        for (int owner = 0; owner < 4; owner++) {
            var earlier = before.players().get(owner).melds();
            var current = after.melds(owner);
            if (earlier.equals(current)) continue;
            var meld = current.stream().filter(group -> !earlier.contains(group)).findFirst().orElseThrow();
            if (action.type() == McrAction.Type.CONCEALED_KONG) continue;
            var committed = switch (meld.type()) {
                case SEQUENCE -> McrReplayHand.Kind.CHOW;
                case TRIPLET -> McrReplayHand.Kind.PUNG;
                case OPEN_QUAD, CONCEALED_QUAD, ADDED_QUAD -> McrReplayHand.Kind.KONG;
            };
            result.add(new McrReplayHand.Event(committed, cursor, owner,
                meld.calledTile() >= 0 ? meld.calledTile() : meld.tiles().get(0)));
        }
        if (taken >= 0 && action.type() != McrAction.Type.DRAW && action.type() != McrAction.Type.REPLACE_FLOWER)
            result.add(new McrReplayHand.Event(McrReplayHand.Kind.DRAW, cursor, after.turn(), taken));
        for (int index = before.penalties().size(); index < after.penalties().size(); index++) {
            var penalty = after.penalties().get(index);
            result.add(new McrReplayHand.Event(McrReplayHand.Kind.WRONG_WIN, cursor, penalty.offender(), penalty.tile()));
        }
        if (before.result() == null && after.result() != null)
            result.add(new McrReplayHand.Event(McrReplayHand.Kind.SETTLEMENT, cursor, seat, Tile.ABSENT));
        return List.copyOf(result);
    }

    McrReplayHand finish(McrGame game) {
        if (game.result() == null || game.handNumber() != number) throw new IllegalStateException("MCR hand is not complete");
        var points = java.util.stream.IntStream.range(0, 4).map(game::points).boxed().toList();
        return new McrReplayHand(number, initialPoints, wall, opening, initialHands, events, decisions,
            game.result() instanceof McrSettlement.Win win ? win : null,
            game.result() instanceof McrSettlement.Draw,
            game.penalties().stream().filter(penalty -> penalty.handNumber() == number).toList(), points);
    }
}
