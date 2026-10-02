package top.skyeyefast.mchjong.engine;

import java.util.List;

/** Detached state for an unfinished Riichi hand recording. */
record ReplayRecorderState(int number, int round, int dealer, int honba, int sticks,
                           List<Integer> initialPoints, List<List<Integer>> initialHands,
                           List<Integer> initialDora, List<ReplayHand.Event> events,
                           List<ReplayHand.Decision> decisions, List<ReplayHand.Win> wins,
                           int pendingDeclaration, int indicators) {
    ReplayRecorderState {
        initialPoints = List.copyOf(initialPoints);
        initialHands = initialHands.stream().map(List::copyOf).toList();
        initialDora = List.copyOf(initialDora);
        events = List.copyOf(events);
        decisions = List.copyOf(decisions);
        wins = List.copyOf(wins);
        if (number < 1 || round < 0 || dealer < 0 || dealer > 3 || honba < 0 || sticks < 0
            || pendingDeclaration < -1 || pendingDeclaration >= events.size() || indicators < 1 || indicators > 5)
            throw new IllegalArgumentException("Invalid Riichi recorder state");
    }
}
