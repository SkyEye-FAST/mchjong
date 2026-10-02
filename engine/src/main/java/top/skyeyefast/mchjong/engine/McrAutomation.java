package top.skyeyefast.mchjong.engine;

final class McrAutomation {
    private McrAutomation() {}

    static int action(MatchAutomation preference, McrView view) {
        var legal = view.actions();
        if (view.qualifyingWin()) {
            for (int index = 0; index < legal.size(); index++)
                if (legal.get(index).type() == McrAction.Type.WIN) return preference.win() ? index : -1;
        }
        if (view.phase() == McrGame.Phase.REACTION && preference.noCalls()) {
            for (int index = 0; index < legal.size(); index++)
                if (legal.get(index).type() == McrAction.Type.PASS) return index;
        }
        if (view.phase() == McrGame.Phase.TURN && preference.discard() && view.viewerSeat() >= 0) {
            int drawn = view.seats().get(view.viewerSeat()).drawn();
            for (int index = 0; index < legal.size(); index++)
                if (drawn >= 0 && legal.get(index).type() == McrAction.Type.DISCARD
                    && legal.get(index).tiles().get(0) == drawn) return index;
        }
        return -1;
    }
}
