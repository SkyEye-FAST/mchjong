package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.UUID;

/** This is the ONLY game object allowed in an S2C payload. No private game state is referenced. */
public record TableView(UUID tableId, long revision, long decision, int handNumber, RuleSet rules, Game.Phase phase,
                        int viewerSeat, int dealer, int round, int honba, int riichiSticks,
                        int turn, int remaining, int wallBreak, List<Integer> wall, Focus focus,
                        List<Seat> seats, List<Action> actions, List<Win> wins,
                        String result, List<Integer> deltas, List<Double> finalScores) {
    public record Seat(String name, boolean occupied, boolean bot, boolean ready, int points,
                       List<Integer> hand, int drawn, List<Meld> melds, List<Discard> river,
                       List<Integer> norths, boolean riichi, boolean exposed) {
        public Seat {
            hand = List.copyOf(hand); melds = List.copyOf(melds); river = List.copyOf(river);
            norths = List.copyOf(norths);
        }
    }
    public record Win(int seat, int from, int tile, HandScore score) {}
    /** A declaration or discard is public, even while its reaction window is open. */
    public record Focus(int seat, int tile, boolean declaration, int index) {}
    public TableView {
        wall = List.copyOf(wall); seats = List.copyOf(seats); actions = List.copyOf(actions);
        wins = List.copyOf(wins); deltas = List.copyOf(deltas); finalScores = List.copyOf(finalScores);
    }
}
