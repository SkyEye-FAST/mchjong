package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import static top.skyeyefast.mchjong.engine.SichuanReplayHand.Kind.*;

public final class SichuanReplayRecorder {
    public record State(int number, List<Integer> initialPoints, SichuanWall.State opening,
                        List<List<Integer>> initialHands, List<SichuanReplayHand.Decision> decisions,
                        List<SichuanReplayHand.Event> events) {
        public State {
            initialPoints = List.copyOf(initialPoints);
            initialHands = initialHands.stream().map(List::copyOf).toList();
            decisions = List.copyOf(decisions); events = List.copyOf(events);
            if (number < 1 || initialPoints.size() != 4 || opening.cursor() != 0 || !Tile.validSichuanSet(opening.slots())
                || initialHands.size() != 4 || decisions.size() > 4096 || events.size() > 8192)
                throw new IllegalArgumentException("Invalid Sichuan recorder");
            SichuanWall.restore(opening);
        }
    }

    private final State initial;
    private final List<SichuanReplayHand.Decision> decisions = new ArrayList<>();
    private final List<SichuanReplayHand.Event> events = new ArrayList<>();

    public SichuanReplayRecorder(SichuanGame game) {
        this(new State(game.handNumber(), game.scores(), game.initialOpening(), game.initialHands(), List.of(), List.of()));
    }
    public SichuanReplayRecorder(State state) {
        initial = state; decisions.addAll(state.decisions()); events.addAll(state.events());
    }
    public State save() {
        return new State(initial.number(), initial.initialPoints(), initial.opening(), initial.initialHands(), decisions, events);
    }
    public void accepted(SichuanGame.State before, int seat, List<SichuanAction> options, int selected, SichuanGame after) {
        var choice = new SichuanReplayHand.Decision(seat, options, selected, false);
        decisions.add(choice); events.addAll(effects(before, choice, after.save(), decisions.size()));
    }
    public void adjudicated(SichuanGame.State before, int seat, SichuanGame after) {
        var choice = new SichuanReplayHand.Decision(seat, List.of(), -1, true);
        decisions.add(choice); events.addAll(effects(before, choice, after.save(), decisions.size()));
    }
    static List<SichuanReplayHand.Event> effects(SichuanGame.State before, SichuanReplayHand.Decision choice,
                                               SichuanGame.State after, int cursor) {
        var events = new ArrayList<SichuanReplayHand.Event>();
        int seat = choice.seat();
        if (!choice.adjudication()) {
            var action = choice.options().get(choice.selected());
            var kind = switch (action.type()) {
                case VOID_SUIT -> VOID_SUIT;
                case DRAW -> DRAW;
                case DISCARD -> DISCARD;
                case ADDED_KONG -> ADDED_KONG;
                case PASS -> PASS;
                case PUNG, DISCARD_KONG, CONCEALED_KONG, WIN -> RESPONSE;
            };
            int tile = action.tiles().isEmpty() ? Tile.ABSENT : action.tiles().get(0);
            if (kind == DRAW) for (int slot = 0; slot < 108; slot++)
                if (before.wall().slots().get(slot) >= 0 && after.wall().slots().get(slot) == Tile.ABSENT)
                    tile = before.wall().slots().get(slot);
            events.add(new SichuanReplayHand.Event(kind, cursor, seat, tile, -1));
        }
        if (before.phase() == SichuanGame.Phase.VOIDING && after.phase() != SichuanGame.Phase.VOIDING)
            events.add(new SichuanReplayHand.Event(VOID_SUITS, cursor, seat, Tile.ABSENT, -1));
        for (int owner = 0; owner < 4; owner++) {
            var earlier = before.players().get(owner).melds();
            for (var meld : after.players().get(owner).melds()) if (!earlier.contains(meld))
                events.add(new SichuanReplayHand.Event(meld.quad() ? KONG : PUNG, cursor, owner,
                    meld.calledTile() >= 0 ? meld.calledTile() : meld.tiles().get(0), -1));
        }
        for (int index = before.wins().size(); index < after.wins().size(); index++) {
            var win = after.wins().get(index);
            events.add(new SichuanReplayHand.Event(WIN, cursor, win.seat(), win.tile(), -1));
        }
        for (int index = before.ledger().size(); index < after.ledger().size(); index++)
            events.add(new SichuanReplayHand.Event(PAYMENT, cursor, after.ledger().get(index).payer(), Tile.ABSENT, index));
        if (before.result() == null && after.result() != null)
            events.add(new SichuanReplayHand.Event(SETTLEMENT, cursor, seat, Tile.ABSENT, -1));
        return List.copyOf(events);
    }
    public SichuanReplayHand finish(SichuanGame game) {
        if (!game.ended() || game.handNumber() != initial.number()) throw new IllegalStateException("Sichuan hand is not complete");
        return new SichuanReplayHand(initial.number(), initial.initialPoints(), initial.opening(), initial.initialHands(),
            game.save().players().stream().map(SichuanPlayerState::voidSuit).toList(), decisions, events, game.result(), game.scores());
    }
}
