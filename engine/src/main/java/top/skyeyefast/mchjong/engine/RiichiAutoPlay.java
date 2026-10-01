package top.skyeyefast.mchjong.engine;

import java.util.List;
import static top.skyeyefast.mchjong.engine.RiichiAction.Type.*;

/** Per-seat preferences. Decisions use the referee's legal actions and current token. */
public record RiichiAutoPlay(boolean sort, boolean kita) {
    public static final RiichiAutoPlay DEFAULT = new RiichiAutoPlay(true, false);
    public enum Option { SORT, KITA }

    public boolean enabled(Option option) {
        return switch (option) {
            case SORT -> sort;
            case KITA -> kita;
        };
    }

    public RiichiAutoPlay with(Option option, boolean enabled) {
        return new RiichiAutoPlay(option == Option.SORT ? enabled : sort, option == Option.KITA ? enabled : kita);
    }

    /** Never discard or pass a legal win while waiting for an explicit win decision. */
    public int action(MatchAutomation automation, RiichiGame.Phase phase, boolean riichi, int drawn, List<RiichiAction> legal) {
        for (int i = 0; i < legal.size(); i++)
            if (legal.get(i).type() == TSUMO || legal.get(i).type() == RON) return automation.win() ? i : -1;
        if (phase == RiichiGame.Phase.REACTION && (automation.noCalls() || riichi)) return RiichiGame.indexOf(legal, PASS);
        if (phase == RiichiGame.Phase.TURN && kita) {
            int north = RiichiGame.indexOf(legal, NUKI);
            if (north >= 0) return north;
        }
        if (phase != RiichiGame.Phase.TURN || drawn < 0 || !(riichi || automation.discard())) return -1;
        // A legal concealed kan or north extraction after riichi remains an explicit choice.
        if (riichi && legal.stream().anyMatch(action -> action.type() == NUKI
            || !automation.noCalls() && action.type() == CLOSED_KAN)) return -1;
        for (int i = 0; i < legal.size(); i++)
            if (legal.get(i).type() == DISCARD && legal.get(i).tiles().get(0) == drawn) return i;
        return -1;
    }
}
