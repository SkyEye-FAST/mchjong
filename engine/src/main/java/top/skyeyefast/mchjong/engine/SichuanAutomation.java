package top.skyeyefast.mchjong.engine;

final class SichuanAutomation {
    private SichuanAutomation() {}

    static int action(MatchAutomation preference, SichuanView view) {
        var legal = view.actions();
        for (int index = 0; index < legal.size(); index++)
            if (legal.get(index).type() == SichuanAction.Type.WIN) return preference.win() ? index : -1;
        if (view.phase() == SichuanGame.Phase.REACTION && preference.noCalls()) {
            for (int index = 0; index < legal.size(); index++)
                if (legal.get(index).type() == SichuanAction.Type.PASS) return index;
        }
        if (view.phase() == SichuanGame.Phase.TURN && preference.discard() && view.viewerSeat() >= 0) {
            int drawn = view.seats().get(view.viewerSeat()).drawn();
            for (int index = 0; index < legal.size(); index++)
                if (drawn >= 0 && legal.get(index).type() == SichuanAction.Type.DISCARD
                    && legal.get(index).tiles().get(0) == drawn) return index;
        }
        return -1;
    }
}
