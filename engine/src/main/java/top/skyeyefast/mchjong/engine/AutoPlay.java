package top.skyeyefast.mchjong.engine;

import java.util.List;
import static top.skyeyefast.mchjong.engine.Action.Type.*;

/** Per-seat preferences. Decisions use the referee's legal actions and current token. */
public record AutoPlay(boolean sort, boolean win, boolean noCalls, boolean discard) {
    public static final AutoPlay DEFAULT = new AutoPlay(true, false, false, false);
    public enum Option { SORT, WIN, NO_CALLS, DISCARD }

    public boolean enabled(Option option) {
        return switch (option) {
            case SORT -> sort;
            case WIN -> win;
            case NO_CALLS -> noCalls;
            case DISCARD -> discard;
        };
    }

    public AutoPlay with(Option option, boolean enabled) {
        return new AutoPlay(option == Option.SORT ? enabled : sort, option == Option.WIN ? enabled : win,
            option == Option.NO_CALLS ? enabled : noCalls, option == Option.DISCARD ? enabled : discard);
    }

    /** Never discard or pass a legal win while waiting for an explicit win decision. */
    public int action(Game.Phase phase, boolean riichi, int drawn, List<Action> legal) {
        for (int i = 0; i < legal.size(); i++)
            if (legal.get(i).type() == TSUMO || legal.get(i).type() == RON) return win ? i : -1;
        if (phase == Game.Phase.REACTION && (noCalls || riichi)) return Game.indexOf(legal, PASS);
        if (phase != Game.Phase.TURN || drawn < 0 || !(riichi || discard)) return -1;
        // A legal concealed kan or north extraction after riichi remains an explicit choice.
        if (riichi && legal.stream().anyMatch(action -> action.type() == NUKI
            || !noCalls && action.type() == CLOSED_KAN)) return -1;
        for (int i = 0; i < legal.size(); i++)
            if (legal.get(i).type() == DISCARD && legal.get(i).tiles().getFirst() == drawn) return i;
        return -1;
    }
}
