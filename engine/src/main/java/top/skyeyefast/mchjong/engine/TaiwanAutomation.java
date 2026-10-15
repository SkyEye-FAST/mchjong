package top.skyeyefast.mchjong.engine;

final class TaiwanAutomation {
    private TaiwanAutomation() {}

    static int action(MatchAutomation preference, TaiwanView view) {
        var legal = view.actions();
        for (int index = 0; index < legal.size(); index++)
            if (legal.get(index).type() == TaiwanAction.Type.WIN) return preference.win() ? index : -1;
        if (view.phase() == TaiwanGame.Phase.REACTION && preference.noCalls()) {
            for (int index = 0; index < legal.size(); index++)
                if (legal.get(index).type() == TaiwanAction.Type.PASS) return index;
        }
        if (view.phase() == TaiwanGame.Phase.TURN && preference.discard() && view.recipient() >= 0) {
            int drawn = view.seats().get(view.recipient()).drawn();
            for (int index = 0; index < legal.size(); index++)
                if (drawn >= 0 && legal.get(index).type() == TaiwanAction.Type.DISCARD
                    && legal.get(index).tiles().get(0) == drawn) return index;
        }
        return -1;
    }
}
